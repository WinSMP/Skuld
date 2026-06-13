package org.winlogon.skuld

import com.destroystokyo.paper.profile.ProfileProperty
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

import de.exlll.configlib.NameFormatters
import de.exlll.configlib.YamlConfigurations

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Logger

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import org.json.JSONObject
import org.winlogon.skuld.config.SkuldConfig
import org.winlogon.skuld.data.DataHandler
import org.winlogon.skuld.data.ExposedDataHandler
import org.winlogon.skuld.data.MysqlDsn
import org.winlogon.skuld.data.PostgresqlDsn
import org.winlogon.skuld.data.createDatabase
import org.winlogon.xpconomy.XPConomy

open class Skuld : JavaPlugin(), Listener {
    internal val economy = XPConomy()
    internal lateinit var skuldConfig: SkuldConfig

    private val logger: Logger = getLogger()

    internal val uuidRegex = Regex("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})")

    private lateinit var usernameCache: Cache<String, UUID>
    private lateinit var textureCache: Cache<UUID, TextureData>

    internal var nameKeeper: PlayerHistoryKeeper? = null
    internal var executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
    private var dataHandler: DataHandler? = null

    companion object {
        val isFolia = checkFolia()

        private fun checkFolia(): Boolean {
            return try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }
    }

    override fun onEnable() {
        logger.info("Loading configuration...")

        // -- Config loading --

        skuldConfig = YamlConfigurations.update(
            dataFolder.toPath().resolve("config.yml"),
            SkuldConfig::class.java,
        ) { builder ->
            builder.setNameFormatter(NameFormatters.LOWER_KEBAB_CASE)
                .header("Skuld Configuration")
        }

        // -- Cache init --

        val expirationDays = skuldConfig.cache.expirationDays.coerceAtLeast(1L)
        usernameCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(expirationDays))
            .build()

        val usernameExpirationDays = (expirationDays - 2).coerceAtLeast(1L)
        textureCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(usernameExpirationDays))
            .build()

        // -- Database setup via Exposed (auto-detects vendor from config) --

        if (skuldConfig.history.enabled) {
            val db = createDatabase(
                dataFolder,
                skuldConfig.database.type,
                skuldConfig.database.maxConnections,
                PostgresqlDsn(
                    skuldConfig.database.postgresql.name,
                    skuldConfig.database.postgresql.username,
                    skuldConfig.database.postgresql.password,
                ),
                MysqlDsn(
                    skuldConfig.database.mysql.name,
                    skuldConfig.database.mysql.username,
                    skuldConfig.database.mysql.password,
                ),
            )
            dataHandler = ExposedDataHandler(db, executor, logger)
            nameKeeper = PlayerHistoryKeeper(dataHandler!!)
            server.pluginManager.registerEvents(this, this)
        }

        logger.info("Registering commands...")

        // -- Command registration --

        val commandRegistry = CommandRegistry(this)

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            registrar.apply {
                register(commandRegistry.buildSkullCommand())
                if (nameKeeper != null) {
                    register(commandRegistry.buildNameHistoryCommand())
                }
            }
        }
    }

    override fun onDisable() {
        usernameCache.invalidateAll()
        textureCache.invalidateAll()
        dataHandler?.close()
        executor.shutdown()
    }

    @EventHandler
    @Suppress("UNUSED")
    fun onPlayerJoin(event: PlayerJoinEvent) {
        nameKeeper?.updatePlayerHistory(event.player)
    }

    internal fun runEntitySyncTask(player: Player, block: () -> Unit) {
        if (isFolia) {
            player.scheduler.run(this, { _ -> block() }, null)
        } else {
            Bukkit.getScheduler().runTask(this, Runnable(block))
        }
    }

    // UUID fetching with fallback
    internal fun getUUID(username: String): UUID {
        return usernameCache.get(username) { fetchUUID(username) }
    }

    private fun fetchUUID(username: String): UUID {
        val encodedName = URLEncoder.encode(username, "UTF-8")

        // Try Mojang API first
        try {
            val mojangUrl = URI.create("https://api.mojang.com/users/profiles/minecraft/$encodedName").toURL()
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
            val minetoolsUrl = URI.create("https://api.minetools.eu/uuid/$encodedName").toURL()
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

    internal fun getTextureData(uuid: UUID): TextureData {
        return textureCache.get(uuid) { fetchTextureData(it) }
    }

    // Texture data and skull creation
    private fun fetchTextureData(uuid: UUID): TextureData {
        val urlFriendlyId = uuid.toString().replace("-", "")
        val url = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/$urlFriendlyId").toURL()
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

    internal fun createSkull(uuid: UUID, name: String, texture: TextureData): ItemStack {
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
    internal fun String.toUUID(): UUID {
        val clean = replace(uuidRegex, "$1-$2-$3-$4-$5")
        return UUID.fromString(clean)
    }

    internal data class TextureData(val value: String, val signature: String)
}
