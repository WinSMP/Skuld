package org.winlogon.skuld

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.entity.OfflinePlayerMock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.winlogon.skuld.data.DataHandler
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerHistoryKeeperTest {
    private lateinit var dataHandler: DataHandler
    private lateinit var playerHistoryKeeper: PlayerHistoryKeeper

    @BeforeEach
    fun setUp() {
        dataHandler = mock()
        playerHistoryKeeper = PlayerHistoryKeeper(dataHandler)
    }

    @Test
    fun `getHistory should return history from dataHandler`() {
        val uuid = UUID.randomUUID()
        val expectedHistory = listOf("oldName1", "oldName2")
        whenever(dataHandler.getHistory(uuid)).thenReturn(CompletableFuture.completedFuture(expectedHistory))

        val actualHistory = playerHistoryKeeper.getHistory(uuid)

        assertEquals(expectedHistory, actualHistory)
    }

    @Test
    fun `getHistory should return empty list if dataHandler returns empty`() {
        val uuid = UUID.randomUUID()
        whenever(dataHandler.getHistory(uuid)).thenReturn(CompletableFuture.completedFuture(emptyList()))

        val actualHistory = playerHistoryKeeper.getHistory(uuid)

        assertTrue(actualHistory.isEmpty())
    }

    @Test
    fun `getNameSuggestions should return suggestions from dataHandler`() {
        val prefix = "test"
        val expectedSuggestions = listOf("testName1", "testName2")
        whenever(dataHandler.getNameSuggestions(prefix)).thenReturn(CompletableFuture.completedFuture(expectedSuggestions))

        val actualSuggestions = playerHistoryKeeper.getNameSuggestions(prefix)

        assertEquals(expectedSuggestions, actualSuggestions)
    }

    @Test
    fun `updatePlayerHistory should delegate to dataHandler`() {
        val player = OfflinePlayerMock(UUID.randomUUID(), "TestPlayer")
        whenever(dataHandler.updatePlayerHistory(any())).thenReturn(CompletableFuture.completedFuture(Unit))

        playerHistoryKeeper.updatePlayerHistory(player)

        verify(dataHandler).updatePlayerHistory(player)
    }

    @Test
    fun `getNameSuggestions should return empty list if dataHandler returns empty`() {
        val prefix = "test"
        whenever(dataHandler.getNameSuggestions(prefix)).thenReturn(CompletableFuture.completedFuture(emptyList()))

        val actualSuggestions = playerHistoryKeeper.getNameSuggestions(prefix)

        assertTrue(actualSuggestions.isEmpty())
    }
}
