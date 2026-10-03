package app.amegram.module.ghost

import app.amegram.module.features.ghost.AmegramGhostPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmegramGhostPolicyTest {

    @Test
    fun `dormant module defers to legacy`() {
        assertTrue(AmegramGhostPolicy.resolveSendRead(false, true, true))
        assertFalse(AmegramGhostPolicy.resolveSendRead(false, false, false))
        assertTrue(AmegramGhostPolicy.resolveSendOnline(false, true, true))
        assertTrue(AmegramGhostPolicy.resolveSendTyping(false, true, true))
    }

    @Test
    fun `active module hide flags win over legacy`() {
        // legacy says "send", module says "hide" -> effective is block
        assertFalse(AmegramGhostPolicy.resolveSendRead(true, true, true))
        assertFalse(AmegramGhostPolicy.resolveSendOnline(true, true, true))
        assertFalse(AmegramGhostPolicy.resolveSendTyping(true, true, true))
        // module says "show" -> effective is send even if legacy blocked
        assertTrue(AmegramGhostPolicy.resolveSendRead(true, false, false))
    }

    @Test
    fun `typing blocked unless excluded`() {
        assertTrue(AmegramGhostPolicy.shouldBlockTyping(false, false))
        assertFalse(AmegramGhostPolicy.shouldBlockTyping(false, true))
        assertFalse(AmegramGhostPolicy.shouldBlockTyping(true, false))
    }

    @Test
    fun `read blocked needs all three conditions`() {
        assertTrue(AmegramGhostPolicy.shouldBlockRead(false, false, false))
        assertFalse(AmegramGhostPolicy.shouldBlockRead(true, false, false))
        assertFalse(AmegramGhostPolicy.shouldBlockRead(false, true, false))
        assertFalse(AmegramGhostPolicy.shouldBlockRead(false, false, true))
    }

    @Test
    fun `offline forced when online disabled`() {
        assertTrue(AmegramGhostPolicy.shouldForceOffline(false))
        assertFalse(AmegramGhostPolicy.shouldForceOffline(true))
    }

    @Test
    fun `stories follow read channel`() {
        assertTrue(AmegramGhostPolicy.shouldBlockStories(false, false))
        assertFalse(AmegramGhostPolicy.shouldBlockStories(false, true))
        assertFalse(AmegramGhostPolicy.shouldBlockStories(true, false))
    }
}
