package io.music_assistant.client.tv.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvAccountTest {
    private val account = TvAccount(
        serverId = "server",
        serverName = "Home",
        host = "192.168.1.10",
        port = 8095,
        isTls = false,
        basePath = "",
        userId = "user",
        username = "alice",
        displayName = null,
        role = "user",
        token = "token",
    )

    @Test
    fun `a PIN checks only with the same digits`() {
        val locked = account.withPin("1234", PinMode.ON_SWITCH)
        assertTrue(locked.hasPin)
        assertTrue(locked.checkPin("1234"))
        assertFalse(locked.checkPin("4321"))
    }

    @Test
    fun `the same PIN hashes differently with each salt`() {
        assertNotEquals(account.withPin("1234", PinMode.ON_SWITCH).pinHash, account.withPin("1234", PinMode.ON_SWITCH).pinHash)
    }

    @Test
    fun `removing the PIN forgets the hash`() {
        val open = account.withPin("1234", PinMode.ON_SWITCH_AND_START).withoutPin()
        assertFalse(open.hasPin)
        assertNull(open.pinHash)
        assertEquals(PinMode.NONE, open.pinMode)
    }

    @Test
    fun `an account without a known user shows its server`() {
        assertEquals("Home", account.copy(userId = null, username = null).label)
        assertEquals("server/?", account.copy(userId = null).id)
    }
}
