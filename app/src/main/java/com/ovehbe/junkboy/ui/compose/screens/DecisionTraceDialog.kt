package com.ovehbe.junkboy.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ovehbe.junkboy.database.MessageCategory
import com.ovehbe.junkboy.pipeline.DecisionTraceCodec
import com.ovehbe.junkboy.ui.theme.DesignColors
import com.ovehbe.junkboy.ui.theme.DesignSpacing

/** The per-message version of the sample trace: why this SMS was blocked, muted or answered. */
@Composable
fun DecisionTraceDialog(
    sender: String,
    body: String,
    category: MessageCategory,
    isTest: Boolean,
    isBlocked: Boolean,
    summary: String,
    traceJson: String,
    contextNote: String? = null,
    onDismiss: () -> Unit
) {
    val steps = remember(traceJson) { DecisionTraceCodec.decode(traceJson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Why this was filtered") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DesignSpacing.SM)
            ) {
                Text(sender, style = MaterialTheme.typography.labelSmall, color = DesignColors.Secondary)
                Text(body, style = MaterialTheme.typography.bodyMedium)
                if (contextNote != null) {
                    Text(
                        contextNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TraceChip(category.name.lowercase())
                    TraceChip(
                        when {
                            isTest -> "test"
                            isBlocked -> "blocked"
                            else -> "allowed"
                        }
                    )
                }

                if (summary.isNotEmpty()) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                Divider()
                Text("Steps", style = MaterialTheme.typography.titleSmall)

                if (steps.isEmpty()) {
                    Text(
                        "No step trace was stored for this message. Messages received before this " +
                            "version do not have one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignColors.Secondary
                    )
                } else {
                    steps.forEachIndexed { index, step ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${index + 1}. ${step.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (step.matched) "match" else "skip",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (step.matched) MaterialTheme.colorScheme.primary
                                    else DesignColors.Secondary
                                )
                            }
                            step.checks.forEach { check ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (check.passed) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (check.passed) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(check.label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (step.actions.isNotEmpty()) {
                                Text(
                                    "→ " + step.actions.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DesignColors.Secondary
                                )
                            }
                        }
                        if (index < steps.lastIndex) Divider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun TraceChip(text: String) {
    Surface(color = DesignColors.Background, shape = RoundedCornerShape(12.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = DesignColors.Secondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
