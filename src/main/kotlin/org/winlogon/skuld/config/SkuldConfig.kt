package org.winlogon.skuld.config

import de.exlll.configlib.Comment
import de.exlll.configlib.Configuration

@Configuration
class SkuldConfig {
    @Comment("The cost of getting a player's skull, in experience points")
    var xpCost: Int = 100

    var cache: CacheConfig = CacheConfig()
    var history: HistoryConfig = HistoryConfig()
    var database: DatabaseConfig = DatabaseConfig()
}

@Configuration
class CacheConfig {
    @Comment("How many days before cached UUID/texture data expires")
    var expirationDays: Long = 3
}

@Configuration
class HistoryConfig {
    @Comment("Whether to enable the name history feature.\nIf this is disabled, you do not need a database.")
    var enabled: Boolean = true
}

@Configuration
class DatabaseConfig {
    @Comment("The type of database to use. Can be \"sqlite\", \"mysql\", or \"postgresql\".")
    var type: String = "sqlite"

    @Comment("The maximum number of connections to the database.")
    var maxConnections: Int = 10

    var postgresql: PostgresqlConfig = PostgresqlConfig()
    var mysql: MysqlConfig = MysqlConfig()
}

@Configuration
class PostgresqlConfig {
    var name: String = "skuld_names"
    var username: String = "postgres"
    var password: String = "password"
}

@Configuration
class MysqlConfig {
    var name: String = "skuld_names"
    var username: String = "root"
    var password: String = "password"
}
