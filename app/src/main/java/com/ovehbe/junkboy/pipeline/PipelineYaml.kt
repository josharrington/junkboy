package com.ovehbe.junkboy.pipeline

import com.ovehbe.junkboy.database.MessageCategory
import com.ovehbe.junkboy.filters.AllowRule
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

class PipelineFormatException(message: String) : Exception(message)

/**
 * YAML is the interchange format; the app keeps JSON internally.
 *
 * ```yaml
 * version: 1
 * steps:
 *   - name: Allow list
 *     match: any
 *     checks:
 *       - allowed_sender
 *       - allowed_content
 *     actions:
 *       - label: allowed
 *
 *   - name: Political STOP
 *     checks:
 *       - text_matches: [democrat, republican]
 *       - is_contact: false
 *       - has_otp: false
 *     actions:
 *       - reply_stop
 *
 *   - name: Block junk
 *     checks:
 *       - spam_verdict
 *     actions:
 *       - block
 * ```
 *
 * Checks may be a bare name (`always`), a name with a boolean (`is_contact: false`),
 * a list (`text_matches: [a, b]`, `category_is: [junk]`), or a rule list that also
 * supports regexes (`text_matches: [{regex: "democrat.*"}]`).
 */
object PipelineYaml {

    private val checkNames = mapOf(
        "always" to CheckType.ALWAYS,
        "text_matches" to CheckType.TEXT_MATCHES,
        "sender_matches" to CheckType.SENDER_MATCHES,
        "category_is" to CheckType.CATEGORY_IS,
        "is_contact" to CheckType.IS_CONTACT,
        "has_otp" to CheckType.HAS_OTP,
        "has_outbound_history" to CheckType.HAS_OUTBOUND_HISTORY,
        "has_opt_out_directive" to CheckType.HAS_OPT_OUT_DIRECTIVE,
        "spam_verdict" to CheckType.SPAM_VERDICT,
        "allowed_sender" to CheckType.ALLOWED_SENDER,
        "allowed_content" to CheckType.ALLOWED_CONTENT
    )

    private val booleanChecks = setOf(
        CheckType.IS_CONTACT, CheckType.HAS_OTP, CheckType.HAS_OUTBOUND_HISTORY,
        CheckType.HAS_OPT_OUT_DIRECTIVE, CheckType.SPAM_VERDICT,
        CheckType.ALLOWED_SENDER, CheckType.ALLOWED_CONTENT
    )

    private val actionNames = mapOf(
        "pass" to ActionType.PASS,
        "label" to ActionType.LABEL,
        "block" to ActionType.BLOCK,
        "reply_stop" to ActionType.REPLY_STOP,
        "mute" to ActionType.MUTE,
        "delete" to ActionType.DELETE
    )

