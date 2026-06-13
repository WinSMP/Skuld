package org.winlogon.skuld

import org.bukkit.OfflinePlayer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.entity.OfflinePlayerMock
import org.winlogon.skuld.data.DataHandler

import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockDataHandler : DataHandler {
    private val historyMap = mutableMapOf<UUID, MutableList<String>>()
    private val nameSet = mutableSetOf<String>()

    override fun setup() = Unit

    override fun updatePlayerHistory(player: OfflinePlayer): CompletableFuture<Unit> {
        val name = player.name ?: return CompletableFuture.completedFuture(Unit)
        val uuid = player.uniqueId
        nameSet.add(name)
        historyMap.computeIfAbsent(uuid) { mutableListOf() }.add(name)
        return CompletableFuture.completedFuture(Unit)
    }

    override fun getHistory(uuid: UUID): CompletableFuture<List<String>> =
        CompletableFuture.completedFuture(historyMap[uuid]?.toList() ?: emptyList())

    override fun getNameSuggestions(prefix: String): CompletableFuture<List<String>> =
        CompletableFuture.completedFuture(nameSet.filter { it.startsWith(prefix) })

    override fun close() = Unit
}

class PlayerHistoryIntegrationTest {
    private lateinit var dataHandler: MockDataHandler
    private lateinit var playerHistoryKeeper: PlayerHistoryKeeper

    @BeforeEach
    fun setUp() {
        dataHandler = MockDataHandler()
        playerHistoryKeeper = PlayerHistoryKeeper(dataHandler)
    }

    @Test
    fun `getHistory returns empty for unknown player`() {
        val uuid = UUID.randomUUID()
        assertTrue(playerHistoryKeeper.getHistory(uuid).isEmpty())
    }

    @Test
    fun `updatePlayerHistory and getHistory work end-to-end`() {
        val playerUUID = UUID.randomUUID()
        val offlinePlayer = OfflinePlayerMock(playerUUID, "InitialPlayerName")

        dataHandler.updatePlayerHistory(offlinePlayer).join()

        val history = playerHistoryKeeper.getHistory(playerUUID)
        assertEquals(listOf("InitialPlayerName"), history)
    }

    @Test
    fun `name history accumulates entries`() {
        val playerUUID = UUID.randomUUID()

        dataHandler.updatePlayerHistory(OfflinePlayerMock(playerUUID, "OldName")).join()
        dataHandler.updatePlayerHistory(OfflinePlayerMock(playerUUID, "NewName")).join()

        val history = playerHistoryKeeper.getHistory(playerUUID)
        assertEquals(listOf("OldName", "NewName"), history)
    }

    @Test
    fun `getNameSuggestions returns matching names`() {
        dataHandler.updatePlayerHistory(OfflinePlayerMock(UUID.randomUUID(), "Steve")).join()
        dataHandler.updatePlayerHistory(OfflinePlayerMock(UUID.randomUUID(), "Stephen")).join()
        dataHandler.updatePlayerHistory(OfflinePlayerMock(UUID.randomUUID(), "Alex")).join()

        val suggestions = dataHandler.getNameSuggestions("St").join()
        assertEquals(setOf("Steve", "Stephen"), suggestions.toSet())
    }

    @Test
    fun `getNameSuggestions returns empty for no matches`() {
        dataHandler.updatePlayerHistory(OfflinePlayerMock(UUID.randomUUID(), "Steve")).join()

        val suggestions = dataHandler.getNameSuggestions("Xyz").join()
        assertTrue(suggestions.isEmpty())
    }
}
