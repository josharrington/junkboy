package com.ovehbe.junkboy.utils

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import android.util.Log
import com.ovehbe.junkboy.classifier.SmsClassifier
import com.ovehbe.junkboy.database.AppDatabase
import com.ovehbe.junkboy.database.FilteredMessage
import com.ovehbe.junkboy.database.MessageCategory
import com.ovehbe.junkboy.filters.ContentAllowlist
import com.ovehbe.junkboy.filters.CustomFilter
import com.ovehbe.junkboy.filters.Stop2EndDetector
import com.ovehbe.junkboy.pipeline.DecisionTraceCodec
import com.ovehbe.junkboy.pipeline.ExecutionPlan
import com.ovehbe.junkboy.pipeline.MessageContext
import com.ovehbe.junkboy.pipeline.PipelineRunner
import com.ovehbe.junkboy.pipeline.decisionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Imports the device's existing inbox through the same pipeline the service uses.
 *
 * Imported messages are recorded as test decisions: the pipeline is evaluated, but nothing is
 * blocked, replied to or deleted for messages that already arrived. Duplicates are skipped by
 * (sender, timestamp, body), so running the import twice does not inflate history or statistics.
 */
class ExistingMessagesProcessor(private val context: Context) {

    companion object {
        private const val TAG = "ExistingMessagesProcessor"
    }

    private val database = AppDatabase.getDatabase(context)
    private val preferencesManager = PreferencesManager(context)
    private val smsClassifier = SmsClassifier.getInstance()
    private val senderHistoryChecker = SenderHistoryChecker(context)
    private val otpHelper = OtpHelper(context)

    suspend fun processAllExistingMessages(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to process all existing messages")

            if (!smsClassifier.initialize(context)) {
                Log.w(TAG, "ML classifier initialization failed, using rule-based filtering only")
            }

            val existingMessages = readAllSmsMessages()
            Log.d(TAG, "Found ${existingMessages.size} existing messages")

            val pipeline = preferencesManager.getPipeline()
            var importedCount = 0
            var skippedCount = 0

            for (smsMessage in existingMessages) {
                try {
                    val alreadyStored = database.filteredMessageDao().countMatchingMessage(
                        sender = smsMessage.sender,
                        body = smsMessage.body,
                        receivedAt = smsMessage.timestamp
                    ) > 0
                    if (alreadyStored) {
                        skippedCount++
                        continue
                    }

                    val filteredMessage = processMessage(smsMessage, pipeline)
                    database.filteredMessageDao().insertMessage(filteredMessage)
                    updateStatistics(filteredMessage.category, filteredMessage.isBlocked)
                    importedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing message from ${smsMessage.sender}", e)
                }
            }

            Log.d(TAG, "Finished importing $importedCount existing messages ($skippedCount already stored)")
            Result.success(importedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing existing messages", e)
            Result.failure(e)
        }
    }

    private suspend fun readAllSmsMessages(): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { c: Cursor ->
            val addressIndex = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIndex = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)

