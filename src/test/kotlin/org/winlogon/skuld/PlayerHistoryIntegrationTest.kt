package org.winlogon.skuld

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.bukkit.OfflinePlayer
import org.winlogon.skuld.data.DataHandler
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockDataHandler : DataHandler {
    // TODO: implement mock data handler
}

class PlayerHistoryIntegrationTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: Skuld
    private lateinit var dataHandler: DataHandler
    private lateinit var playerHistoryKeeper: PlayerHistoryKeeper

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        plugin = mock()
        dataHandler = mock()
        playerHistoryKeeper = PlayerHistoryKeeper(dataHandler)

        whenever(plugin.nameKeeper).thenReturn(playerHistoryKeeper)
        whenever(plugin.logger).thenReturn(mock())

        // Mock DataHandler methods
        whenever(dataHandler.getHistory(any())).thenReturn(CompletableFuture.completedFuture(emptyList()))
        whenever(dataHandler.getNameSuggestions(any())).thenReturn(CompletableFuture.completedFuture(emptyList()))
        whenever(dataHandler.updatePlayerHistory(any())).thenReturn(CompletableFuture.completedFuture(Unit))
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `player name history should be updated on join after name change`() {
        val playerUUID = UUID.randomUUID()
        val initialName = "InitialPlayerName"
        val newName = "NewPlayerName"

        val playerMockInstance = PlayerMock(server, initialName, playerUUID)
        server.addPlayer(playerMockInstance)
        server.scheduler.performTicks(1)

        verify(dataHandler).updatePlayerHistory(playerMockInstance)
        whenever(dataHandler.getHistory(playerUUID)).thenReturn(CompletableFuture.completedFuture(listOf(initialName)))
        assertEquals(listOf(initialName), playerHistoryKeeper.getHistory(playerUUID))

        playerMockInstance.disconnect()
        server.scheduler.performTicks(1)

        playerMockInstance.setName(newName)

        playerMockInstance.reconnect()
        server.scheduler.performTicks(1)

        verify(dataHandler).updatePlayerHistory(playerMockInstance)
        whenever(dataHandler.getHistory(playerUUID)).thenReturn(CompletableFuture.completedFuture(listOf(newName, initialName)))
        assertEquals(listOf(newName, initialName), playerHistoryKeeper.getHistory(playerUUID))
    }
}
