package com.ovehbe.junkboy.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetentionPolicyTest {

    @Test
    fun `default is two months`() {
        val now = 1_000_000_000_000L
        val cutoff = RetentionPolicy.cutoffMillis(now, 60)
        assertEquals(now - 60L * 24 * 60 * 60 * 1000, cutoff)
    }

    @Test
    fun `zero disables deletion`() {
        assertNull(RetentionPolicy.cutoffMillis(1_000_000_000_000L, 0))
        assertNull(RetentionPolicy.cutoffMillis(1_000_000_000_000L, -1))
    }
}
