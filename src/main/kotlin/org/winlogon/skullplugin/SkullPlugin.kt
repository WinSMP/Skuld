package org.winlogon.skullplugin

import com.destroystokyo.paper.profile.ProfileProperty
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.OfflinePlayerArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import dev.jorel.commandapi.arguments.StringArgument
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.function.Consumer
import java.util.*

class SkullPlugin : JavaPlugin() {
    companion object {
        lateinit var instance: SkullPlugin
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
        registerCommands()
    }

    private fun registerCommands() {
        CommandAPICommand("skull")
            .withArguments(StringArgument("username"))
            .executesPlayer(PlayerCommandExecutor { player, args ->
                val username = args.get("username") as String
                val xpCost = config.getInt("xp-cost", 100)

                runAsync {
                    try {
                        val uuid = getUUID(username)
                        val textureData = getTextureData(uuid)
                        val skull = createSkull(uuid, username, textureData)

                        runSync(player) {
                            val currentXP = player.calculateTotalExperiencePoints()
                            if (currentXP < xpCost) {
                                player.sendMessage("§cYou need at least $xpCost XP points!")
                                return@runSync
                            }

                            player.setExperienceLevelAndProgress(currentXP - xpCost)
                            player.inventory.addItem(skull)
                            player.sendMessage("§7Obtained skull of §3$username§7! (-$xpCost XP)")
                        }
                    } catch (e: Exception) {
                        runSync(player) {
                            player.sendMessage("§cError: ${e.message?.replaceFirstChar { it.lowercase() }}")
                        }
                    }
                }
            })
            .register()
    }

    private fun runAsync(task: () -> Unit) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(instance, Consumer<ScheduledTask> { task() })
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(instance, task)
        }
    }
    
    private fun runSync(player: Player, task: () -> Unit) {
        if (isFolia) {
            player.scheduler.run(instance, Consumer<ScheduledTask> { task() }, null)
        } else {
            Bukkit.getScheduler().runTask(instance, task)
        }
    }

    // UUID fetching with fallback
    private fun getUUID(username: String): UUID {
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

            if (conn.responseCode != 200) throw Exception("Minetools API error (${conn.responseCode})")

            val json = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            if (json.getString("status") != "OK") throw Exception("Minetools API: ${json.optString("error")}")

            return json.getString("id").toUUID()
        } catch (e: Exception) {
            throw Exception("Failed both Mojang and Minetools API: ${e.message}")
        }
    }

    // Texture data and skull creation
    private fun getTextureData(uuid: UUID): TextureData {
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
