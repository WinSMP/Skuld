package org.winlogon.skuld

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerHistoryIntegrationTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: Skuld

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        plugin = MockBukkit.load(Skuld::class.java)
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `player join adds name to history`() {
        val player = server.addPlayer("TestPlayer")

        val history = plugin.nameKeeper!!.getHistory(player.uniqueId)
        assertEquals(listOf("TestPlayer"), history)
    }

    @Test
    fun `name change accumulates history`() {
        val uuid = UUID.randomUUID()

        val player1 = PlayerMock(server, "OldName", uuid)
        server.addPlayer(player1)

        var history = plugin.nameKeeper!!.getHistory(uuid)
        assertEquals(listOf("OldName"), history)

        player1.disconnect()

        val player2 = PlayerMock(server, "NewName", uuid)
        server.addPlayer(player2)

        history = plugin.nameKeeper!!.getHistory(uuid)
        assertEquals(listOf("NewName", "OldName"), history)
    }

    @Test
    fun `rejoin with same name does not duplicate history`() {
        val uuid = UUID.randomUUID()

        val player1 = PlayerMock(server, "SameName", uuid)
        server.addPlayer(player1)
        player1.disconnect()

        val player2 = PlayerMock(server, "SameName", uuid)
        server.addPlayer(player2)

        val history = plugin.nameKeeper!!.getHistory(uuid)
        assertEquals(listOf("SameName"), history)
    }

    @Test
    fun `getHistory returns empty for unknown player`() {
        assertTrue(plugin.nameKeeper!!.getHistory(UUID.randomUUID()).isEmpty())
    }

    @Test
    fun `getNameSuggestions returns matching names`() {
        server.addPlayer("Steve")
        server.addPlayer("Stephen")
        server.addPlayer("Alex")

        val suggestions = plugin.nameKeeper!!.getNameSuggestions("St")
        assertEquals(setOf("Steve", "Stephen"), suggestions.toSet())
    }

    @Test
    fun `getNameSuggestions returns empty for no matches`() {
        server.addPlayer("Steve")

        val suggestions = plugin.nameKeeper!!.getNameSuggestions("Xyz")
        assertTrue(suggestions.isEmpty())
    }
}
