// SPDX-License-Identifier: MPL-2.0
package org.winlogon.skuld

import com.destroystokyo.paper.profile.ProfileProperty
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode

import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import io.papermc.paper.threadedregions.scheduler.ScheduledTask

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import org.json.JSONObject
import org.winlogon.skuld.data.DataHandler
import org.winlogon.skuld.data.MySQLDatabase
import org.winlogon.skuld.data.PostgreSQLDatabase
import org.winlogon.skuld.data.SQLiteDatabase
import org.winlogon.xpconomy.ExperienceError
import org.winlogon.xpconomy.ExperienceUnit
import org.winlogon.xpconomy.OfflineExperienceCache
import org.winlogon.xpconomy.XPConomy

class Skuld : JavaPlugin() {
    private val cache: OfflineExperienceCache? = null
    private val economy = XPConomy(cache)

    private val logger: java.util.logging.Logger = getLogger()

    private lateinit var usernameCache: Cache<String, UUID>
    private lateinit var textureCache: Cache<UUID, TextureData>

    private var nameKeeper: PlayerHistoryKeeper? = null
    private var executor = Executors.newVirtualThreadPerTaskExecutor()
    private var dataHandler: DataHandler? = null

    companion object {
        lateinit var instance: Skuld
        val isFolia = checkFolia()

        private fun checkFolia(): Boolean {
            return try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
    }

    override fun onEnable() {
        saveDefaultConfig()
        instance = this
        reloadConfig()

        logger.info("Registering commands...")

        val expirationDays = config.getLong("cache.expiration-days", 3).coerceAtLeast(1L)
        usernameCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(expirationDays))
            .build()

        val usernameExpirationDays = (expirationDays - 2).coerceAtLeast(1L)
        textureCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(usernameExpirationDays))
            .build()

