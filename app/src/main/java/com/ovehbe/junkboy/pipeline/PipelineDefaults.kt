package com.ovehbe.junkboy.pipeline

import com.ovehbe.junkboy.database.MessageCategory
import java.util.UUID

/** Ships with the app and is used after a reset. */
object PipelineDefaults {

    fun id(): String = UUID.randomUUID().toString()

    fun default(legacyStop2End: Boolean = false): Pipeline {
        val steps = mutableListOf(
            Step(
                id = id(),
                name = "Allow list",
                mode = MatchMode.ANY,
                checks = listOf(
                    CheckSpec(type = CheckType.ALLOWED_SENDER),
                    CheckSpec(type = CheckType.ALLOWED_CONTENT)
                ),
                actions = listOf(ActionSpec(ActionType.LABEL, MessageCategory.ALLOWED))
            )
        )

        if (legacyStop2End) {
            steps += Step(
                id = id(),
                name = "Stop2End auto-reply",
                mode = MatchMode.ALL,
                checks = listOf(
                    CheckSpec(type = CheckType.HAS_OPT_OUT_DIRECTIVE),
                    CheckSpec(type = CheckType.SPAM_VERDICT),
                    CheckSpec(type = CheckType.IS_CONTACT, expected = false),
                    CheckSpec(type = CheckType.HAS_OTP, expected = false),
                    CheckSpec(type = CheckType.HAS_OUTBOUND_HISTORY, expected = false),
                    CheckSpec(type = CheckType.ALLOWED_SENDER, expected = false),
                    CheckSpec(type = CheckType.ALLOWED_CONTENT, expected = false)
                ),
                actions = listOf(ActionSpec(ActionType.REPLY_STOP))
            )
        }

        steps += Step(
            id = id(),
            name = "Block junk",
            mode = MatchMode.ALL,
            checks = listOf(
                CheckSpec(type = CheckType.SPAM_VERDICT),
                CheckSpec(type = CheckType.ALLOWED_SENDER, expected = false),
                CheckSpec(type = CheckType.ALLOWED_CONTENT, expected = false)
            ),
            actions = listOf(ActionSpec(ActionType.BLOCK))
        )

        return Pipeline(version = 1, steps = steps)
    }

    /** A ready-made step for the "only reply to these words" case. Inserted before the blocker. */
    fun politicalReplyStep(rules: List<com.ovehbe.junkboy.filters.AllowRule>): Step = Step(
        id = id(),
        name = "Political STOP",
        mode = MatchMode.ALL,
        checks = listOf(
            CheckSpec(type = CheckType.TEXT_MATCHES, rules = rules),
            CheckSpec(type = CheckType.IS_CONTACT, expected = false),
            CheckSpec(type = CheckType.HAS_OTP, expected = false),
            CheckSpec(type = CheckType.ALLOWED_SENDER, expected = false),
            CheckSpec(type = CheckType.ALLOWED_CONTENT, expected = false)
        ),
        actions = listOf(ActionSpec(ActionType.REPLY_STOP))
    )

    /** New steps go before the first step that can block, or at the end. */
    fun insertionIndex(pipeline: Pipeline): Int {
        val index = pipeline.steps.indexOfFirst { step ->
            step.enabled && step.actions.any { it.type == ActionType.BLOCK }
        }
        return if (index >= 0) index else pipeline.steps.size
    }
}
