package com.ovehbe.junkboy.pipeline

import com.ovehbe.junkboy.database.MessageCategory
import com.ovehbe.junkboy.filters.AllowRule

/**
 * A pipeline is an ordered list of steps. Every message starts at step one and falls through
 * each step in order. A matching step runs its actions; [ActionType.BLOCK] ends the pipeline.
 * Reaching the end without a block means the message is allowed.
 */
data class Pipeline(
    val version: Int = 1,
    val steps: List<Step> = emptyList()
)

data class Step(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val mode: MatchMode = MatchMode.ALL,
    val checks: List<CheckSpec> = emptyList(),
    val actions: List<ActionSpec> = emptyList()
)

enum class MatchMode { ALL, ANY }

enum class CheckType {
    /** Always passes. Handy for a step that only acts (label, notify, reply). */
    ALWAYS,
    /** The message body matches one of the rules (same matching as the content allowlist). */
    TEXT_MATCHES,
    /** The sender matches one of the rules. */
    SENDER_MATCHES,
    /** The message's current category is one of the selected ones. */
    CATEGORY_IS,
    /** The sender is in the device's contacts. */
    IS_CONTACT,
    /** An OTP/delivery code was detected in the message. */
    HAS_OTP,
    /** The user has texted this sender before. */
    HAS_OUTBOUND_HISTORY,
    /** The message contains an opt-out directive such as "Reply STOP". */
    HAS_OPT_OUT_DIRECTIVE,
    /** The classifier or keyword rules flagged the message as junk or promotion. */
    SPAM_VERDICT,
    /** The sender is on the user's allowed-sender list. */
    ALLOWED_SENDER,
    /** The body matches one of the user's content allow rules. */
    ALLOWED_CONTENT
}

data class CheckSpec(
    val type: CheckType = CheckType.ALWAYS,
    val rules: List<AllowRule> = emptyList(),
    val categories: List<MessageCategory> = emptyList(),
    /** Used by the boolean checks; false asks for the opposite ("is not a contact"). */
    val expected: Boolean = true
)

enum class ActionType {
    /** No-op. Makes an explicit "do nothing, keep going" step readable. */
    PASS,
    /** Set the message's category; later steps see the new value. */
    LABEL,
    /** Stop the pipeline and suppress the message. */
    BLOCK,
    /** Send "STOP" to the sender (guarded by the executor). */
    REPLY_STOP,
    /** Keep the message but do not post a notification. */
    MUTE,
    /** Delete the message from the system SMS database, if Junkboy is the default SMS app. */
    DELETE
}

data class ActionSpec(
    val type: ActionType = ActionType.PASS,
    val category: MessageCategory? = null
)

/** Everything a step may look at. Gathered before evaluation so the engine stays pure. */
data class MessageContext(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val category: MessageCategory,
    val isBlocked: Boolean,
    val isContact: Boolean,
    val hasOutboundHistory: Boolean,
    val hasOtp: Boolean,
    val hasOptOutDirective: Boolean,
    val allowedBySender: Boolean,
    val allowedByContent: Boolean
)

/** One step's outcome, kept for the dry-run/test trace. */
data class StepTrace(
    val stepId: String,
    val stepName: String,
    val matched: Boolean,
    val checks: List<Pair<String, Boolean>>,
    val actions: List<String>
)

data class Evaluation(
    val category: MessageCategory,
    val blocked: Boolean,
    val muted: Boolean,
    val delete: Boolean,
    val replyStop: Boolean,
    /** The step that blocked, if any. */
    val blockedBy: String?,
    /** The step that asked for a STOP reply, if any. */
    val repliedBy: String?,
    val trace: List<StepTrace>
)

/** A pure evaluator: message + pipeline in, decisions out. No I/O, no side effects. */
object PipelineRunner {

