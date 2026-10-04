package org.winlogon.skuld

import com.destroystokyo.paper.profile.ProfileProperty

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.json.JSONObject
import org.winlogon.skuld.config.SkuldConfig

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.Duration
import java.util.UUID
import java.util.logging.Logger

class SkullGetter(skuldConfig: SkuldConfig, private val logger: Logger) : AutoCloseable {
    private val expirationDays = skuldConfig.cache.expirationDays.coerceAtLeast(1L)
    private val usernameCache: Cache<String, UUID> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofDays(expirationDays))
        .build()
    private val textureCache: Cache<UUID, TextureData> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofDays((expirationDays - 2).coerceAtLeast(1L)))
        .build()

    private val uuidRegex = Regex("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})")

    internal data class TextureData(val value: String, val signature: String)

    // UUID fetching with fallback
    internal fun getUUID(username: String): UUID {
        return usernameCache.get(username) { fetchUUID(username) }
    }

    fun getPlayerSkull(username: String): ItemStack = getUUID(username).let { uuid ->
        createSkull(uuid, username, getTextureData(uuid))
    }

    internal fun createSkull(uuid: UUID, name: String, texture: TextureData): ItemStack {
        val profile = Bukkit.createProfile(uuid, name).apply {
            setProperty(ProfileProperty("textures", texture.value, texture.signature))
        }

        return ItemStack(Material.PLAYER_HEAD).apply {
            itemMeta = (itemMeta as SkullMeta).apply { playerProfile = profile }
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

    // Utilities
    internal fun String.toUUID(): UUID {
        val clean = replace(uuidRegex, "$1-$2-$3-$4-$5")
        return UUID.fromString(clean)
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

    override fun close() {
        usernameCache.invalidateAll()
        textureCache.invalidateAll()
    }
}
