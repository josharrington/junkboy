package com.ovehbe.junkboy.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ovehbe.junkboy.database.AppDatabase
import com.ovehbe.junkboy.utils.PreferencesManager
import java.util.Date

/** Deletes Junkboy's own copies of old messages. The carrier and Google Messages are untouched. */
class RetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        val cutoffMillis = RetentionPolicy.cutoffMillis(System.currentTimeMillis(), prefs.getMessageRetentionDays())
            ?: return Result.success()
        val cutoff = Date(cutoffMillis)
        val database = AppDatabase.getDatabase(applicationContext)
        database.filteredMessageDao().deleteMessagesOlderThan(cutoff)
        database.chatMessageDao().deleteChatMessagesOlderThan(cutoff)
        database.autoReplyLogDao().deleteRepliesOlderThan(cutoff.time)
        return Result.success()
    }
}
