// SPDX-License-Identifier: MPL-2.0
package org.winlogon.skuld.data

import org.bukkit.OfflinePlayer
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface DataHandler {
    fun setup()
    fun updatePlayerHistory(player: OfflinePlayer): CompletableFuture<Unit>
    fun getHistory(uuid: UUID): CompletableFuture<List<String>>
    fun getNameSuggestions(prefix: String): CompletableFuture<List<String>>
    fun close()
}
