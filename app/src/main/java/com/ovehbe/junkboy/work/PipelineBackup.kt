package com.ovehbe.junkboy.work

import android.content.Context
import com.ovehbe.junkboy.pipeline.PipelineYaml
import com.ovehbe.junkboy.utils.PreferencesManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes the pipeline as YAML plus a small settings snapshot into app-specific storage. */
object PipelineBackup {

    fun directory(context: Context): File =
        context.getExternalFilesDir("backups") ?: File(context.filesDir, "backups")

    fun write(context: Context): File? = try {
        val prefs = PreferencesManager(context)
        val dir = directory(context).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

        val pipelineFile = File(dir, "pipeline-$stamp.yaml")
        pipelineFile.writeText(PipelineYaml.export(prefs.getPipeline()))

        File(dir, "settings-$stamp.txt").writeText(
            buildString {
                appendLine("mode=${if (prefs.isEnforcementEnabled()) "enforcing" else "testing"}")
                appendLine("retentionDays=${prefs.getMessageRetentionDays()}")
                appendLine("autoPauseStale=${prefs.isAutoPauseStaleEnabled()}")
            }
        )

        prune(dir)
        pipelineFile
    } catch (e: Exception) {
        null
    }

    private fun prune(dir: File) {
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(60)
            ?.forEach { it.delete() }
    }
}
