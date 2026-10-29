package com.ovehbe.junkboy.pipeline

import com.google.gson.Gson

data class TraceCheck(val label: String = "", val passed: Boolean = false)

data class TraceStep(
    val name: String = "",
    val matched: Boolean = false,
    val checks: List<TraceCheck> = emptyList(),
    val actions: List<String> = emptyList()
)

/** Compact JSON storage for the per-message pipeline trace. */
object DecisionTraceCodec {
    private val gson = Gson()

    fun encode(trace: List<StepTrace>): String = gson.toJson(
        trace.map { step ->
            TraceStep(
                name = step.stepName,
                matched = step.matched,
                checks = step.checks.map { (label, passed) -> TraceCheck(label, passed) },
                actions = step.actions
            )
        }
    )

    fun decode(json: String): List<TraceStep> {
        if (json.isBlank()) return emptyList()
        return try {
            gson.fromJson(json, Array<TraceStep>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
