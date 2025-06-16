// SPDX-License-Identifier: MPL-2.0
package org.winlogon.skuld

import java.sql.ResultSet

/**
 * Helper to turn a JDBC ResultSet into a Kotlin List<T>
 */
fun <T> ResultSet.map(transform: (ResultSet) -> T): List<T> {
    val list = mutableListOf<T>()
    while (next()) {
        list += transform(this)
    }
    return list
}