    fun export(pipeline: Pipeline): String {
        val root = linkedMapOf<String, Any>(
            "version" to pipeline.version,
            "steps" to pipeline.steps.map { step ->
                linkedMapOf<String, Any>(
                    "name" to step.name,
                    "enabled" to step.enabled,
                    "match" to if (step.mode == MatchMode.ALL) "all" else "any",
                    "checks" to step.checks.map { exportCheck(it) },
                    "actions" to step.actions.map { exportAction(it) }
                )
            }
        )
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
            indicatorIndent = 0
            isAllowUnicode = true
        }
        return Yaml(options).dump(root)
    }

    /**
     * Parses a single step template. Accepts either a step mapping (`name: ...`, as used by the
     * suggestions screen) or a complete pipeline document with a `steps` list.
     */
    fun importStep(text: String): Step {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw PipelineFormatException("The template is empty")
        val isDocument = trimmed.lineSequence().any { it.trimStart().startsWith("steps:") }
        val document = if (isDocument) trimmed else wrapStep(trimmed)
        return import(document).steps.firstOrNull()
            ?: throw PipelineFormatException("The template has no step")
    }

    private fun wrapStep(step: String): String {
        val lines = step.lines()
        val first = lines.firstOrNull { it.isNotBlank() }?.trimStart().orEmpty()
        if (first.startsWith("-")) {
            throw PipelineFormatException("A template must be a step mapping, not a list item")
        }
        val body = StringBuilder("version: 1\nsteps:\n")
        var firstWritten = false
        for (line in lines) {
            if (line.isBlank()) {
                body.append("\n")
                continue
            }
            if (!firstWritten) {
                body.append("  - ").append(line).append("\n")
                firstWritten = true
            } else {
                body.append("    ").append(line).append("\n")
            }
        }
        return body.toString()
    }

    fun import(text: String): Pipeline {
        val parsed = try {
            Yaml(SafeConstructor(LoaderOptions())).load<Any?>(text)
        } catch (e: Exception) {
            throw PipelineFormatException("Not valid YAML: ${e.message}")
        } ?: throw PipelineFormatException("The document is empty")

        val root = parsed as? Map<*, *>
            ?: throw PipelineFormatException("The document must be a mapping with a 'steps' list")

        val version = (root["version"] as? Number)?.toInt() ?: 1
        if (version != 1) throw PipelineFormatException("Unsupported version $version (expected 1)")

        val rawSteps = root["steps"] ?: emptyList<Any>()
        val stepList = rawSteps as? List<*>
            ?: throw PipelineFormatException("'steps' must be a list")

        if (stepList.size > 100) throw PipelineFormatException("Too many steps (limit 100)")

        val steps = stepList.mapIndexed { index, raw ->
            importStep(raw, index)
        }
        return Pipeline(version = version, steps = steps)
    }

    private fun importStep(raw: Any?, index: Int): Step {
        val map = raw as? Map<*, *>
            ?: throw PipelineFormatException("Step ${index + 1} must be a mapping")
        val name = string(map["name"])?.trim().orEmpty()
        if (name.isEmpty()) throw PipelineFormatException("Step ${index + 1} needs a name")

        val enabled = (map["enabled"] as? Boolean) ?: true
        val mode = when (val match = string(map["match"])?.lowercase()) {
            null, "all" -> MatchMode.ALL
            "any" -> MatchMode.ANY
            else -> throw PipelineFormatException("Step '$name': unknown match '$match' (use all or any)")
        }

        val checks = (map["checks"] as? List<*>).orEmpty().mapIndexed { i, rawCheck ->
            importCheck(rawCheck, name, i)
        }
        val actions = (map["actions"] as? List<*>).orEmpty().mapIndexed { i, rawAction ->
            importAction(rawAction, name, i)
        }

        return Step(
            id = PipelineDefaults.id(),
            name = name,
            enabled = enabled,
            mode = mode,
            checks = checks,
            actions = actions
        )
    }

    private fun importCheck(raw: Any?, stepName: String, index: Int): CheckSpec {
        if (raw is String) {
            val type = checkNames[raw.lowercase()]
                ?: throw PipelineFormatException("Step '$stepName': unknown check '$raw'")
            if (type !in booleanChecks && type != CheckType.ALWAYS) {
                throw PipelineFormatException("Step '$stepName': check '$raw' needs a value")
            }
            return CheckSpec(type = type, expected = true)
        }

        val map = raw as? Map<*, *>
            ?: throw PipelineFormatException("Step '$stepName': check ${index + 1} must be a name or a mapping")
        if (map.size != 1) {
            throw PipelineFormatException("Step '$stepName': check ${index + 1} must have exactly one key")
        }

        val key = map.keys.first().toString().lowercase()
        val type = checkNames[key]
            ?: throw PipelineFormatException("Step '$stepName': unknown check '$key'")
        val value = map.values.first()

        return when {
            type in booleanChecks -> CheckSpec(type = type, expected = (value as? Boolean)
                ?: throw PipelineFormatException("Step '$stepName': '$key' expects true or false"))
            type == CheckType.TEXT_MATCHES || type == CheckType.SENDER_MATCHES ->
                CheckSpec(type = type, rules = importRules(value, stepName, key))
            type == CheckType.CATEGORY_IS ->
                CheckSpec(type = type, categories = importCategories(value, stepName))
            else -> throw PipelineFormatException("Step '$stepName': '$key' does not take a value")
        }
    }

    private fun importRules(value: Any?, stepName: String, key: String): List<AllowRule> {
        val items = when (value) {
            is List<*> -> value
            null -> emptyList<Any>()
            else -> listOf(value)
        }
        if (items.isEmpty()) throw PipelineFormatException("Step '$stepName': '$key' needs at least one rule")
        return items.map { item ->
            when (item) {
                is Map<*, *> -> {
                    val pattern = string(item["regex"] ?: item["word"])
                        ?: throw PipelineFormatException("Step '$stepName': rule needs 'regex' or 'word'")
                    AllowRule(pattern, isRegex = item.containsKey("regex"))
                }
                else -> AllowRule(scalarString(item) ?: "", isRegex = false)
            }.also {
                if (it.pattern.isBlank()) throw PipelineFormatException("Step '$stepName': empty rule")
            }
        }
    }

    private fun importCategories(value: Any?, stepName: String): List<MessageCategory> {
        val items = when (value) {
            is List<*> -> value
            null -> emptyList<Any>()
            else -> listOf(value)
        }
        if (items.isEmpty()) throw PipelineFormatException("Step '$stepName': 'category_is' needs a category")
        return items.map { item ->
            val name = (scalarString(item) ?: "").uppercase()
            MessageCategory.entries.firstOrNull { it.name == name }
                ?: throw PipelineFormatException(
                    "Step '$stepName': unknown category '${scalarString(item)}' " +
                        "(use ${MessageCategory.entries.joinToString(", ") { it.name.lowercase() }})"
                )
        }
    }

    private fun importAction(raw: Any?, stepName: String, index: Int): ActionSpec {
        if (raw is String) {
            val type = actionNames[raw.lowercase()]
                ?: throw PipelineFormatException("Step '$stepName': unknown action '$raw'")
            if (type == ActionType.LABEL) {
                throw PipelineFormatException("Step '$stepName': 'label' needs a category, e.g. label: junk")
            }
            return ActionSpec(type = type)
        }

        val map = raw as? Map<*, *>
            ?: throw PipelineFormatException("Step '$stepName': action ${index + 1} must be a name or a mapping")
        if (map.size != 1) {
            throw PipelineFormatException("Step '$stepName': action ${index + 1} must have exactly one key")
        }

        val key = map.keys.first().toString().lowercase()
        val type = actionNames[key]
            ?: throw PipelineFormatException("Step '$stepName': unknown action '$key'")
        if (type != ActionType.LABEL) {
            throw PipelineFormatException("Step '$stepName': '$key' does not take a value")
        }
        val categoryName = (scalarString(map.values.first()) ?: "").uppercase()
        val category = MessageCategory.entries.firstOrNull { it.name == categoryName }
            ?: throw PipelineFormatException("Step '$stepName': unknown category '$categoryName'")
        return ActionSpec(type = ActionType.LABEL, category = category)
    }

    private fun exportCheck(check: CheckSpec): Any = when {
        check.type == CheckType.ALWAYS -> "always"
        check.type in booleanChecks -> {
            // Bare name when true keeps the common case terse; explicit false otherwise.
            if (check.expected) checkKey(check.type) else linkedMapOf(checkKey(check.type) to false)
        }
        check.type == CheckType.TEXT_MATCHES || check.type == CheckType.SENDER_MATCHES ->
            linkedMapOf(checkKey(check.type) to check.rules.map { exportRule(it) })
        check.type == CheckType.CATEGORY_IS ->
            linkedMapOf(checkKey(check.type) to check.categories.map { it.name.lowercase() })
        else -> checkKey(check.type)
    }

    private fun exportAction(action: ActionSpec): Any = when (action.type) {
        ActionType.LABEL -> linkedMapOf("label" to (action.category ?: MessageCategory.JUNK).name.lowercase())
        else -> actionKey(action.type)
    }

    private fun exportRule(rule: AllowRule): Any =
        if (rule.isRegex) linkedMapOf("regex" to rule.pattern) else rule.pattern

    private fun checkKey(type: CheckType): String = checkNames.entries.first { it.value == type }.key
    private fun actionKey(type: ActionType): String = actionNames.entries.first { it.value == type }.key

    private fun string(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        else -> null
    }

    /** SnakeYAML turns `yes`, `on`, numbers and dates into non-strings; accept them as text. */
    private fun scalarString(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        is Boolean -> value.toString()
        is Number -> value.toString()
        else -> value.toString()
    }
}
