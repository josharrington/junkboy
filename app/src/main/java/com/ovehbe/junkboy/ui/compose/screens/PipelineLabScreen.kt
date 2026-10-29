@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ovehbe.junkboy.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ovehbe.junkboy.pipeline.SampleMessages
import com.ovehbe.junkboy.pipeline.SampleResult
import com.ovehbe.junkboy.ui.theme.DesignColors
import com.ovehbe.junkboy.ui.theme.DesignSpacing
import com.ovehbe.junkboy.utils.OtpHelper
import com.ovehbe.junkboy.utils.PreferencesManager

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

    val pipeline = if (useReference) SampleMessages.samplePipeline() else myPipeline
    val results = remember(useReference) {
        SampleMessages.run(pipeline) { sample ->
            otpHelper.testOtpDetection(sample.body) != null
        }
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
                    "Classification uses the built-in keyword and regex rules (the on-device model is not " +
                    "part of this lab), so results are deterministic and repeatable.",
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

        if (useReference) {
            item {
                Text(
                    "$matched of ${results.size} match the fixture's expectations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (matched == results.size) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }

        items(results, key = { it.sample.id }) { result ->
            SampleCard(result, showExpectation = useReference)
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
}

@Composable
private fun SampleCard(result: SampleResult, showExpectation: Boolean) {
    val evaluation = result.evaluation
    Card(
        colors = CardDefaults.cardColors(containerColor = DesignColors.InputBackground),
        modifier = Modifier.fillMaxWidth()
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
