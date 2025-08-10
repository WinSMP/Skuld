// SPDX-License-Identifier: MPL-2.0
package org.winlogon.skuld

import org.bukkit.Bukkit

import java.sql.Connection
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import javax.sql.DataSource

class VirtualThreadConnectionPool(
    private val ds: DataSource,
    maxConnections: Int
) {
    private val sem = Semaphore(maxConnections)
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    fun <T> runQuery(action: (Connection) -> T): CompletableFuture<T> =
        CompletableFuture.supplyAsync({
            sem.acquire()
            try {
                ds.connection.use { conn ->
                    action(conn)
                }
            } finally {
                sem.release()
            }
        }, executor)

    fun shutdown() {
        executor.shutdown()
    }

    companion object {
        /**
         * Create a pool using a custom Connection supplier instead of a DataSource.
         */
        operator fun invoke(getConn: () -> Connection, maxConnections: Int): VirtualThreadConnectionPool {
            val ds = object : DataSource {
                override fun getConnection(): Connection = getConn()
                override fun getConnection(username: String?, password: String?) = getConn()

                @Suppress("UNCHECKED_CAST")
                override fun <T> unwrap(iface: Class<T>): T {
                    throw UnsupportedOperationException("unwrap not supported")
                }

                override fun isWrapperFor(iface: Class<*>?): Boolean = false
                override fun setLoginTimeout(seconds: Int) {}
                override fun getLoginTimeout(): Int = 0
                override fun getLogWriter(): java.io.PrintWriter? = null
                override fun setLogWriter(out: java.io.PrintWriter?) {}
                override fun getParentLogger(): java.util.logging.Logger =
                    java.util.logging.Logger.getGlobal()
            }
            return VirtualThreadConnectionPool(ds, maxConnections)
        }
    }
}
