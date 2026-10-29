package com.ovehbe.junkboy.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 20-message fixture, run through the reference pipeline. These are the expectations the
 * in-app "Sample messages" lab is written against.
 */
class SampleMessagesTest {

    private val pipeline = SampleMessages.samplePipeline()

    @Test
    fun `fixture has twenty unique messages`() {
        assertEquals(20, SampleMessages.all.size)
        assertEquals(20, SampleMessages.all.map { it.id }.toSet().size)
    }

    @Test
    fun `every sample matches its expected block and reply outcome`() {
        val failures = SampleMessages.run(pipeline)
            .filterNot { it.matches }
            .map { result ->
                "${result.sample.id}: blocked=${result.evaluation.blocked} " +
                    "expected=${result.sample.expectBlocked}, " +
                    "reply=${result.evaluation.replyStop} expected=${result.sample.expectReply} " +
                    "(category=${result.verdict.category})"
            }
        assertTrue("Mismatches:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `otp messages are never answered`() {
        val otp = SampleMessages.run(pipeline).filter { it.sample.hasOtp }
        assertEquals(5, otp.size)
        assertTrue(otp.none { it.evaluation.replyStop })

        // Even the one that also carries a political keyword stays untouched.
        val mixed = SampleMessages.all.first { it.id == "otp-in-political" }
        assertTrue(mixed.body.contains("Republican"))
        assertFalse(SampleMessages.run(pipeline).first { it.sample.id == "otp-in-political" }.evaluation.replyStop)
    }

    @Test
    fun `known senders are never answered`() {
        val known = SampleMessages.run(pipeline).filter { it.sample.fromContact || it.sample.hasOutboundHistory }
        assertEquals(3, known.size)
        assertTrue(known.none { it.evaluation.replyStop })
    }

    @Test
    fun `political samples are answered exactly once`() {
        val political = SampleMessages.all.filter { it.expectReply }
        assertEquals(4, political.size)
        political.forEach { sample ->
            val result = SampleMessages.run(pipeline).first { it.sample.id == sample.id }
            assertTrue(sample.id, result.evaluation.replyStop)
            assertFalse(sample.id, result.evaluation.blocked)
        }
    }

    @Test
    fun `appointments, personal messages and marketing fall through`() {
        listOf("appointment-doctor", "appointment-stop2end", "personal-uncle", "personal-friend", "marketing-stop")
            .forEach { id ->
                val result = SampleMessages.run(pipeline).first { it.sample.id == id }
                assertFalse(id, result.evaluation.blocked)
                assertFalse(id, result.evaluation.replyStop)
            }
    }

    @Test
    fun `the fixture covers every requested category`() {
        val ids = SampleMessages.all.map { it.id }
        assertTrue(ids.any { it.startsWith("otp-") })
        assertTrue(ids.any { it.startsWith("politics-") })
        assertTrue(ids.any { it.startsWith("appointment-") })
        assertTrue(ids.any { it.startsWith("personal-") })
        assertTrue(ids.any { it.startsWith("scam-") })
        assertTrue(SampleMessages.all.any { it.body.contains("Uncle", ignoreCase = true) })
    }

    @Test
    fun `a pipeline with no reply step answers nobody`() {
        val blockOnly = Pipeline(
            steps = listOf(
                Step(
                    id = "b",
                    name = "Block junk",
                    checks = listOf(CheckSpec(CheckType.SPAM_VERDICT)),
                    actions = listOf(ActionSpec(ActionType.BLOCK))
                )
            )
        )
        val results = SampleMessages.run(blockOnly)
        assertTrue(results.none { it.evaluation.replyStop })
        assertEquals(results.count { it.verdict.isBlocked }, results.count { it.evaluation.blocked })
    }
}
