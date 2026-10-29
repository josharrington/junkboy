@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ovehbe.junkboy.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ovehbe.junkboy.database.AppDatabase
import com.ovehbe.junkboy.database.FilteredMessage
import com.ovehbe.junkboy.pipeline.DecisionTraceCodec
import com.ovehbe.junkboy.ui.theme.DesignColors
import com.ovehbe.junkboy.ui.theme.DesignSpacing
import kotlinx.coroutines.launch

/**
 * Test-mode records, grouped by the step that produced the decision. Nothing in this list was
 * blocked, answered or deleted; it is the review queue for deciding whether to enforce.
 */
@Composable
fun TestDecisionsScreen() {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val messages by remember { database.filteredMessageDao().getTestMessages() }
        .collectAsState(initial = emptyList())

    var filter by remember { mutableStateOf(0) } // 0 all, 1 block, 2 reply
    var selected by remember { mutableStateOf<FilteredMessage?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(messages, filter) {
        when (filter) {
            1 -> messages.filter { wouldBlock(it) }
            2 -> messages.filter { wouldReply(it) }
            else -> messages
        }
    }
    val groups = remember(filtered) { filtered.groupBy { it.decisionStep.ifBlank { "(no step recorded)" } } }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear test decisions?") },
            text = { Text("This deletes the ${messages.size} test-mode records and their traces. Real messages are untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { database.filteredMessageDao().deleteTestMessages() }
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }

    selected?.let { message ->
        DecisionTraceDialog(
            sender = message.sender,
            body = message.messageBody,
            category = message.category,
            isTest = true,
            isBlocked = false,
            summary = message.decisionSummary,
            traceJson = message.decisionTrace,
            onDismiss = { selected = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignColors.Background),
        contentPadding = PaddingValues(DesignSpacing.MD),
        verticalArrangement = Arrangement.spacedBy(DesignSpacing.SM)
    ) {
        item {
            Text(
                "Test decisions",
                style = MaterialTheme.typography.headlineSmall,
                color = DesignColors.Primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${messages.size} recorded · ${messages.count { wouldBlock(it) }} would block · " +
                    "${messages.count { wouldReply(it) }} would reply. Nothing here was performed.",
                style = MaterialTheme.typography.bodySmall,
                color = DesignColors.Secondary,
                modifier = Modifier.padding(top = DesignSpacing.XS)
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(DesignSpacing.SM)) {
                FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("All") })
                FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("Would reply") })
                FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("Would block") })
            }
        }

        if (messages.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = DesignColors.InputBackground)) {
                    Text(
                        "No test decisions yet. Switch the pipeline to Testing, receive a message, " +
                            "and its decision will appear here.",
                        modifier = Modifier.padding(DesignSpacing.MD),
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignColors.Secondary
                    )
                }
            }
        }

        groups.forEach { (step, items) ->
            item(key = "header-$step") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DesignSpacing.SM),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(step, style = MaterialTheme.typography.titleSmall, color = DesignColors.Primary)
                    Text("${items.size}", style = MaterialTheme.typography.labelMedium, color = DesignColors.Secondary)
                }
            }
            items(items, key = { it.id }) { message ->
                TestDecisionCard(message) { selected = message }
            }
        }

        if (messages.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DesignSpacing.SM)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear test decisions")
                }
            }
        }
    }
}

@Composable
private fun TestDecisionCard(message: FilteredMessage, onWhy: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DesignColors.InputBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(DesignSpacing.SM),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.sender,
                    style = MaterialTheme.typography.titleSmall,
                    color = DesignColors.Primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    message.category.name.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = DesignColors.Secondary
                )
            }
            Text(
                message.messageBody,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (wouldBlock(message)) DecisionChip("would block")
                if (wouldReply(message)) DecisionChip("would reply STOP")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onWhy) { Text("Why?") }
            }
        }
    }
}

@Composable
private fun DecisionChip(text: String) {
    Surface(color = DesignColors.Background, shape = RoundedCornerShape(12.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = DesignColors.Secondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun traceActions(message: FilteredMessage): Set<String> =
    DecisionTraceCodec.decode(message.decisionTrace).flatMap { it.actions }.toSet()

private fun wouldBlock(message: FilteredMessage): Boolean =
    "block" in traceActions(message) || message.decisionSummary.startsWith("Would block")

private fun wouldReply(message: FilteredMessage): Boolean =
    "reply_stop" in traceActions(message) || message.decisionSummary.contains("would reply STOP")
