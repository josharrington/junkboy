package com.ovehbe.junkboy.pipeline

import com.ovehbe.junkboy.database.MessageCategory
import com.ovehbe.junkboy.filters.AllowRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepChecksTest {

    @Test
    fun `adding a word appends it to the existing text check`() {
        val check = CheckSpec(type = CheckType.TEXT_MATCHES, rules = listOf(AllowRule("lottery")))

        val merged = StepChecks.addRule(check, AllowRule("prize"))

        assertEquals(listOf("lottery", "prize"), merged.rules.map { it.pattern })
    }

    @Test
    fun `the same word is not added twice`() {
        val check = CheckSpec(type = CheckType.TEXT_MATCHES, rules = listOf(AllowRule("lottery")))

        val merged = StepChecks.addRule(check, AllowRule("lottery"))

        assertEquals(1, merged.rules.size)
        assertTrue(merged == check)
    }

    @Test
    fun `keywords are deduplicated case-insensitively`() {
        val check = CheckSpec(type = CheckType.TEXT_MATCHES, rules = listOf(AllowRule("Lottery")))

        val merged = StepChecks.addRule(check, AllowRule("lottery"))

        assertEquals(listOf("Lottery"), merged.rules.map { it.pattern })
    }

    @Test
    fun `regexes are deduplicated case-insensitively`() {
        val check = CheckSpec(type = CheckType.TEXT_MATCHES, rules = listOf(AllowRule("(?i)free.*money", isRegex = true)))

        val merged = StepChecks.addRule(check, AllowRule("(?i)FREE.*MONEY", isRegex = true))

        assertEquals(1, merged.rules.size)
    }

    @Test
    fun `normalize deduplicates keywords case-insensitively`() {
        val checks = listOf(
            CheckSpec(type = CheckType.TEXT_MATCHES, rules = listOf(AllowRule("Lottery"))),
            CheckSpec(type = CheckType.TEXT_MATCHES, rules = listOf(AllowRule("lottery")))
        )

        assertEquals(listOf("Lottery"), StepChecks.normalize(checks)[0].rules.map { it.pattern })
    }

    @Test
    fun `a regex and a plain word with the same text are distinct`() {
        val check = CheckSpec(type = CheckType.TEXT_MATCHES, rules = listOf(AllowRule("win", isRegex = false)))

        val merged = StepChecks.addRule(check, AllowRule("win", isRegex = true))

        assertEquals(2, merged.rules.size)
    }

    @Test
    fun `normalize merges duplicate rule checks and keeps their order`() {
        val checks = listOf(
            CheckSpec(type = CheckType.TEXT_MATCHES, rules = listOf(AllowRule("b"))),
            CheckSpec(type = CheckType.IS_CONTACT, expected = false),
            CheckSpec(type = CheckType.TEXT_MATCHES, rules = listOf(AllowRule("a"), AllowRule("b"))),
            CheckSpec(type = CheckType.CATEGORY_IS, categories = listOf(MessageCategory.JUNK)),
            CheckSpec(type = CheckType.CATEGORY_IS, categories = listOf(MessageCategory.PROMOTION, MessageCategory.JUNK))
        )

        val normalized = StepChecks.normalize(checks)

        assertEquals(3, normalized.size)
        assertEquals(listOf("b", "a"), normalized[0].rules.map { it.pattern })
        assertEquals(CheckType.IS_CONTACT, normalized[1].type)
        assertEquals(listOf(MessageCategory.JUNK, MessageCategory.PROMOTION), normalized[2].categories)
    }

    @Test
    fun `normalize drops a duplicate boolean check`() {
        val checks = listOf(
            CheckSpec(type = CheckType.SPAM_VERDICT, expected = true),
            CheckSpec(type = CheckType.SPAM_VERDICT, expected = false)
        )

        val normalized = StepChecks.normalize(checks)

        assertEquals(1, normalized.size)
        assertTrue(normalized[0].expected)
    }
}
