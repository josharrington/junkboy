package com.ovehbe.junkboy.pipeline

import com.ovehbe.junkboy.database.MessageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionPlanTest {

    private fun evaluation(
        blocked: Boolean = false,
        reply: Boolean = false,
        muted: Boolean = false,
        delete: Boolean = false
    ) = Evaluation(
        category = if (blocked) MessageCategory.JUNK else MessageCategory.GENERAL,
        blocked = blocked,
        muted = muted,
        delete = delete,
        replyStop = reply,
        blockedBy = if (blocked) "Block junk" else null,
        repliedBy = if (reply) "Political STOP" else null,
        trace = emptyList()
    )

    @Test
    fun `enforcing performs the evaluation`() {
        val plan = ExecutionPlan.forMode(evaluation(blocked = true, reply = true, muted = true, delete = true), true)

        assertTrue(plan.block)
        assertFalse(plan.notify)
        assertTrue(plan.replyStop)
        assertTrue(plan.mute)
        assertTrue(plan.delete)
    }

    @Test
    fun `enforcing notifies an allowed message`() {
        val plan = ExecutionPlan.forMode(evaluation(), true)

        assertFalse(plan.block)
        assertTrue(plan.notify)
        assertFalse(plan.replyStop)
        assertFalse(plan.delete)
    }

    @Test
    fun `testing performs nothing and still notifies`() {
        val plan = ExecutionPlan.forMode(evaluation(blocked = true, reply = true, muted = true, delete = true), false)

        assertFalse(plan.block)
        assertTrue(plan.notify)
        assertFalse(plan.replyStop)
        assertFalse(plan.mute)
        assertFalse(plan.delete)
    }

    @Test
    fun `decision summary describes what would happen`() {
        val blocked = evaluation(blocked = true, reply = true, muted = true, delete = true).decisionSummary()
        assertTrue(blocked.contains("Would block as junk (Block junk)"))
        assertTrue(blocked.contains("would reply STOP (Political STOP)"))
        assertTrue(blocked.contains("would mute"))
        assertTrue(blocked.contains("would delete"))

        val allowed = evaluation().decisionSummary()
        assertEquals("Would allow as general", allowed)
    }
}