    fun evaluate(pipeline: Pipeline, context: MessageContext): Evaluation {
        var category = context.category
        var blocked = false
        var muted = false
        var delete = false
        var replyStop = false
        var blockedBy: String? = null
        var repliedBy: String? = null
        val trace = mutableListOf<StepTrace>()

        for (step in pipeline.steps) {
            if (!step.enabled) continue
            // Later steps must see categories set by earlier steps (that is how an allow
            // rule short-circuits the junk block without a special terminal action).
            val current = context.copy(category = category, isBlocked = context.isBlocked || blocked)
            val results = step.checks.map { it to passes(it, current) }
            val matched = when (step.mode) {
                MatchMode.ALL -> results.all { it.second }
                MatchMode.ANY -> results.any { it.second }
            }

            val applied = mutableListOf<String>()
            if (matched) {
                for (action in step.actions) {
                    when (action.type) {
                        ActionType.PASS -> applied += "pass"
                        ActionType.LABEL -> {
                            action.category?.let {
                                category = it
                                applied += "label:${it.name}"
                            }
                        }
                        ActionType.BLOCK -> {
                            blocked = true
                            blockedBy = step.name
                            applied += "block"
                        }
                        ActionType.REPLY_STOP -> {
                            if (!replyStop) repliedBy = step.name
                            replyStop = true
                            applied += "reply_stop"
                        }
                        ActionType.MUTE -> {
                            muted = true
                            applied += "mute"
                        }
                        ActionType.DELETE -> {
                            delete = true
                            applied += "delete"
                        }
                    }
                    if (blocked) break
                }
            }

            trace += StepTrace(
                stepId = step.id,
                stepName = step.name,
                matched = matched,
                checks = results.map { (check, pass) -> checkLabel(check) to pass },
                actions = applied
            )

            if (blocked) break
        }

        return Evaluation(
            category = category,
            blocked = blocked,
            muted = muted,
            delete = delete,
            replyStop = replyStop,
            blockedBy = blockedBy,
            repliedBy = repliedBy,
            trace = trace
        )
    }

    private fun passes(check: CheckSpec, context: MessageContext): Boolean = when (check.type) {
        CheckType.ALWAYS -> true
        CheckType.TEXT_MATCHES -> matches(check.rules, context.body)
        CheckType.SENDER_MATCHES -> matches(check.rules, context.sender)
        CheckType.CATEGORY_IS -> context.category in check.categories
        CheckType.IS_CONTACT -> context.isContact == check.expected
        CheckType.HAS_OTP -> context.hasOtp == check.expected
        CheckType.HAS_OUTBOUND_HISTORY -> context.hasOutboundHistory == check.expected
        CheckType.HAS_OPT_OUT_DIRECTIVE -> context.hasOptOutDirective == check.expected
        CheckType.SPAM_VERDICT -> context.isBlocked == check.expected
        CheckType.ALLOWED_SENDER -> context.allowedBySender == check.expected
        CheckType.ALLOWED_CONTENT -> context.allowedByContent == check.expected
    }

    private fun matches(rules: List<AllowRule>, text: String): Boolean {
        if (rules.isEmpty()) return false
        return com.ovehbe.junkboy.filters.ContentAllowlist.firstMatch(rules, text) != null
    }

    fun checkLabel(check: CheckSpec): String = when (check.type) {
        CheckType.ALWAYS -> "always"
        CheckType.TEXT_MATCHES -> "text matches ${check.rules.joinToString(", ") { it.pattern }}"
        CheckType.SENDER_MATCHES -> "sender matches ${check.rules.joinToString(", ") { it.pattern }}"
        CheckType.CATEGORY_IS -> "category is ${check.categories.joinToString(", ") { it.name }}"
        CheckType.IS_CONTACT -> "is contact = ${check.expected}"
        CheckType.HAS_OTP -> "has OTP = ${check.expected}"
        CheckType.HAS_OUTBOUND_HISTORY -> "has outbound history = ${check.expected}"
        CheckType.HAS_OPT_OUT_DIRECTIVE -> "has opt-out directive = ${check.expected}"
        CheckType.SPAM_VERDICT -> "spam verdict = ${check.expected}"
        CheckType.ALLOWED_SENDER -> "allowed sender = ${check.expected}"
        CheckType.ALLOWED_CONTENT -> "allowed content = ${check.expected}"
    }
}
