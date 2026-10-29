package com.ovehbe.junkboy.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ovehbe.junkboy.utils.PreferencesManager

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!PreferencesManager(applicationContext).isBackupEnabled()) return Result.success()
        return if (PipelineBackup.write(applicationContext) != null) Result.success() else Result.retry()
    }
}
