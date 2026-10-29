package com.ovehbe.junkboy.pipeline

/**
 * Turns a pipeline evaluation into the actions that are actually allowed in the current mode.
 * Testing mode performs none of them and only records the decision.
 */
data class ExecutionPlan(
    val block: Boolean,
    val notify: Boolean,
    val replyStop: Boolean,
    val mute: Boolean,
    val delete: Boolean
) {
    companion object {
        fun forMode(evaluation: Evaluation, enforcing: Boolean): ExecutionPlan =
            if (enforcing) {
                ExecutionPlan(
                    block = evaluation.blocked,
                    notify = !evaluation.blocked && !evaluation.muted,
                    replyStop = evaluation.replyStop,
                    mute = evaluation.muted,
                    delete = evaluation.delete
                )
            } else {
                ExecutionPlan(
                    block = false,
                    notify = true,
                    replyStop = false,
                    mute = false,
                    delete = false
                )
            }
    }
}

/** Human-readable record for testing mode, stored next to the message. */
fun Evaluation.decisionSummary(): String {
    val parts = mutableListOf<String>()
    parts += if (blocked) {
        "Would block as ${category.name.lowercase()} (${blockedBy ?: "a step"})"
    } else {
        "Would allow as ${category.name.lowercase()}"
    }
    if (replyStop) parts += "would reply STOP (${repliedBy ?: "a step"})"
    if (muted) parts += "would mute"
    if (delete) parts += "would delete"
    return parts.joinToString(" · ")
}
