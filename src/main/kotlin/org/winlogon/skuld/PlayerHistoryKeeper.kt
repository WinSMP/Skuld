package org.winlogon.skuld

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.postgresql.ds.PGSimpleDataSource
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

class PlayerHistoryKeeper(
    dbName: String,
    dbUser: String,
    dbPassword: String,
    maxConnections: Int = 10,
    private val logger: Logger
) {
    private val pool: VirtualThreadConnectionPool

    init {
        val dbUrl = "jdbc:postgresql://localhost:5432/$dbName"
        val ds = PGSimpleDataSource().apply {
            setURL(dbUrl)
            user = dbUser
            password = dbPassword
        }
        pool = VirtualThreadConnectionPool(ds, maxConnections)

        createTableIfNotExists(ds)

        // on startup, ensure everyone’s last_seen is up to date
        Bukkit.getOfflinePlayers().forEach { player ->
            val name = player.name ?: return@forEach
            pool.runQuery { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO player_history(name, last_seen)
                      VALUES (?, now())
                    ON CONFLICT (name)
                      DO UPDATE SET last_seen = EXCLUDED.last_seen
                    """
                ).use { ps ->
                    ps.setString(1, name)
                    ps.executeUpdate()
                }
            }.exceptionally { err ->
                logger.severe("Failed to update history for $name: ${err.message}")
                null
            }
        }
    }

    /**
     * Returns _all_ recorded names for the given UUID, most-recent first.
     * Assumes you have a table player_name_history(uuid UUID, name TEXT, changed_at TIMESTAMP).
     */
    fun getHistory(uuid: UUID): List<String> {
        return pool.runQuery { conn ->
            conn.prepareStatement(
                """
                SELECT name
                  FROM player_name_history
                 WHERE uuid = ?
                 ORDER BY changed_at DESC
                """
            ).use { ps ->
                ps.setObject(1, uuid)
                ps.executeQuery().map { it.getString("name") }
            }
        }.exceptionally { err ->
            logger.severe("Failed to get name history for $uuid: ${err.message}")
            emptyList()
        }.get()
    }

    /**
     * Return up to 10 player‐names that start with [prefix], for tab‐completion.
     */
    fun getNameSuggestions(prefix: String): List<String> {
        val pattern = "$prefix%"
        return pool.runQuery { conn ->
            conn.prepareStatement(
                """
                SELECT DISTINCT name, last_seen
                  FROM player_history
                 WHERE name ILIKE ?
                 ORDER BY last_seen DESC
                 LIMIT 10
                """
            ).use { ps ->
                ps.setString(1, pattern)
                ps.executeQuery().map { it.getString("name") }
            }
        }.exceptionally { err ->
            logger.severe("Failed to get name suggestions for '$prefix': ${err.message}")
            emptyList()
        }.get()
    }

    private fun createTableIfNotExists(ds: PGSimpleDataSource) {
        ds.connection.use { conn ->
            conn.prepareStatement(
                """
                CREATE TABLE IF NOT EXISTS player_history (
                  name TEXT NOT NULL,
                  last_seen TIMESTAMP NOT NULL,
                  PRIMARY KEY (name)
                );

                CREATE TABLE IF NOT EXISTS player_name_history (
                  uuid UUID NOT NULL,
                  name TEXT NOT NULL,
                  changed_at TIMESTAMP NOT NULL DEFAULT NOW()
                );
                """
            ).use { ps ->
                ps.executeUpdate()
            }
        }
    }
}
