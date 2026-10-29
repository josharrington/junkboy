package com.ovehbe.junkboy.pipeline

import com.ovehbe.junkboy.filters.AllowRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestedRulesTest {

    @Test
    fun `every suggestion is a parseable step with an action`() {
        assertTrue(SuggestedRules.all.isNotEmpty())
        assertEquals(SuggestedRules.all.size, SuggestedRules.all.map { it.id }.toSet().size)
        assertEquals(SuggestedRules.all.size, SuggestedRules.all.map { it.name }.toSet().size)

        SuggestedRules.all.forEach { suggestion ->
            val step = suggestion.toStep()
            assertEquals(suggestion.name, step.name)
            assertTrue("${suggestion.id} has no checks", step.checks.isNotEmpty())
            assertTrue("${suggestion.id} has no actions", step.actions.isNotEmpty())
            assertTrue("${suggestion.id} yaml is empty", suggestion.yaml.isNotBlank())
        }
    }

    @Test
    fun `importStep accepts a full pipeline document`() {
        val step = PipelineYaml.importStep(
            """
            version: 1
            steps:
              - name: From document
                actions:
                  - block
            """.trimIndent()
        )
        assertEquals("From document", step.name)
        assertEquals(ActionType.BLOCK, step.actions.single().type)
    }

    @Test
    fun `importStep rejects a bare list item`() {
        val error = runCatching { PipelineYaml.importStep("- name: nope") }.exceptionOrNull()
        assertTrue(error is PipelineFormatException)
    }

    @Test
    fun `political suggestion adds the four expected replies`() {
        val block = Step(
            id = "block",
            name = "Block junk",
            checks = listOf(CheckSpec(CheckType.SPAM_VERDICT)),
            actions = listOf(ActionSpec(ActionType.BLOCK))
        )
        val base = Pipeline(steps = listOf(block))
        val withRule = Pipeline(steps = listOf(SuggestedRules.byId("political-stop")!!.toStep(), block))

        assertEquals(0, SampleMessages.run(base).count { it.evaluation.replyStop })
        assertEquals(4, SampleMessages.run(withRule).count { it.evaluation.replyStop })
    }

    @Test
    fun `scam suggestions block their fixture messages`() {
        fun blockedBy(id: String, sampleId: String): Boolean {
            val pipeline = Pipeline(steps = listOf(SuggestedRules.byId(id)!!.toStep()))
            return SampleMessages.run(pipeline).first { it.sample.id == sampleId }.evaluation.blocked
        }

        assertTrue(blockedBy("parcel-fee", "scam-parcel"))
        assertTrue(blockedBy("crypto-doubling", "scam-crypto"))
        assertTrue(blockedBy("lottery-prize", "scam-lottery"))
        assertFalse(blockedBy("parcel-fee", "personal-uncle"))
    }

    @Test
    fun `unsubscribe suggestion replies to the marketing sample but not to appointments`() {
        val pipeline = Pipeline(steps = listOf(SuggestedRules.byId("unsubscribe-marketing")!!.toStep()))
        val results = SampleMessages.run(pipeline)

        assertTrue(results.first { it.sample.id == "marketing-stop" }.evaluation.replyStop)
        assertFalse(results.first { it.sample.id == "appointment-doctor" }.evaluation.replyStop)
    }

    @Test
    fun `parcel regex uses a valid pattern`() {
        val rule = SuggestedRules.byId("parcel-fee")!!.toStep()
        val regexRules = rule.checks.flatMap { it.rules }.filter { it.isRegex }
        assertTrue(regexRules.isNotEmpty())
        regexRules.forEach {
            assertNull(
                "invalid regex ${it.pattern}",
                com.ovehbe.junkboy.filters.ContentAllowlist.validate(AllowRule(it.pattern, true))
            )
        }
    }
}
