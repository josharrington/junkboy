package com.ovehbe.junkboy.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionTraceCodecTest {

    @Test
    fun `round trips steps, checks and actions`() {
        val trace = listOf(
            StepTrace(
                stepId = "a",
                stepName = "Political STOP",
                matched = true,
                checks = listOf("text matches" to true, "is contact = false" to false),
                actions = listOf("reply_stop")
            ),
            StepTrace("b", "Block junk", false, listOf("spam verdict = true" to false), emptyList())
        )

        val decoded = DecisionTraceCodec.decode(DecisionTraceCodec.encode(trace))

        assertEquals(2, decoded.size)
        assertEquals("Political STOP", decoded[0].name)
        assertTrue(decoded[0].matched)
        assertEquals(2, decoded[0].checks.size)
        assertTrue(decoded[0].checks[0].passed)
        assertFalse(decoded[0].checks[1].passed)
        assertEquals(listOf("reply_stop"), decoded[0].actions)
        assertFalse(decoded[1].matched)
    }

    @Test
    fun `blank and malformed traces decode to an empty list`() {
        assertTrue(DecisionTraceCodec.decode("").isEmpty())
        assertTrue(DecisionTraceCodec.decode("not json").isEmpty())
    }
}
