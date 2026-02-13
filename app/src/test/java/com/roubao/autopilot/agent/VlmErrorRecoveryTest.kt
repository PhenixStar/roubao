package com.roubao.autopilot.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for VlmErrorRecovery strategy logic.
 *
 * Pure Kotlin - no Android dependencies. Verifies that the progressive
 * recovery strategy returns correct actions for each failure count.
 */
class VlmErrorRecoveryTest {

    @Test
    fun `failure 1 returns RETRY_NORMAL`() {
        val strategy = VlmErrorRecovery.getRecoveryStrategy(1)
        assertNotNull(strategy)
        assertEquals(VlmErrorRecovery.RecoveryStrategy.RETRY_NORMAL, strategy)
    }

    @Test
    fun `failure 2 returns RETRY_NORMAL`() {
        val strategy = VlmErrorRecovery.getRecoveryStrategy(2)
        assertEquals(VlmErrorRecovery.RecoveryStrategy.RETRY_NORMAL, strategy)
    }

    @Test
    fun `failure 3 returns RETRY_SIMPLIFIED`() {
        val strategy = VlmErrorRecovery.getRecoveryStrategy(3)
        assertEquals(VlmErrorRecovery.RecoveryStrategy.RETRY_SIMPLIFIED, strategy)
    }

    @Test
    fun `failure 4 returns WAIT_AND_RETRY`() {
        val strategy = VlmErrorRecovery.getRecoveryStrategy(4)
        assertEquals(VlmErrorRecovery.RecoveryStrategy.WAIT_AND_RETRY, strategy)
    }

    @Test
    fun `failure 5 returns null - give up`() {
        val strategy = VlmErrorRecovery.getRecoveryStrategy(5)
        assertNull("Should give up after 5 consecutive failures", strategy)
    }

    @Test
    fun `failure 10 returns null - give up`() {
        val strategy = VlmErrorRecovery.getRecoveryStrategy(10)
        assertNull(strategy)
    }

    @Test
    fun `failure 0 returns null - no failure`() {
        // 0 consecutive failures falls into the else branch
        val strategy = VlmErrorRecovery.getRecoveryStrategy(0)
        assertNull(strategy)
    }

    @Test
    fun `max consecutive failures constant is 5`() {
        assertEquals(5, VlmErrorRecovery.MAX_CONSECUTIVE_FAILURES)
    }

    @Test
    fun `all strategies below max return non-null`() {
        for (i in 1 until VlmErrorRecovery.MAX_CONSECUTIVE_FAILURES) {
            assertNotNull(
                "Failure count $i should have a recovery strategy",
                VlmErrorRecovery.getRecoveryStrategy(i)
            )
        }
    }

    @Test
    fun `at max and above all return null`() {
        for (i in VlmErrorRecovery.MAX_CONSECUTIVE_FAILURES..10) {
            assertNull(
                "Failure count $i should give up",
                VlmErrorRecovery.getRecoveryStrategy(i)
            )
        }
    }
}