            while (c.moveToNext()) {
                if (c.getInt(typeIndex) == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    messages.add(
                        SmsMessage(
                            sender = c.getString(addressIndex) ?: "Unknown",
                            body = c.getString(bodyIndex) ?: "",
                            timestamp = c.getLong(dateIndex)
                        )
                    )
                }
            }
        }

        return messages
    }

    private suspend fun processMessage(
        smsMessage: SmsMessage,
        pipeline: com.ovehbe.junkboy.pipeline.Pipeline
    ): FilteredMessage {
        val allowedBySender = isAllowedSenderFlexible(smsMessage.sender)
        val allowRule = if (allowedBySender) null
        else ContentAllowlist.firstMatch(preferencesManager.getAllowRules(), smsMessage.body)
        val allowedByContent = allowRule != null

        val classification = classify(smsMessage)
        val context = MessageContext(
            sender = smsMessage.sender,
            body = smsMessage.body,
            timestamp = smsMessage.timestamp,
            category = classification.category,
            isBlocked = classification.isBlocked,
            isContact = senderHistoryChecker.isContact(smsMessage.sender),
            hasOutboundHistory = senderHistoryChecker.hasOutboundHistory(smsMessage.sender),
            hasOtp = otpHelper.testOtpDetection(smsMessage.body) != null,
            hasOptOutDirective = Stop2EndDetector.detect(smsMessage.body) != Stop2EndDetector.Directive.NONE,
            allowedBySender = allowedBySender,
            allowedByContent = allowedByContent
        )
        val evaluation = PipelineRunner.evaluate(pipeline, context)

        // Historical import never enforces: it records what the pipeline would have done.
        val plan = ExecutionPlan.forMode(evaluation, enforcing = false)

        return FilteredMessage(
            sender = smsMessage.sender,
            messageBody = smsMessage.body,
            receivedAt = Date(smsMessage.timestamp),
            category = evaluation.category,
            confidence = classification.confidence,
            filterType = classification.filterType,
            isBlocked = plan.block,
            isUserOverride = evaluation.category == MessageCategory.ALLOWED,
            isRead = false,
            isTestMode = true,
            decisionSummary = evaluation.decisionSummary(),
            decisionTrace = DecisionTraceCodec.encode(evaluation.trace),
            decisionStep = evaluation.blockedBy ?: evaluation.repliedBy ?: "",
            decisionReplyStep = evaluation.repliedBy ?: ""
        )
    }

    /** Classifier evidence only; the pipeline is the policy that decides what happens. */
    private fun classify(smsMessage: SmsMessage): com.ovehbe.junkboy.filters.FilterResult {
        val isMlEnabled = preferencesManager.isMlFilteringEnabled()
        val isKeywordEnabled = preferencesManager.isKeywordFilteringEnabled()
        val isRegexEnabled = preferencesManager.isRegexFilteringEnabled()

        return when {
            isMlEnabled -> smsClassifier.classify(smsMessage.body)
            isKeywordEnabled || isRegexEnabled -> CustomFilter.filterMessage(
                message = smsMessage.body,
                sender = smsMessage.sender,
                isUnderAttackMode = preferencesManager.isUnderAttackMode(),
                customKeywords = if (isKeywordEnabled) preferencesManager.getCustomKeywords() else emptyList(),
                customRegexPatterns = if (isRegexEnabled) preferencesManager.getCustomRegexPatterns() else emptyList()
            )
            else -> com.ovehbe.junkboy.filters.FilterResult(
                isBlocked = false,
                category = MessageCategory.GENERAL,
                filterType = com.ovehbe.junkboy.database.FilterType.KEYWORD_FILTER,
                confidence = 0.0f
            )
        }
    }

    private fun updateStatistics(category: MessageCategory, isBlocked: Boolean) {
        try {
            preferencesManager.incrementCategoryCount(category)
            if (isBlocked) {
                preferencesManager.incrementBlockedCount()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating statistics", e)
        }
    }

    private suspend fun isAllowedSenderFlexible(sender: String): Boolean {
        val dao = database.allowedSenderDao()

        if (dao.isAllowedSender(sender)) return true
        if (dao.isAllowedSenderCaseInsensitive(sender)) return true

        val normalizedSender = normalizePhoneNumber(sender)
        if (normalizedSender != sender && dao.isAllowedSenderCaseInsensitive(normalizedSender)) {
            return true
        }

        for (allowed in dao.getAllowedSendersList()) {
            val normalizedAllowed = normalizePhoneNumber(allowed.phoneNumber)
            if (normalizedSender == normalizedAllowed) return true
            if (normalizedSender.length >= 10 && normalizedAllowed.length >= 10 &&
                normalizedSender.takeLast(10) == normalizedAllowed.takeLast(10)
            ) {
                return true
            }
            if (sender.equals(allowed.phoneNumber, ignoreCase = true)) return true
        }

        return false
    }

    private fun normalizePhoneNumber(phone: String): String {
        return phone
            .replace(Regex("[+\\-()\\s]"), "")
            .replace(Regex("^0+"), "")
    }

    data class SmsMessage(
        val sender: String,
        val body: String,
        val timestamp: Long
    )
}
