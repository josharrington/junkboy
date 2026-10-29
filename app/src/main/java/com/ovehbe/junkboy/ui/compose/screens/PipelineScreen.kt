@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ovehbe.junkboy.ui.compose.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ovehbe.junkboy.database.AppDatabase
import com.ovehbe.junkboy.database.MessageCategory
import com.ovehbe.junkboy.filters.AllowRule
import com.ovehbe.junkboy.filters.ContentAllowlist
import com.ovehbe.junkboy.pipeline.*
import com.ovehbe.junkboy.ui.theme.DesignColors
import com.ovehbe.junkboy.ui.theme.DesignSpacing
import com.ovehbe.junkboy.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ordered pipeline editor. A message enters at the top, each enabled step that matches runs its
 * actions, and the first Block ends the chain. Reaching the bottom means the message is allowed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineScreen(onOpenLab: () -> Unit = {}, onOpenSuggested: () -> Unit = {}) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    var pipeline by remember { mutableStateOf(preferencesManager.getPipeline()) }
    var editing by remember { mutableStateOf<Step?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var showPoliticalDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var enforcing by remember { mutableStateOf(preferencesManager.isEnforcementEnabled()) }
    var showExport by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }
                    if (text != null) {
                        importText = text
                        importError = null
                    } else {
                        importError = "Could not read that file"
                    }
                } catch (e: Exception) {
                    importError = "Could not read that file: ${e.message}"
                }
            }
        }
    }
    val database = remember { AppDatabase.getDatabase(context) }
    var contactsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> contactsGranted = granted }
    val replies by remember { database.autoReplyLogDao().getRecent() }.collectAsState(initial = emptyList())
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh after returning from the suggestions screen (or any other editor).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                pipeline = preferencesManager.getPipeline()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun save(next: Pipeline) {
        pipeline = next
        preferencesManager.setPipeline(next)
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in pipeline.steps.indices) return
        val steps = pipeline.steps.toMutableList()
        val item = steps.removeAt(index)
        steps.add(target, item)
        save(pipeline.copy(steps = steps))
    }

    if (editing != null) {
        StepEditorDialog(
            initial = editing!!,
            isNew = editingIsNew,
            onDismiss = { editing = null },
            onSave = { step ->
                val steps = pipeline.steps.toMutableList()
                if (editingIsNew) {
                    steps.add(PipelineDefaults.insertionIndex(pipeline), step)
                } else {
                    val index = steps.indexOfFirst { it.id == step.id }
                    if (index >= 0) steps[index] = step else steps.add(step)
                }
                save(pipeline.copy(steps = steps))
                editing = null
            }
        )
    }

    if (showPoliticalDialog) {
        PoliticalRuleDialog(
            onDismiss = { showPoliticalDialog = false },
            onAdd = { rules ->
                val step = PipelineDefaults.politicalReplyStep(rules)
                val steps = pipeline.steps.toMutableList()
                steps.add(PipelineDefaults.insertionIndex(pipeline), step)
                save(pipeline.copy(steps = steps))
                showPoliticalDialog = false
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset pipeline?") },
            text = { Text("Discard your steps and go back to the default: allow list, then block junk.") },
            confirmButton = {
                TextButton(onClick = {
                    preferencesManager.resetPipeline()
                    pipeline = preferencesManager.getPipeline()
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("Export pipeline") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(DesignSpacing.SM)) {
                    Text(
                        "Save this as a .yaml file, keep it in version control, or paste it into another install.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignColors.Secondary
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .background(DesignColors.Background, RoundedCornerShape(6.dp))
                            .padding(DesignSpacing.SM)
                    ) {
                        Text(
                            exportText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Junkboy pipeline", exportText))
                }) { Text("Copy") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        try {
                            val file = File(context.cacheDir, "junkboy-pipeline.yaml")
                            file.writeText(exportText)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/yaml"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Junkboy pipeline")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Share pipeline"))
                        } catch (e: Exception) {
                            // Fall back to copying if there is no share target
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Junkboy pipeline", exportText))
                        }
                        showExport = false
                    }) { Text("Share") }
                    TextButton(onClick = { showExport = false }) { Text("Close") }
                }
            }
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import pipeline") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(DesignSpacing.SM)) {
                    Text(
                        "Paste YAML, or open a .yaml file. Importing replaces the current pipeline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignColors.Secondary
                    )
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it; importError = null },
                        label = { Text("YAML") },
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                    importError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { importFileLauncher.launch(arrayOf("*/*")) }) {
                        Text("Open file")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importText.isNotBlank(),
                    onClick = {
                        try {
                            val imported = PipelineYaml.import(importText)
                            save(imported)
                            showImport = false
                            importText = ""
                        } catch (e: PipelineFormatException) {
                            importError = e.message
                        } catch (e: Exception) {
                            importError = "Import failed: ${e.message}"
                        }
                    }
                ) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } }
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
                "Pipeline",
                style = MaterialTheme.typography.headlineSmall,
                color = DesignColors.Primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Messages run through these steps in order. A step that matches runs its actions; " +
                    "Block ends the chain and suppresses the message. Reaching the bottom means allow. " +
                    "Step labels apply to later steps, so an allow rule keeps a message out of the blocker.",
                style = MaterialTheme.typography.bodySmall,
                color = DesignColors.Secondary,
                modifier = Modifier.padding(top = DesignSpacing.XS, bottom = DesignSpacing.SM)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = DesignSpacing.SM),
                colors = CardDefaults.cardColors(containerColor = DesignColors.InputBackground)
            ) {
                Column(Modifier.padding(DesignSpacing.SM)) {
                    Text(
                        "Mode",
                        style = MaterialTheme.typography.titleSmall,
                        color = DesignColors.Primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(DesignSpacing.SM)) {
                        FilterChip(
                            selected = enforcing,
                            onClick = {
                                enforcing = true
                                preferencesManager.setEnforcementEnabled(true)
                            },
                            label = { Text("Enforcing") }
                        )
                        FilterChip(
                            selected = !enforcing,
                            onClick = {
                                enforcing = false
                                preferencesManager.setEnforcementEnabled(false)
                            },
                            label = { Text("Testing") }
                        )
                    }
                    Text(
                        if (enforcing) {
                            "Pipeline actions are performed: blocks suppress messages, STOP replies are sent, " +
                                "mutes and deletes apply."
                        } else {
                            "Nothing is performed. Every message is delivered and notified, and the decision " +
                                "is recorded in the message list so you can tune the steps first."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enforcing) DesignColors.Secondary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = DesignSpacing.XS)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(DesignSpacing.SM)) {
                OutlinedButton(onClick = {
                    exportText = PipelineYaml.export(pipeline)
                    showExport = true
                }) { Text("Export YAML") }
                OutlinedButton(onClick = {
                    importText = ""
                    importError = null
                    showImport = true
                }) { Text("Import YAML") }
            }
            Row(
                modifier = Modifier.padding(top = DesignSpacing.SM),
                horizontalArrangement = Arrangement.spacedBy(DesignSpacing.SM)
            ) {
                OutlinedButton(onClick = onOpenLab) { Text("Run 20 sample messages") }
                OutlinedButton(onClick = onOpenSuggested) { Text("Suggested rules") }
            }
        }

        if (!contactsGranted) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = DesignColors.InputBackground)) {
                    Column(Modifier.padding(DesignSpacing.MD)) {
                        Text(
                            "Contacts permission is off",
                            style = MaterialTheme.typography.titleSmall,
                            color = DesignColors.Primary
                        )
                        Text(
                            "Without it the app cannot tell whether a sender is someone you know, so it " +
                                "refuses every STOP reply. Grant it to let replies through." ,
                            style = MaterialTheme.typography.bodySmall,
                            color = DesignColors.Secondary,
                            modifier = Modifier.padding(vertical = DesignSpacing.XS)
                        )
                        OutlinedButton(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                            Text("Grant contacts access")
                        }
                    }
                }
            }
        }

        if (pipeline.steps.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = DesignColors.InputBackground)) {
                    Text(
                        "No steps. Every message is allowed.",
                        modifier = Modifier.padding(DesignSpacing.MD),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DesignColors.Secondary
                    )
                }
            }
        }

        itemsIndexed(pipeline.steps, key = { _, step -> step.id }) { index, step ->
            StepCard(
                index = index,
                step = step,
                canMoveUp = index > 0,
                canMoveDown = index < pipeline.steps.lastIndex,
                onToggle = { enabled ->
                    val steps = pipeline.steps.toMutableList()
                    steps[index] = step.copy(enabled = enabled)
                    save(pipeline.copy(steps = steps))
                },
                onEdit = { editing = step; editingIsNew = false },
                onDelete = {
                    save(pipeline.copy(steps = pipeline.steps.filterNot { it.id == step.id }))
                },
                onMoveUp = { move(index, -1) },
                onMoveDown = { move(index, 1) }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DesignSpacing.SM)
            ) {
                OutlinedButton(
                    onClick = {
                        editing = Step(
                            id = PipelineDefaults.id(),
                            name = "New step",
                            mode = MatchMode.ALL,
                            checks = listOf(CheckSpec(CheckType.ALWAYS)),
                            actions = listOf(ActionSpec(ActionType.PASS))
                        )
                        editingIsNew = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add step")
                }
                Button(
                    onClick = { showPoliticalDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Political STOP")
                }
            }
            TextButton(onClick = { showResetDialog = true }) { Text("Reset to defaults") }
        }

        item {
            Spacer(Modifier.height(DesignSpacing.SM))
            Text(
                "Sending STOP costs an SMS on your plan. Contacts, OTP messages and allow-listed " +
                    "senders are never replied to, and each number gets at most one reply per 24 hours.",
                style = MaterialTheme.typography.bodySmall,
                color = DesignColors.Secondary
            )
        }

        item {
            Spacer(Modifier.height(DesignSpacing.MD))
            Text(
                "Recent auto-replies",
                style = MaterialTheme.typography.titleSmall,
                color = DesignColors.Primary
            )
        }

        if (replies.isEmpty()) {
            item {
                Text(
                    "No auto-replies sent yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = DesignColors.Secondary
                )
            }
        } else {
            items(replies.take(20)) { entry ->
                Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(entry.senderNumber, style = MaterialTheme.typography.bodyMedium, color = DesignColors.Primary)
                    Text(
                        entry.reason.ifEmpty { entry.replyText },
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignColors.Secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: Step,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DesignColors.InputBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(DesignSpacing.SM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}. ${step.name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = DesignColors.Primary,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = step.enabled, onCheckedChange = onToggle)
            }
            Text(
                describeStep(step),
                style = MaterialTheme.typography.bodySmall,
                color = DesignColors.Secondary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun describeStep(step: Step): String {
    val mode = if (step.mode == MatchMode.ALL) "all of" else "any of"
    val checks = if (step.checks.isEmpty()) "always"
    else "$mode: " + step.checks.joinToString("; ") { PipelineRunner.checkLabel(it) }
    val actions = step.actions.joinToString(", ") { action ->
        when (action.type) {
            ActionType.PASS -> "pass"
            ActionType.LABEL -> "label ${action.category?.name ?: "?"}"
            ActionType.BLOCK -> "block"
            ActionType.REPLY_STOP -> "reply STOP"
            ActionType.MUTE -> "mute"
            ActionType.DELETE -> "delete"
        }
    }
    return "$checks → $actions"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepEditorDialog(
    initial: Step,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Step) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var mode by remember { mutableStateOf(initial.mode) }
    var checks by remember { mutableStateOf(initial.checks) }
    var actions by remember { mutableStateOf(initial.actions) }
    var addCheckMenu by remember { mutableStateOf(false) }
    var addActionMenu by remember { mutableStateOf(false) }
    var ruleTarget by remember { mutableStateOf<Pair<Int, CheckType>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New step" else "Edit step") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DesignSpacing.SM)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Match", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(DesignSpacing.SM)) {
                    FilterChip(
                        selected = mode == MatchMode.ALL,
                        onClick = { mode = MatchMode.ALL },
                        label = { Text("All checks") }
                    )
                    FilterChip(
                        selected = mode == MatchMode.ANY,
                        onClick = { mode = MatchMode.ANY },
                        label = { Text("Any check") }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Checks", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { addCheckMenu = true }) { Text("+ Add") }
                        DropdownMenu(expanded = addCheckMenu, onDismissRequest = { addCheckMenu = false }) {
                            CheckType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(checkTypeLabel(type)) },
                                    onClick = {
                                        addCheckMenu = false
                                        if (type == CheckType.TEXT_MATCHES || type == CheckType.SENDER_MATCHES) {
                                            ruleTarget = checks.size to type
                                            checks = checks + CheckSpec(type = type)
                                        } else {
                                            checks = checks + CheckSpec(type = type)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                checks.forEachIndexed { index, check ->
                    CheckRow(
                        check = check,
                        onChange = { updated ->
                            checks = checks.toMutableList().also { it[index] = updated }
                        },
                        onRemove = { checks = checks.filterIndexed { i, _ -> i != index } }
                    )
                }

                if (checks.isEmpty()) {
                    Text(
                        "No checks: this step matches every message.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignColors.Secondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Actions", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { addActionMenu = true }) { Text("+ Add") }
                        DropdownMenu(expanded = addActionMenu, onDismissRequest = { addActionMenu = false }) {
                            ActionType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(actionTypeLabel(type)) },
                                    onClick = {
                                        addActionMenu = false
                                        actions = actions + ActionSpec(
                                            type = type,
                                            category = if (type == ActionType.LABEL) MessageCategory.JUNK else null
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                actions.forEachIndexed { index, action ->
                    ActionRow(
                        action = action,
                        onChange = { updated ->
                            actions = actions.toMutableList().also { it[index] = updated }
                        },
                        onRemove = { actions = actions.filterIndexed { i, _ -> i != index } }
                    )
                }

                if (actions.isEmpty()) {
                    Text(
                        "No actions: this step is a pure check and changes nothing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignColors.Secondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(initial.copy(name = name.trim(), mode = mode, checks = checks, actions = actions)) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    val target = ruleTarget
    if (target != null) {
        RuleDialog(
            title = if (target.second == CheckType.TEXT_MATCHES) "Words in the message" else "Senders",
            onDismiss = { ruleTarget = null },
            onAdd = { rule ->
                checks = checks.toMutableList().also { list ->
                    list[target.first] = list[target.first].copy(rules = list[target.first].rules + rule)
                }
                ruleTarget = null
            }
        )
    }
}

@Composable
private fun CheckRow(check: CheckSpec, onChange: (CheckSpec) -> Unit, onRemove: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DesignColors.Background, RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                checkTypeLabel(check.type),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
            }
        }

        when (check.type) {
            CheckType.TEXT_MATCHES, CheckType.SENDER_MATCHES -> {
                if (check.rules.isEmpty()) {
                    Text("No rules yet", style = MaterialTheme.typography.bodySmall, color = DesignColors.Secondary)
                } else {
                    check.rules.forEach { rule ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (rule.isRegex) "/${rule.pattern}/" else rule.pattern),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onChange(check.copy(rules = check.rules - rule)) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove rule", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            CheckType.CATEGORY_IS -> {
                val all = MessageCategory.entries
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    all.forEach { category ->
                        FilterChip(
                            selected = category in check.categories,
                            onClick = {
                                val next = if (category in check.categories) check.categories - category
                                else check.categories + category
                                onChange(check.copy(categories = next))
                            },
                            label = { Text(category.name.lowercase(), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            CheckType.IS_CONTACT, CheckType.HAS_OTP, CheckType.HAS_OUTBOUND_HISTORY,
            CheckType.HAS_OPT_OUT_DIRECTIVE, CheckType.SPAM_VERDICT,
            CheckType.ALLOWED_SENDER, CheckType.ALLOWED_CONTENT -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Expect", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(if (check.expected) "true" else "false", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = check.expected,
                        onCheckedChange = { onChange(check.copy(expected = it)) },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            CheckType.ALWAYS -> Unit
        }
    }
}

@Composable
private fun ActionRow(action: ActionSpec, onChange: (ActionSpec) -> Unit, onRemove: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DesignColors.Background, RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                actionTypeLabel(action.type),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
            }
        }
        if (action.type == ActionType.LABEL) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MessageCategory.entries.forEach { category ->
                    FilterChip(
                        selected = action.category == category,
                        onClick = { onChange(action.copy(category = category)) },
                        label = { Text(category.name.lowercase(), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleDialog(title: String, onDismiss: () -> Unit, onAdd: (AllowRule) -> Unit) {
    var text by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }
    val rule = AllowRule(text.trim(), isRegex)
    val error = if (text.isBlank()) null else ContentAllowlist.validate(rule)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignSpacing.SM)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (isRegex) "Regex" else "Word or phrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Regular expression", modifier = Modifier.weight(1f))
                    Switch(checked = isRegex, onCheckedChange = { isRegex = it })
                }
                if (isRegex) {
                    Text(
                        "Whole-message matching with Kotlin regex syntax.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignColors.Secondary
                    )
                } else {
                    Text(
                        "Whole words, case-insensitive. Add \"democratic\" separately if you want it too.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignColors.Secondary
                    )
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && error == null, onClick = { onAdd(rule) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Convenience flow for the common case: reply STOP to messages containing these words. */
@Composable
private fun PoliticalRuleDialog(onDismiss: () -> Unit, onAdd: (List<AllowRule>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val words = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val rules = words.map { AllowRule(it, isRegex = false) }
    val error = rules.firstNotNullOfOrNull { ContentAllowlist.validate(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Political STOP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignSpacing.SM)) {
                Text(
                    "Adds a step that replies STOP when the message contains any of these words and " +
                        "comes from someone you have not texted. The step goes above the blocker, so it " +
                        "still runs for messages the classifier calls spam.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DesignColors.Secondary
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Words, comma separated") },
                    placeholder = { Text("democrat, republican, gop, senate") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Whole words, case-insensitive. Use a normal step with a regex if you need " +
                        "prefixes such as democrat*.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DesignColors.Secondary
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = rules.isNotEmpty() && error == null, onClick = { onAdd(rules) }) { Text("Add step") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun checkTypeLabel(type: CheckType): String = when (type) {
    CheckType.ALWAYS -> "Always"
    CheckType.TEXT_MATCHES -> "Message contains"
    CheckType.SENDER_MATCHES -> "Sender matches"
    CheckType.CATEGORY_IS -> "Category is"
    CheckType.IS_CONTACT -> "Sender is a contact"
    CheckType.HAS_OTP -> "Contains an OTP"
    CheckType.HAS_OUTBOUND_HISTORY -> "You have texted them"
    CheckType.HAS_OPT_OUT_DIRECTIVE -> "Has \"Reply STOP\""
    CheckType.SPAM_VERDICT -> "Classifier says spam"
    CheckType.ALLOWED_SENDER -> "Sender is allow-listed"
    CheckType.ALLOWED_CONTENT -> "Matches content allow rule"
}

private fun actionTypeLabel(type: ActionType): String = when (type) {
    ActionType.PASS -> "Pass"
    ActionType.LABEL -> "Set category"
    ActionType.BLOCK -> "Block (ends pipeline)"
    ActionType.REPLY_STOP -> "Reply STOP"
    ActionType.MUTE -> "Mute notification"
    ActionType.DELETE -> "Delete message"
}
