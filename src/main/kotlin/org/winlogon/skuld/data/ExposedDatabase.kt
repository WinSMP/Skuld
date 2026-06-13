package org.winlogon.skuld.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import org.bukkit.OfflinePlayer
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Contains all players who have logged in and when they last logged in as an Unix timestamp.
 */
object PlayerHistoryTable : Table("player_history") {
    val name = varchar("name", 255)
    val lastSeen = long("last_seen")
    override val primaryKey = PrimaryKey(name)
}

/**
 * Contains the name history of each player who joined.
 *
 * The values in this table are added only if a player's name has changed.
 */
object PlayerNameHistoryTable : Table("player_name_history") {
    val uuid = javaUUID("uuid")
    val name = varchar("name", 255)
    val changedAt = long("changed_at")
}

class ExposedDataHandler(
    private val db: Database,
    private val executor: ExecutorService,
    private val logger: Logger,
) : DataHandler {
    init {
        transaction(db) {
            SchemaUtils.create(PlayerHistoryTable, PlayerNameHistoryTable)
        }
    }

    override fun setup() = Unit

    override fun updatePlayerHistory(player: OfflinePlayer): CompletableFuture<Unit> {
        val name = player.name ?: return CompletableFuture.completedFuture(Unit)
        val uuid = player.uniqueId

        return supplyAsync(executor) {
            transaction(db) {
                PlayerHistoryTable.replace {
                    it[PlayerHistoryTable.name] = name
                    it[lastSeen] = System.currentTimeMillis()
                }

                // Get the lastest name from the player's UUID
                val latestName = PlayerNameHistoryTable.selectAll()
                    .where { PlayerNameHistoryTable.uuid eq uuid }
                    .orderBy(PlayerNameHistoryTable.changedAt, SortOrder.DESC) // Highest Unix timestamp
                    .limit(1) // "Latest" based on Unix timestamp
                    .firstOrNull()
                    ?.get(PlayerNameHistoryTable.name) // Get correspoding name

                // Add a new row if only if the player has a new name
                if (latestName != name) {
                    PlayerNameHistoryTable.insert {
                        it[PlayerNameHistoryTable.uuid] = uuid
                        it[PlayerNameHistoryTable.name] = name
                        it[changedAt] = System.currentTimeMillis()
                    }
                }

                Unit
            }
        }.exceptionally { err ->
            logger.log(Level.SEVERE,"Failed to update history for $name", err)
            Unit
        }
    }

    override fun getHistory(uuid: UUID): CompletableFuture<List<String>> = supplyAsync(executor) {
        transaction(db) {
            PlayerNameHistoryTable.selectAll()
                .where { PlayerNameHistoryTable.uuid eq uuid }
                .orderBy(PlayerNameHistoryTable.changedAt, SortOrder.DESC)
                .map { it[PlayerNameHistoryTable.name] }
        }
    }.exceptionally { err ->
        logger.log(Level.SEVERE, "Failed to get name history for $uuid", err)
        emptyList()
    }

    override fun getNameSuggestions(prefix: String): CompletableFuture<List<String>> = supplyAsync(executor) {
        transaction(db) {
            PlayerHistoryTable.selectAll()
                .where { PlayerHistoryTable.name like "$prefix%" }
                .orderBy(PlayerHistoryTable.lastSeen, SortOrder.DESC)
                .limit(10)
                .map { it[PlayerHistoryTable.name] }
        }
    }.exceptionally { err ->
        logger.log(Level.SEVERE, "Failed to get name suggestions for '$prefix'", err)
        emptyList()
    }

    override fun close() = Unit

    private fun <T> supplyAsync(executor: ExecutorService, block: () -> T): CompletableFuture<T> =
        CompletableFuture.supplyAsync(block, executor)
}

fun createDatabase(dataFolder: File, type: String, maxConnections: Int, pgConfig: PostgresqlDsn, mysqlConfig: MysqlDsn): Database {
    val hikariConfig = HikariConfig().apply {
        when (type.lowercase()) {
            "postgresql" -> {
                jdbcUrl = "jdbc:postgresql://localhost:5432/${pgConfig.name}"
                username = pgConfig.username
                password = pgConfig.password
                driverClassName = "org.postgresql.Driver"
            }
            "mysql" -> {
                jdbcUrl = "jdbc:mysql://localhost:3306/${mysqlConfig.name}"
                username = mysqlConfig.username
                password = mysqlConfig.password
                driverClassName = "com.mysql.cj.jdbc.Driver"
            }
            else -> {
                jdbcUrl = "jdbc:sqlite:${File(dataFolder, "skuld.db").absolutePath}"
                driverClassName = "org.sqlite.JDBC"
            }
        }
        maximumPoolSize = when (type.lowercase()) {
            "sqlite" -> 1
            else -> maxConnections
        }
        connectionTimeout = 5000
        idleTimeout = 30000
    }
    return Database.connect(HikariDataSource(hikariConfig))
}

data class PostgresqlDsn(val name: String, val username: String, val password: String)
data class MysqlDsn(val name: String, val username: String, val password: String)
