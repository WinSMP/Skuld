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
}

