package org.winlogon.skuld

import de.exlll.configlib.NameFormatters
import de.exlll.configlib.YamlConfigurations

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Logger

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
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

    internal var nameKeeper: PlayerHistoryKeeper? = null
    internal lateinit var skullGetter: SkullGetter
        private set
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

        // -- Skull getter (UUID/texture caches) --

        skullGetter = SkullGetter(skuldConfig, logger)

        // -- Death listener (drops the victim's skull on PvP kills) --

        server.pluginManager.registerEvents(PlayerDeathListener(this), this)

        // -- Database setup via Exposed (auto-detects vendor from config) --

        if (skuldConfig.history.enabled) {
            val database = skuldConfig.database

            val db = createDatabase(
                dataFolder,
                database.type,
                database.maxConnections,
                PostgresqlDsn(
                    database.postgresql.name,
                    database.postgresql.username,
                    database.postgresql.password,
                ),
                MysqlDsn(
                    database.mysql.name,
                    database.mysql.username,
                    database.mysql.password,
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
        if (::skullGetter.isInitialized) skullGetter.close()
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
}
