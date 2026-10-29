@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ovehbe.junkboy.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ovehbe.junkboy.classifier.SmsClassifier
import com.ovehbe.junkboy.filters.FilterResult
import com.ovehbe.junkboy.pipeline.Pipeline
import com.ovehbe.junkboy.pipeline.SampleMessages
import com.ovehbe.junkboy.pipeline.SampleResult
import com.ovehbe.junkboy.ui.theme.DesignColors
import com.ovehbe.junkboy.ui.theme.DesignSpacing
import com.ovehbe.junkboy.utils.OtpHelper
import com.ovehbe.junkboy.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Runs the fixed 20-message fixture through a pipeline and shows what would happen to each one.
 * Nothing is sent, stored or notified: this is the same pure evaluator the service uses.
 */
@Composable
fun PipelineLabScreen() {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val otpHelper = remember { OtpHelper(context) }
    val myPipeline = remember { preferencesManager.getPipeline() }
    var useReference by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<SampleResult?>(null) }
    var useModel by remember { mutableStateOf(false) }
    var modelLoading by remember { mutableStateOf(false) }
    var modelFailed by remember { mutableStateOf(false) }
    var modelVerdicts by remember { mutableStateOf<Map<String, FilterResult>?>(null) }
    val classifier = remember { SmsClassifier.getInstance() }

    LaunchedEffect(useModel) {
        if (!useModel) return@LaunchedEffect
        modelLoading = true
        modelFailed = false
        val verdicts = withContext(Dispatchers.Default) {
            if (!classifier.initialize(context)) null
            else SampleMessages.all.associate { it.id to classifier.classify(it.body) }
        }
        modelVerdicts = verdicts
        modelFailed = verdicts == null
        modelLoading = false
    }

    // Keep one instance: samplePipeline() mints new step ids, and the detail dialog matches
    // trace entries by id. Rebuilding it on recomposition would show every step as disabled.
    val pipeline = remember(useReference, myPipeline) {
        if (useReference) SampleMessages.samplePipeline() else myPipeline
    }
    val results = remember(pipeline, useModel, modelVerdicts) {
        SampleMessages.run(
            pipeline = pipeline,
            otpResolver = { sample -> otpHelper.testOtpDetection(sample.body) != null },
            verdictResolver = { sample ->
                val model = modelVerdicts?.get(sample.id)
                if (useModel && model != null) model else SampleMessages.verdict(sample)
            }
        )
    }

    val blocked = results.count { it.evaluation.blocked }
    val replies = results.count { it.evaluation.replyStop }
    val matched = results.count { it.matches }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignColors.Background),
        contentPadding = PaddingValues(DesignSpacing.MD),
        verticalArrangement = Arrangement.spacedBy(DesignSpacing.SM)
    ) {
        item {
            Text(
                "Sample messages",
                style = MaterialTheme.typography.headlineSmall,
                color = DesignColors.Primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "$blocked blocked, $replies would get a STOP reply, ${results.size - blocked} allowed. " +
                    if (useModel && !modelFailed) {
                        "Verdicts come from the bundled on-device classifier. Fixture expectations were " +
                            "written for the rule tier, so the score below compares the two."
                    } else if (useModel) {
                        "The classifier could not be initialized, so these are rule verdicts, not model " +
                            "verdicts. Fixture expectations were written for the rule tier."
                    } else {
                        "Verdicts come from the built-in keyword and regex rules, so results are " +
                            "deterministic and repeatable."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = DesignColors.Secondary,
                modifier = Modifier.padding(top = DesignSpacing.XS)
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(DesignSpacing.SM)) {
                FilterChip(
                    selected = useReference,
                    onClick = { useReference = true },
                    label = { Text("Reference pipeline") }
                )
                FilterChip(
                    selected = !useReference,
                    onClick = { useReference = false },
                    label = { Text("My pipeline") }
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(DesignSpacing.SM)) {
                FilterChip(
                    selected = !useModel,
                    onClick = { useModel = false },
                    label = { Text("Built-in rules") }
                )
                FilterChip(
                    selected = useModel,
                    onClick = { useModel = true },
                    label = { Text("On-device model") }
                )
            }
        }

        item {
            Text(
                "Tap a message for the check-by-check breakdown.",
                style = MaterialTheme.typography.bodySmall,
                color = DesignColors.Secondary
            )
        }

        if (modelLoading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(DesignSpacing.SM))
                    Text(
                        "Loading and running the classifier…",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignColors.Secondary
                    )
                }
            }
        }

        if (modelFailed) {
            item {
                Text(
                    "The on-device classifier could not be initialized; showing rule verdicts instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (useReference) {
            item {
                Text(
                    if (useModel && !modelFailed) {
                        "$matched of ${results.size} still match the rule-tier expectations"
                    } else {
                        "$matched of ${results.size} match the fixture's expectations"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (matched == results.size) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }

        items(results, key = { it.sample.id }) { result ->
            SampleCard(result, showExpectation = useReference) { selected = result }
        }

        item {
            Spacer(Modifier.height(DesignSpacing.SM))
            Text(
                "Reference pipeline: reply STOP to messages containing " +
                    SampleMessages.politicalWords.joinToString(", ") +
                    " from senders you have not texted, then block what the classifier calls junk. " +
                    "No SMS is sent here.",
                style = MaterialTheme.typography.bodySmall,
                color = DesignColors.Secondary
            )
        }
    }

    selected?.let { result ->
        SampleDetailDialog(
            result = result,
            pipeline = pipeline,
            showExpectation = useReference,
            onDismiss = { selected = null }
        )
    }
}

@Composable
private fun SampleCard(result: SampleResult, showExpectation: Boolean, onClick: () -> Unit) {
    val evaluation = result.evaluation
    Card(
        colors = CardDefaults.cardColors(containerColor = DesignColors.InputBackground),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(DesignSpacing.SM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    result.sample.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = DesignColors.Primary,
                    modifier = Modifier.weight(1f)
                )
                if (showExpectation) {
                    Icon(
                        if (result.matches) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = if (result.matches) "As expected" else "Unexpected",
                        tint = if (result.matches) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                result.sample.sender,
                style = MaterialTheme.typography.labelSmall,
                color = DesignColors.Secondary
            )
            Text(
                result.sample.body,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusChip(evaluation.category.name.lowercase())
                StatusChip(if (evaluation.blocked) "blocked" else "allowed")
                if (evaluation.replyStop) StatusChip("reply STOP")
                if (evaluation.muted) StatusChip("muted")
            }
            val trace = evaluation.trace.joinToString(" → ") { step ->
                if (step.matched) "${step.stepName}: match" else "${step.stepName}: skip"
            }
            if (trace.isNotEmpty()) {
                Text(
                    trace,
                    style = MaterialTheme.typography.labelSmall,
                    color = DesignColors.Secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(
        color = DesignColors.Background,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = DesignColors.Secondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SampleDetailDialog(
    result: SampleResult,
    pipeline: Pipeline,
    showExpectation: Boolean,
    onDismiss: () -> Unit
) {
    val evaluation = result.evaluation
    val context = result.context

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(result.sample.title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DesignSpacing.SM)
            ) {
                Text(
                    result.sample.sender,
                    style = MaterialTheme.typography.labelSmall,
                    color = DesignColors.Secondary
                )
                Text(result.sample.body, style = MaterialTheme.typography.bodyMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusChip(evaluation.category.name.lowercase())
                    StatusChip(if (evaluation.blocked) "blocked" else "allowed")
                    if (evaluation.replyStop) StatusChip("reply STOP")
                    if (evaluation.muted) StatusChip("muted")
                }

                Text(
                    "Verdict: ${result.verdict.category.name.lowercase()}, " +
                        "blocked ${result.verdict.isBlocked}, " +
                        String.format(Locale.US, "%.0f%% confidence", result.verdict.confidence * 100),
                    style = MaterialTheme.typography.bodySmall,
                    color = DesignColors.Secondary
                )

                Text(
                    "Facts: contact ${context.isContact}, OTP ${context.hasOtp}, " +
                        "texted before ${context.hasOutboundHistory}, opt-out ${context.hasOptOutDirective}, " +
                        "allow-listed ${context.allowedBySender || context.allowedByContent}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DesignColors.Secondary
                )

                if (showExpectation) {
                    Text(
                        if (result.matches) {
                            "Matches the fixture: blocked ${result.sample.expectBlocked}, " +
                                "reply ${result.sample.expectReply}"
                        } else {
                            "Differs from the fixture: expected blocked ${result.sample.expectBlocked}, " +
                                "reply ${result.sample.expectReply}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.matches) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    "Outcome: ${outcomeText(result)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Divider()

                Text("Steps", style = MaterialTheme.typography.titleSmall)

                pipeline.steps.forEachIndexed { index, step ->
                    val trace = evaluation.trace.firstOrNull { it.stepId == step.id }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}. ${step.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                when {
                                    trace == null -> "disabled"
                                    trace.matched -> "match"
                                    else -> "skip"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    trace == null -> DesignColors.Secondary
                                    trace.matched -> MaterialTheme.colorScheme.primary
                                    else -> DesignColors.Secondary
                                }
                            )
                        }
                        Text(
                            if (step.mode == com.ovehbe.junkboy.pipeline.MatchMode.ALL) "all checks" else "any check",
                            style = MaterialTheme.typography.labelSmall,
                            color = DesignColors.Secondary
                        )
                        if (trace != null) {
                            trace.checks.forEach { (label, pass) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (pass) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (pass) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text(
                                "→ " + trace.actions.joinToString(", ").ifEmpty { "no actions" },
                                style = MaterialTheme.typography.bodySmall,
                                color = DesignColors.Secondary
                            )
                        }
                    }
                    if (index < pipeline.steps.lastIndex) Divider()
                }

            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun outcomeText(result: SampleResult): String {
    val e = result.evaluation
    val parts = mutableListOf<String>()
    if (e.blocked) parts += "Blocked by ${e.blockedBy ?: "a step"}" else parts += "Allowed"
    if (e.replyStop) parts += "STOP reply from ${e.repliedBy ?: "a step"}"
    if (e.muted) parts += "notification muted"
    if (e.delete) parts += "message deleted"
    return parts.joinToString(" · ")
}
