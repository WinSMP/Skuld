// SPDX-License-Identifier: MPL-2.0
package org.winlogon.skuld

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode

import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.winlogon.xpconomy.ExperienceError
import org.winlogon.xpconomy.ExperienceUnit

class CommandRegistry(private val plugin: Skuld) {
    fun buildSkullCommand(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("skull")
            .then(Commands.argument("username", StringArgumentType.word())
                .executes(Command { ctx ->
                    val username = StringArgumentType.getString(ctx, "username")
                    val player = ctx.source.sender as? Player ?: return@Command Command.SINGLE_SUCCESS

                    plugin.executor.execute {
                        runCatching {
                            val uuid = plugin.getUUID(username)
                            val textureData = plugin.getTextureData(uuid)
                            val skull = plugin.createSkull(uuid, username, textureData)

                            plugin.runEntitySyncTask(player) {
                                giveSkullToPlayer(player, username, skull)
                            }
                        }.onFailure { e ->
                            player.sendRichMessage("<red>Error: ${e.message?.replaceFirstChar { it.lowercase() }}")
                        }
                    }
                    Command.SINGLE_SUCCESS
                })
            )
            .build()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun giveSkullToPlayer(player: Player, username: String, skull: ItemStack) {
        val xpCost = plugin.skuldConfig.xpCost
        val result = plugin.economy.deductFrom(player, xpCost, ExperienceUnit.POINTS)

        result.fold({
            player.inventory.addItem(skull)

            val usernameComp = Placeholder.component("username", Component.text(username, NamedTextColor.DARK_AQUA))
            val decrease = Placeholder.component("decrease", Component.text("-$xpCost", NamedTextColor.DARK_GREEN))
            player.sendRichMessage("<gray>Obtained skull of <username>! (<decrease> XP)", usernameComp, decrease)
        }, { error ->
            when (error) {
                ExperienceError.INSUFFICIENT_EXPERIENCE -> {
                    player.sendRichMessage("<red>You need at least <dark_red>$xpCost</dark_red> XP points!</red>")
                }
                else -> {
                    player.sendRichMessage("<red>An unexpected error occurred while processing your request.")
                    plugin.logger.info("An error occurred while processing ${player.name}'s request: $error")
                }
            }
        })
    }

    fun buildNameHistoryCommand(): LiteralCommandNode<CommandSourceStack> {
        val arg = Commands.argument("player", StringArgumentType.word())
            .suggests { _, builder ->
                val prefix = builder.remaining
                plugin.nameKeeper?.getNameSuggestions(prefix)?.forEach(builder::suggest)
                builder.buildFuture()
            }
            .executes { ctx ->
                val targetName = StringArgumentType.getString(ctx, "player")
                val sender = ctx.source.sender as? Player ?: return@executes 0

                plugin.executor.execute {
                    runCatching {
                        getNameHistory(sender, targetName)
                    }.onFailure { e ->
                        sender.sendRichMessage("<red>Error fetching history: ${e.message}")
                    }
                }
                Command.SINGLE_SUCCESS
            }

        return Commands.literal("namehistory")
            .then(arg)
            .build()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun getNameHistory(sender: Player, targetName: String) {
        val uuid = plugin.getUUID(targetName)
        val history = plugin.nameKeeper?.getHistory(uuid).orEmpty()

        plugin.runEntitySyncTask(sender) {
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
                return@runEntitySyncTask
            }

            sender.sendRichMessage(
                "<gray>Name history for <target>: <dark_aqua>${history.joinToString("<dark_aqua>, <dark_aqua>")}</dark_aqua>",
                targetPlaceholder
            )
        }
    }
}
