package com.ovehbe.junkboy.pipeline

import com.ovehbe.junkboy.filters.AllowRule

/**
 * Keeps a step's checks one-per-type. A step can match several words, senders or categories, but
 * those belong in one check: a second "Words in the message" row would silently mean "both must
 * match" under All checks, which is never what the editor intends.
 */
object StepChecks {

    /** Index of the first check of [type], or -1. */
    fun indexOf(checks: List<CheckSpec>, type: CheckType): Int =
        checks.indexOfFirst { it.type == type }

    /** Appends [rule] unless an equivalent rule is already present. */
    fun addRule(check: CheckSpec, rule: AllowRule): CheckSpec {
        val exists = check.rules.any {
            it.isRegex == rule.isRegex && it.pattern == rule.pattern
        }
        return if (exists) check else check.copy(rules = check.rules + rule)
    }

    /**
     * Collapses duplicate check types: rule lists are concatenated (deduplicated), category sets are
     * unioned, and further boolean/always checks of a type already present are dropped.
     */
    fun normalize(checks: List<CheckSpec>): List<CheckSpec> {
        val result = mutableListOf<CheckSpec>()
        val indexByType = mutableMapOf<CheckType, Int>()

        checks.forEach { check ->
            val existingIndex = indexByType[check.type]
            if (existingIndex == null) {
                indexByType[check.type] = result.size
                result.add(check)
            } else {
                val previous = result[existingIndex]
                result[existingIndex] = when (check.type) {
                    CheckType.TEXT_MATCHES, CheckType.SENDER_MATCHES ->
                        previous.copy(rules = (previous.rules + check.rules).distinct())
                    CheckType.CATEGORY_IS ->
                        previous.copy(categories = (previous.categories + check.categories).distinct())
                    else -> previous
                }
            }
        }

        return result
    }
}
