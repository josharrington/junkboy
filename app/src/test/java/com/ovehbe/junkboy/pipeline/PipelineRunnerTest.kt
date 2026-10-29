package com.ovehbe.junkboy.pipeline

import com.ovehbe.junkboy.database.MessageCategory
import com.ovehbe.junkboy.filters.AllowRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineRunnerTest {

    private fun ctx(
        body: String = "hello",
        sender: String = "+15555550100",
        category: MessageCategory = MessageCategory.GENERAL,
        blocked: Boolean = false,
        contact: Boolean = false,
        history: Boolean = false,
        otp: Boolean = false,
        directive: Boolean = false,
        allowedSender: Boolean = false,
        allowedContent: Boolean = false
    ) = MessageContext(
        sender = sender,
        body = body,
        timestamp = 0L,
        category = category,
        isBlocked = blocked,
        isContact = contact,
        hasOutboundHistory = history,
        hasOtp = otp,
        hasOptOutDirective = directive,
        allowedBySender = allowedSender,
        allowedByContent = allowedContent
    )

    private fun step(
        name: String,
        checks: List<CheckSpec> = emptyList(),
        actions: List<ActionSpec> = emptyList(),
        mode: MatchMode = MatchMode.ALL,
        enabled: Boolean = true
    ) = Step(id = name, name = name, enabled = enabled, mode = mode, checks = checks, actions = actions)

    private fun political(rules: List<String>) = PipelineDefaults.politicalReplyStep(
        rules.map { AllowRule(it) }
    )

    @Test
    fun `end of pipeline means allow`() {
        val evaluation = PipelineRunner.evaluate(Pipeline(steps = emptyList()), ctx())
        assertFalse(evaluation.blocked)
        assertEquals(MessageCategory.GENERAL, evaluation.category)
    }

    @Test
    fun `block is terminal and stops later steps`() {
        val pipeline = Pipeline(
            steps = listOf(
                step("blocker", listOf(CheckSpec(CheckType.SPAM_VERDICT)), listOf(ActionSpec(ActionType.BLOCK))),
                step("replier", listOf(CheckSpec(CheckType.ALWAYS)), listOf(ActionSpec(ActionType.REPLY_STOP)))
            )
        )
        val evaluation = PipelineRunner.evaluate(pipeline, ctx(blocked = true))

        assertTrue(evaluation.blocked)
        assertEquals("blocker", evaluation.blockedBy)
        assertFalse(evaluation.replyStop)
        assertEquals(1, evaluation.trace.size)
    }

    @Test
    fun `label from an earlier step is visible to later category checks`() {
        val pipeline = Pipeline(
            steps = listOf(
                step("allow", listOf(CheckSpec(CheckType.ALLOWED_SENDER)), listOf(ActionSpec(ActionType.LABEL, MessageCategory.ALLOWED))),
                step("block junk", listOf(CheckSpec(CheckType.CATEGORY_IS, categories = listOf(MessageCategory.JUNK))), listOf(ActionSpec(ActionType.BLOCK)))
            )
        )

        val allowlisted = PipelineRunner.evaluate(pipeline, ctx(category = MessageCategory.JUNK, allowedSender = true))
        assertEquals(MessageCategory.ALLOWED, allowlisted.category)
        assertFalse(allowlisted.blocked)

        val notAllowlisted = PipelineRunner.evaluate(pipeline, ctx(category = MessageCategory.JUNK))
        assertTrue(notAllowlisted.blocked)
    }

    @Test
    fun `political step replies regardless of the spam verdict`() {
        val pipeline = Pipeline(
            steps = listOf(
                political(listOf("democrat", "republican")),
                step("blocker", listOf(CheckSpec(CheckType.SPAM_VERDICT)), listOf(ActionSpec(ActionType.BLOCK)))
            )
        )
        // The classifier called it spam (blocked = true) and it is political: the reply step runs first.
        val evaluation = PipelineRunner.evaluate(
            pipeline,
            ctx(body = "republican candidates need your vote. Reply STOP to end.", blocked = true, directive = true)
        )
        assertTrue(evaluation.replyStop)
        assertTrue(evaluation.blocked)
        assertEquals("Political STOP", evaluation.repliedBy)
    }

    @Test
    fun `only matching messages reply`() {
        val pipeline = Pipeline(
            steps = listOf(
                political(listOf("democrat", "republican")),
                step("blocker", listOf(CheckSpec(CheckType.SPAM_VERDICT)), listOf(ActionSpec(ActionType.BLOCK)))
            )
        )

        val politicalMessage = PipelineRunner.evaluate(pipeline, ctx(body = "The Democratic Party needs your help"))
        // "democratic" is not the whole word "democrat"; the rule is whole-word by design.
        assertFalse(politicalMessage.replyStop)

        val exact = PipelineRunner.evaluate(pipeline, ctx(body = "democrat convention this weekend"))
        assertTrue(exact.replyStop)

        val unrelated = PipelineRunner.evaluate(pipeline, ctx(body = "Your parcel arrives today"))
        assertFalse(unrelated.replyStop)
        assertFalse(unrelated.blocked)
        assertEquals(2, unrelated.trace.size)
    }

    @Test
    fun `reply is refused for contacts, OTPs and allow-listed senders`() {
        val rules = listOf(AllowRule("senate"))
        val pipeline = Pipeline(steps = listOf(political(listOf("senate"))))

        assertFalse(PipelineRunner.evaluate(pipeline, ctx(body = "senate race", contact = true)).replyStop)
        assertFalse(PipelineRunner.evaluate(pipeline, ctx(body = "senate race", otp = true)).replyStop)
        assertFalse(PipelineRunner.evaluate(pipeline, ctx(body = "senate race", allowedSender = true)).replyStop)
        assertTrue(PipelineRunner.evaluate(pipeline, ctx(body = "senate race")).replyStop)
        assertTrue(rules.isNotEmpty())
    }

    @Test
    fun `trace records each check and action for the detail view`() {
        val pipeline = Pipeline(
            steps = listOf(
                step(
                    "detail",
                    checks = listOf(
                        CheckSpec(CheckType.ALWAYS),
                        CheckSpec(CheckType.IS_CONTACT, expected = false)
                    ),
                    actions = listOf(
                        ActionSpec(ActionType.LABEL, MessageCategory.JUNK),
                        ActionSpec(ActionType.PASS)
                    )
                )
            )
        )

        val trace = PipelineRunner.evaluate(pipeline, ctx(contact = false)).trace.single()

        assertTrue(trace.matched)
        assertEquals(2, trace.checks.size)
        assertTrue(trace.checks.all { it.second })
        assertEquals(listOf("label:JUNK", "pass"), trace.actions)
    }

    @Test
    fun `ANY mode matches when one check passes`() {
        val pipeline = Pipeline(
            steps = listOf(
                step(
                    "any",
                    checks = listOf(
                        CheckSpec(CheckType.IS_CONTACT),
                        CheckSpec(CheckType.TEXT_MATCHES, rules = listOf(AllowRule("gop")))
                    ),
                    actions = listOf(ActionSpec(ActionType.MUTE)),
                    mode = MatchMode.ANY
                )
            )
        )
        assertTrue(PipelineRunner.evaluate(pipeline, ctx(body = "gop rally")).muted)
        assertFalse(PipelineRunner.evaluate(pipeline, ctx(body = "nothing here")).muted)
    }

    @Test
    fun `disabled steps are skipped`() {
        val pipeline = Pipeline(
            steps = listOf(
                step("off", listOf(CheckSpec(CheckType.ALWAYS)), listOf(ActionSpec(ActionType.BLOCK)), enabled = false)
            )
        )
        assertFalse(PipelineRunner.evaluate(pipeline, ctx()).blocked)
        assertTrue(PipelineRunner.evaluate(pipeline, ctx()).trace.isEmpty())
    }

    @Test
    fun `default pipeline blocks junk but not allow-listed senders`() {
        val pipeline = PipelineDefaults.default()

        assertTrue(PipelineRunner.evaluate(pipeline, ctx(category = MessageCategory.JUNK, blocked = true)).blocked)
        assertFalse(
            PipelineRunner.evaluate(
                pipeline,
                ctx(category = MessageCategory.JUNK, blocked = true, allowedContent = true)
            ).blocked
        )
    }

    @Test
    fun `legacy stop2end step lands before the blocker`() {
        val pipeline = PipelineDefaults.default(legacyStop2End = true)
        val replyIndex = pipeline.steps.indexOfFirst { s -> s.actions.any { it.type == ActionType.REPLY_STOP } }
        val blockIndex = pipeline.steps.indexOfFirst { s -> s.actions.any { it.type == ActionType.BLOCK } }
        assertTrue(replyIndex in 0 until blockIndex)
    }

    @Test
    fun `new steps are inserted above the blocker`() {
        val pipeline = PipelineDefaults.default()
        val index = PipelineDefaults.insertionIndex(pipeline)
        assertEquals(pipeline.steps.lastIndex, index)

        val withBlockEarly = Pipeline(
            steps = listOf(step("block", actions = listOf(ActionSpec(ActionType.BLOCK))), step("tail"))
        )
        assertEquals(0, PipelineDefaults.insertionIndex(withBlockEarly))
    }
}
