package org.winlogon.skuld

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

import java.util.concurrent.ThreadLocalRandom
import kotlin.math.exp

class PlayerDeathListener(private val plugin: Skuld) : Listener {
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val killer = event.damageSource.causingEntity as? Player ?: return
        val victim = event.player

        val probability = MAX_CHANCE * (1 - exp(-K * killer.totalExperience))
        val roll = ThreadLocalRandom.current().nextDouble()

        // roll before touching the network, so a failed roll costs nothing
        if (roll < probability) {
            plugin.executor.execute {
                runCatching {
                    val texture = plugin.skullGetter.getTextureData(victim.uniqueId)

                    plugin.runEntitySyncTask(killer) {
                        val skull = plugin.skullGetter.createSkull(victim.uniqueId, victim.name, texture)

                        // XXX: revisit the implementation if we add more than one item
                        if (killer.inventory.addItem(skull).isEmpty()) {
                            return@runEntitySyncTask
                        } else {
                            killer.world.dropItemNaturally(killer.location, skull)
                        }
                    }
                }.onFailure { e ->
                    plugin.logger.warning("Could not give ${killer.name} the skull of ${victim.name}: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val MAX_CHANCE = 0.90
        private const val K = 0.0005
    }
}
