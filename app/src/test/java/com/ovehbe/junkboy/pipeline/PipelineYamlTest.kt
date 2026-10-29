package com.ovehbe.junkboy.pipeline

import com.ovehbe.junkboy.database.MessageCategory
import com.ovehbe.junkboy.filters.AllowRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineYamlTest {

    @Test
    fun `round trips a realistic pipeline`() {
        val original = Pipeline(
            steps = listOf(
                Step(
                    id = "a",
                    name = "Allow list",
                    mode = MatchMode.ANY,
                    checks = listOf(
                        CheckSpec(CheckType.ALLOWED_SENDER),
                        CheckSpec(CheckType.ALLOWED_CONTENT)
                    ),
                    actions = listOf(ActionSpec(ActionType.LABEL, MessageCategory.ALLOWED))
                ),
                Step(
                    id = "b",
                    name = "Political STOP",
                    checks = listOf(
                        CheckSpec(CheckType.TEXT_MATCHES, rules = listOf(AllowRule("democrat"), AllowRule("republican"))),
                        CheckSpec(CheckType.IS_CONTACT, expected = false),
                        CheckSpec(CheckType.HAS_OTP, expected = false)
                    ),
                    actions = listOf(ActionSpec(ActionType.REPLY_STOP))
                ),
                Step(
                    id = "c",
                    name = "Block junk",
                    checks = listOf(CheckSpec(CheckType.SPAM_VERDICT)),
                    actions = listOf(ActionSpec(ActionType.BLOCK))
                )
            )
        )

        val yaml = PipelineYaml.export(original)
        val restored = PipelineYaml.import(yaml)

        assertEquals(3, restored.steps.size)
        assertEquals(original.steps[0].name, restored.steps[0].name)
        assertEquals(MatchMode.ANY, restored.steps[0].mode)
        assertEquals(MessageCategory.ALLOWED, restored.steps[0].actions[0].category)
        assertEquals(listOf("democrat", "republican"), restored.steps[1].checks[0].rules.map { it.pattern })
        assertFalse(restored.steps[1].checks[1].expected)
        assertEquals(ActionType.REPLY_STOP, restored.steps[1].actions[0].type)
        assertEquals(ActionType.BLOCK, restored.steps[2].actions[0].type)
    }

    @Test
    fun `imports the documented sample`() {
        val yaml = """
            version: 1
            steps:
              - name: Political STOP
                match: all
                checks:
                  - text_matches: [democrat, republican]
                  - is_contact: false
                  - has_otp: false
                actions:
                  - reply_stop

              - name: Block junk
                checks:
                  - spam_verdict
                actions:
                  - block
        """.trimIndent()

        val pipeline = PipelineYaml.import(yaml)
        assertEquals(2, pipeline.steps.size)
        assertEquals(MatchMode.ALL, pipeline.steps[0].mode)
        assertEquals(2, pipeline.steps[0].checks[0].rules.size)
        assertEquals(ActionType.REPLY_STOP, pipeline.steps[0].actions[0].type)
        assertEquals(ActionType.BLOCK, pipeline.steps[1].actions[0].type)
    }

    @Test
    fun `regex rules survive the round trip`() {
        val pipeline = Pipeline(
            steps = listOf(
                Step(
                    id = "x",
                    name = "Prefix",
                    checks = listOf(
                        CheckSpec(
                            CheckType.TEXT_MATCHES,
                            rules = listOf(AllowRule("democrat.*", isRegex = true), AllowRule("gop"))
                        )
                    ),
                    actions = listOf(ActionSpec(ActionType.MUTE))
                )
            )
        )
        val restored = PipelineYaml.import(PipelineYaml.export(pipeline))
        val rules = restored.steps[0].checks[0].rules
        assertTrue(rules[0].isRegex)
        assertEquals("democrat.*", rules[0].pattern)
        assertFalse(rules[1].isRegex)
    }

    @Test
    fun `categories and labels round trip`() {
        val pipeline = Pipeline(
            steps = listOf(
                Step(
                    id = "c",
                    name = "Cats",
                    checks = listOf(
                        CheckSpec(CheckType.CATEGORY_IS, categories = listOf(MessageCategory.JUNK, MessageCategory.PROMOTION))
                    ),
                    actions = listOf(ActionSpec(ActionType.LABEL, MessageCategory.TRANSACTION))
                )
            )
        )
        val restored = PipelineYaml.import(PipelineYaml.export(pipeline))
        assertEquals(listOf(MessageCategory.JUNK, MessageCategory.PROMOTION), restored.steps[0].checks[0].categories)
        assertEquals(MessageCategory.TRANSACTION, restored.steps[0].actions[0].category)
    }

    @Test
    fun `empty pipeline round trips`() {
        val restored = PipelineYaml.import(PipelineYaml.export(Pipeline(steps = emptyList())))
        assertTrue(restored.steps.isEmpty())
    }

    @Test
    fun `unknown check is rejected with a helpful message`() {
        val yaml = """
            steps:
              - name: Bad
                checks:
                  - contains_magic
                actions:
                  - block
        """.trimIndent()
        val error = runCatching { PipelineYaml.import(yaml) }.exceptionOrNull()
        assertTrue(error is PipelineFormatException)
        assertTrue(error!!.message!!.contains("contains_magic"))
    }

    @Test
    fun `label without a category is rejected`() {
        val yaml = """
            steps:
              - name: Bad
                actions:
                  - label
        """.trimIndent()
        val error = runCatching { PipelineYaml.import(yaml) }.exceptionOrNull()
        assertTrue(error is PipelineFormatException)
        assertTrue(error!!.message!!.contains("label"))
    }

    @Test
    fun `unknown version is rejected`() {
        val error = runCatching { PipelineYaml.import("version: 2\nsteps: []") }.exceptionOrNull()
        assertTrue(error is PipelineFormatException)
    }

    @Test
    fun `step without a name is rejected`() {
        val error = runCatching { PipelineYaml.import("steps:\n  - actions: [block]") }.exceptionOrNull()
        assertTrue(error is PipelineFormatException)
    }

    @Test
    fun `bare boolean check means true`() {
        val pipeline = PipelineYaml.import("steps:\n  - name: A\n    checks: [is_contact]\n    actions: [block]")
        assertTrue(pipeline.steps[0].checks[0].expected)
    }
}

// --- review fixes: strict keys and document markers ---

class PipelineYamlStrictnessTest {

    @Test
    fun `unknown step key is rejected with its name`() {
        val yaml = """
            version: 1
            steps:
              - name: Block junk
                action:
                  - block
        """.trimIndent()

        val error = assertThrows(PipelineFormatException::class.java) { PipelineYaml.import(yaml) }
        assertTrue(error.message!!.contains("action"))
    }

    @Test
    fun `unknown document key is rejected`() {
        val yaml = """
            version: 1
            stepz: []
            steps: []
        """.trimIndent()

        val error = assertThrows(PipelineFormatException::class.java) { PipelineYaml.import(yaml) }
        assertTrue(error.message!!.contains("stepz"))
    }

    @Test
    fun `a step template with a document marker still imports`() {
        val template = """
            ---
            name: Block junk
            checks:
              - spam_verdict
            actions:
              - block
        """.trimIndent()

        val step = PipelineYaml.importStep(template)
        assertEquals("Block junk", step.name)
        assertEquals(listOf(ActionType.BLOCK), step.actions.map { it.type })
    }
}
