// SPDX-License-Identifier: MPL-2.0
package org.winlogon.skuld.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

import org.bukkit.OfflinePlayer
import org.winlogon.skuld.VirtualThreadConnectionPool
import org.winlogon.skuld.map

class SQLiteDatabase(
    private val dataFolder: File,
    private val maxConnections: Int,
    private val logger: Logger
) : DataHandler {
    private lateinit var pool: VirtualThreadConnectionPool
    private lateinit var dbUrl: String

    override fun setup() {
        val dbFile = File(dataFolder, "skuld.db")
        dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"

        // Ensure SQLite driver is loaded
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (ex: ClassNotFoundException) {
            throw IllegalStateException("SQLite JDBC driver not found on classpath!", ex)
        }

        pool = VirtualThreadConnectionPool(this::getConnection, maxConnections)
        createTables()
    }

    private fun getConnection(): Connection =
        DriverManager.getConnection(dbUrl)

    private fun createTables() {
        getConnection().use { conn ->
            conn.createStatement().use { st ->
                st.addBatch(
                    """
                    CREATE TABLE IF NOT EXISTS player_history (
                      name TEXT NOT NULL PRIMARY KEY,
                      last_seen INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.addBatch(
                    """
                    CREATE TABLE IF NOT EXISTS player_name_history (
                      uuid TEXT NOT NULL,
                      name TEXT NOT NULL,
                      changed_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
                    )
                    """.trimIndent()
                )
                st.executeBatch()
            }
        }
    }

    override fun updatePlayerHistory(player: OfflinePlayer): CompletableFuture<Unit> {
        val name = player.name ?: return CompletableFuture.completedFuture(Unit)
        return pool.runQuery { conn ->
            conn.prepareStatement(
                """
                INSERT INTO player_history (name, last_seen)
                  VALUES (?, strftime('%s','now'))
                ON CONFLICT(name) DO UPDATE SET last_seen = EXCLUDED.last_seen
                """
            ).use { ps ->
                ps.setString(1, name)
                ps.executeUpdate()
                Unit
            }
        }.exceptionally { err ->
            logger.severe("Failed to update history for $name: ${err.message}")
            Unit
        }
    }

    override fun getHistory(uuid: UUID): CompletableFuture<List<String>> = pool.runQuery { conn ->
        conn.prepareStatement(
            """
            SELECT name FROM player_name_history
             WHERE uuid = ?
             ORDER BY changed_at DESC
            """
        ).use { ps ->
            ps.setString(1, uuid.toString())
            ps.executeQuery().map { it.getString("name") }
        }
    }.exceptionally { err ->
        logger.severe("Failed to get name history for $uuid: ${err.message}")
        emptyList()
    }

    override fun getNameSuggestions(prefix: String): CompletableFuture<List<String>> {
        val pattern = "$prefix%"
        return pool.runQuery { conn ->
            conn.prepareStatement(
                """
                SELECT DISTINCT name, last_seen
                  FROM player_history
                 WHERE name LIKE ?
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
        }
    }

    override fun close() = pool.shutdown()
}
