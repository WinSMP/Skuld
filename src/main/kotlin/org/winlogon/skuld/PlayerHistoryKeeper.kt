// SPDX-License-Identifier: MPL-2.0
package org.winlogon.skuld

import org.winlogon.skuld.data.DataHandler
import java.util.UUID

class PlayerHistoryKeeper(private val dataHandler: DataHandler) {
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
