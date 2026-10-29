@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ovehbe.junkboy.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ovehbe.junkboy.pipeline.Pipeline
import com.ovehbe.junkboy.pipeline.PipelineDefaults
import com.ovehbe.junkboy.pipeline.SampleMessages
import com.ovehbe.junkboy.pipeline.SuggestedRule
import com.ovehbe.junkboy.pipeline.SuggestedRules
import com.ovehbe.junkboy.ui.theme.DesignColors
import com.ovehbe.junkboy.ui.theme.DesignSpacing
import com.ovehbe.junkboy.utils.PreferencesManager

/**
 * Ready-made steps expressed as one-step YAML templates. Nothing is applied automatically:
 * each card shows its template, what it would do to the 20-message fixture, and an Add button
 * that opens a confirmation dialog.
 */
@Composable
fun SuggestedRulesScreen() {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    var pipeline by remember { mutableStateOf(preferencesManager.getPipeline()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<SuggestedRule?>(null) }

    pending?.let { rule ->
        AddSuggestedRuleDialog(
            rule = rule,
            onDismiss = { pending = null },
            onAdd = {
                val step = rule.toStep()
                val steps = pipeline.steps.toMutableList()
                steps.add(PipelineDefaults.insertionIndex(pipeline), step)
                val next = pipeline.copy(steps = steps)
                preferencesManager.setPipeline(next)
                pipeline = next
                pending = null
                expanded = null
            }
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
                "Suggested rules",
                style = MaterialTheme.typography.headlineSmall,
                color = DesignColors.Primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Each card is a one-step YAML template. Open the template, check what it matches in " +
                    "the 20-message lab, then approve it. Nothing is added until you tap Add step.",
                style = MaterialTheme.typography.bodySmall,
                color = DesignColors.Secondary,
                modifier = Modifier.padding(top = DesignSpacing.XS)
            )
        }

        items(SuggestedRules.all, key = { it.id }) { rule ->
            val added = pipeline.steps.any { it.name == rule.name }
            SuggestedRuleCard(
                rule = rule,
                added = added,
                expanded = expanded == rule.id,
                onToggleYaml = { expanded = if (expanded == rule.id) null else rule.id },
                onAdd = { pending = rule }
            )
        }

        item {
            Text(
                "Adding inserts the step above the first blocking step, so reply rules still run " +
                    "for messages the blocker would otherwise end. Existing messages are not re-evaluated.",
                style = MaterialTheme.typography.bodySmall,
                color = DesignColors.Secondary
            )
        }
    }
}

@Composable
private fun SuggestedRuleCard(
    rule: SuggestedRule,
    added: Boolean,
    expanded: Boolean,
    onToggleYaml: () -> Unit,
    onAdd: () -> Unit
) {
    val impact = remember(rule.id) { impactText(rule) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DesignColors.InputBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(DesignSpacing.SM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = DesignColors.Primary,
                    modifier = Modifier.weight(1f)
                )
                if (added) {
                    Text(
                        "in pipeline",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                rule.summary,
                style = MaterialTheme.typography.bodySmall,
                color = DesignColors.Secondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rule.tags.forEach { tag -> TagChip(tag) }
            }

            Text(
                impact,
                style = MaterialTheme.typography.bodySmall,
                color = DesignColors.Secondary,
                modifier = Modifier.padding(top = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleYaml) {
                    Text(if (expanded) "Hide YAML" else "View YAML")
                }
                Spacer(Modifier.width(DesignSpacing.SM))
                Button(enabled = !added, onClick = onAdd) {
                    Text(if (added) "Added" else "Add…")
                }
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .background(DesignColors.Background, RoundedCornerShape(6.dp))
                        .padding(DesignSpacing.SM)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        rule.yaml,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSuggestedRuleDialog(
    rule: SuggestedRule,
    onDismiss: () -> Unit,
    onAdd: () -> Unit
) {
    val impact = remember(rule.id) { impactText(rule) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${rule.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignSpacing.SM)) {
                Text(
                    "This inserts the step above the first blocking step in your pipeline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DesignColors.Secondary
                )
                Text(impact, style = MaterialTheme.typography.bodySmall)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .background(DesignColors.Background, RoundedCornerShape(6.dp))
                        .padding(DesignSpacing.SM)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        rule.yaml,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAdd) { Text("Add step") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TagChip(text: String) {
    Surface(color = DesignColors.Background, shape = MaterialTheme.shapes.small) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = DesignColors.Secondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Which of the fixture messages this step alone would act on. */
private fun impactText(rule: SuggestedRule): String {
    val step = rule.toStep()
    val hits = SampleMessages.run(Pipeline(version = 1, steps = listOf(step)))
        .filter { result ->
            result.evaluation.trace.any { trace -> trace.stepId == step.id && trace.matched }
        }

    return when {
        hits.isEmpty() -> "Acts on none of the 20 sample messages"
        hits.size == 1 -> "Acts on 1 of the 20 samples: ${hits.first().sample.title}"
        else -> "Acts on ${hits.size} of the 20 samples: " +
            hits.take(3).joinToString(", ") { it.sample.title } +
            if (hits.size > 3) "…" else ""
    }
}