        if (config.getBoolean("history.enabled", true)) {
            val dbType = config.getString("database.type")?.lowercase() ?: "sqlite"
            val maxConnections = config.getInt("database.max-connections", 10)

            dataHandler = when (dbType.lowercase()) {
                "postgresql" -> {
                    val dbName = config.getString("database.postgresql.name") ?: "skuld_names"
                    val dbUser = config.getString("database.postgresql.username") ?: "postgres"
                    val dbPassword = config.getString("database.postgresql.password") ?: "password"
                    PostgreSQLDatabase(dbName, dbUser, dbPassword, maxConnections, logger)
                }
                "mysql" -> {
                    val dbName = config.getString("database.mysql.name") ?: "skuld_names"
                    val dbUser = config.getString("database.mysql.username") ?: "root"
                    val dbPassword = config.getString("database.mysql.password") ?: "password"
                    MySQLDatabase(dbName, dbUser, dbPassword, maxConnections, logger)
                }
                else -> {
                    SQLiteDatabase(dataFolder, maxConnections, logger)
                }
            }
            dataHandler?.setup()
            nameKeeper = PlayerHistoryKeeper(dataHandler!!)
        }

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            registrar.register(buildSkullCommand())
            if (nameKeeper != null) {
                registrar.register(buildNameHistoryCommand())
            }
        }
    }

    override fun onDisable() {
        usernameCache.invalidateAll()
        textureCache.invalidateAll()
        dataHandler?.close()
        executor.shutdown()
    }

    private fun buildSkullCommand(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("skull")
            .then(Commands.argument("username", StringArgumentType.word())
                .executes(Command { ctx ->
                    val username = StringArgumentType.getString(ctx, "username")
                    val player = ctx.source.getSender() as? Player ?: return@Command 0
                    
                    executor.execute {
                        try {
                            val uuid = getUUID(username)
                            val textureData = getTextureData(uuid)
                            val skull = createSkull(uuid, username, textureData)

                            runEntitySyncTask(player) {
                                val xpCost = config.getInt("xp-cost", 100)
                                val result = economy.deductFrom(player, xpCost, ExperienceUnit.POINTS)
                                result.ifOk {
                                    player.inventory.addItem(skull)

                                    val usernameComp = Placeholder.component("username", Component.text(username, NamedTextColor.DARK_AQUA))
                                    val decrease = Placeholder.component("decrease", Component.text("-$xpCost", NamedTextColor.DARK_GREEN))
                                    player.sendRichMessage("<gray>Obtained skull of <username>! (<decrease> XP)", usernameComp, decrease)
                                }
                                result.ifErr { error ->
                                    when (error) {
                                        ExperienceError.INSUFFICIENT_EXPERIENCE -> {
                                            player.sendRichMessage("<red>You need at least $xpCost XP points!")
                                        }
                                        else -> {
                                            player.sendRichMessage("<red>An unexpected error occurred while processing your request.")
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            runEntitySyncTask(player) {
                                player.sendRichMessage("<red>Error: ${e.message?.replaceFirstChar { it.lowercase() }}")
                            }
                        }
                    }
                    Command.SINGLE_SUCCESS
                })
            )
            .build()
    }

    private fun buildNameHistoryCommand(): LiteralCommandNode<CommandSourceStack> {
        val arg = Commands.argument("player", StringArgumentType.word())
            .suggests { _, builder ->
                val prefix = builder.remaining
                nameKeeper?.getNameSuggestions(prefix)?.forEach(builder::suggest)
                builder.buildFuture()
            }
            .executes { ctx ->
                val targetName = StringArgumentType.getString(ctx, "player")
                val sender = ctx.source.getSender() as? Player ?: return@executes 0

                executor.execute {
                    try {
                        val uuid = getUUID(targetName)
                        val history = nameKeeper?.getHistory(uuid) ?: emptyList()
                        runEntitySyncTask(sender) {
                            val isHistoryEmpty = history.isEmpty()
                            val targetPlaceholder = Placeholder.component(
                                "target",
                                Component.text(
                                    targetName, if (isHistoryEmpty) {
                                        NamedTextColor.DARK_RED
                                    } else {
                                        NamedTextColor.DARK_AQUA
                                    }
                                )
                            )
                            if (isHistoryEmpty) {
                                sender.sendRichMessage("<red>No name history found for <target>.", targetPlaceholder)
                            } else {
                                sender.sendRichMessage(
                                    "<gray>Name history for <target>: <dark_aqua>${history.joinToString("<dark_aqua>, <dark_aqua>")}</dark_aqua>",
                                    targetPlaceholder
                                )
                            }
                        }
                    } catch (e: Exception) {
                        runEntitySyncTask(sender) {
                            sender.sendRichMessage("<red>Error fetching history: ${e.message}")
                        }
                    }
                }
                Command.SINGLE_SUCCESS
            }

        return Commands.literal("namehistory")
            .then(arg)
            .build()
    }

    private fun runEntitySyncTask(player: Player, block: () -> Unit) {
        if (isFolia) {
            player.scheduler.run(instance, Consumer { _ -> block() }, null)
        } else {
            Bukkit.getScheduler().runTask(instance, Runnable(block))
        }
    }

    // UUID fetching with fallback
    private fun getUUID(username: String): UUID {
        return usernameCache.get(username) { fetchUUID(username) }
    }

    private fun fetchUUID(username: String): UUID {
        val encodedName = URLEncoder.encode(username, "UTF-8")

        // Try Mojang API first
        try {
            val mojangUrl = URL("https://api.mojang.com/users/profiles/minecraft/$encodedName")
            val conn = mojangUrl.openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }

            when (conn.responseCode) {
                200 -> {
                    val json = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                    return json.getString("id").toUUID()
                }
                429 -> logger.warning("Mojang API rate limited, falling back to Minetools")
                else -> throw Exception("Mojang API error (${conn.responseCode})")
            }
        } catch (e: Exception) {
            logger.warning("Failed Mojang UUID lookup: ${e.message}")
        }

        // Fallback to Minetools.eu
        try {
            val minetoolsUrl = URL("https://api.minetools.eu/uuid/$encodedName")
            val conn = minetoolsUrl.openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }

            if (conn.responseCode != 200) throw Exception("minetools error (${conn.responseCode})")

            val json = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            if (json.getString("status") != "OK") throw Exception("(from Minetools API: ${json.optString("error")})")

            return json.getString("id").toUUID()
        } catch (e: Exception) {
            throw Exception("Fallback and main Mojang API failed to match username: ${e.message}")
        }
    }

    private fun getTextureData(uuid: UUID): TextureData {
        return textureCache.get(uuid) { fetchTextureData(it) }
    }

    // Texture data and skull creation
    private fun fetchTextureData(uuid: UUID): TextureData {
        val url = URL("https://sessionserver.mojang.com/session/minecraft/profile/${uuid.toString().replace("-", "")}")
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
        }

        if (conn.responseCode != 200) throw Exception("Texture API error (${conn.responseCode})")

        val json = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        val props = json.getJSONArray("properties")

        for (i in 0 until props.length()) {
            val prop = props.getJSONObject(i)
            if (prop.getString("name") == "textures") {
                val value = prop.getString("value")
                val signature = prop.optString("signature", "")
                return TextureData(value, signature)
            }
        }
        throw Exception("No texture data found")
    }

    private fun createSkull(uuid: UUID, name: String, texture: TextureData): ItemStack {
        val skull = ItemStack(Material.PLAYER_HEAD)
        val meta = skull.itemMeta as SkullMeta
        val profile = Bukkit.createProfile(uuid, name).apply {
            setProperty(ProfileProperty("textures", texture.value, texture.signature))
        }
        meta.playerProfile = profile
        skull.itemMeta = meta
        return skull
    }

    // Utilities
    private fun String.toUUID(): UUID {
        val clean = replace(Regex("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})"), "$1-$2-$3-$4-$5")
        return UUID.fromString(clean)
    }

    private data class TextureData(val value: String, val signature: String)

    override fun saveDefaultConfig() {
        super.saveDefaultConfig()
        config.options().copyDefaults(true)
    }
}
