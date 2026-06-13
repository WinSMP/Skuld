// SPDX-License-Identifier: MPL-2.0
package org.winlogon.skuld

import org.bukkit.OfflinePlayer
import org.winlogon.skuld.data.DataHandler
import java.util.UUID

class PlayerHistoryKeeper(private val dataHandler: DataHandler) {
    fun updatePlayerHistory(player: OfflinePlayer) {
        dataHandler.updatePlayerHistory(player).join()
    }

    fun getHistory(uuid: UUID): List<String> {
        return dataHandler.getHistory(uuid).join()
    }

    fun getNameSuggestions(prefix: String): List<String> {
        return dataHandler.getNameSuggestions(prefix).join()
    }

    fun close() {
        dataHandler.close()
    }
}
