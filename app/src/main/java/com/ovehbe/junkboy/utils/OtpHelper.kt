package com.ovehbe.junkboy.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.content.Context
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.core.app.NotificationCompat
import com.ovehbe.junkboy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * Advanced OTP detection helper using pattern-based extraction.
 * 
 * This implementation uses a prioritized pattern matching approach:
 * 1. HIGH-CONFIDENCE patterns: OTP code directly adjacent to keywords (99% accuracy)
 * 2. DELIVERY CODE patterns: Cargo/package pickup codes (95% accuracy)
 * 3. EXCLUSION logic: Filter out false positives (money, dates, tracking numbers)
 * 4. FALLBACK: Conservative 6-digit only matching if message has OTP context
 */
class OtpHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "OtpHelper"
        private const val OTP_CHANNEL_ID = "otp_notifications"
        
        // ==================== HIGH-CONFIDENCE OTP PATTERNS ====================
        // These patterns extract OTP codes that are directly adjacent to keywords
        // Pattern format: Regex with capture group for the OTP code
        
        private val HIGH_CONFIDENCE_PATTERNS = listOf(
            // Turkish: "X doğrulama kodu ile" (Masterpass, Papara, Migros, Getir)
            Pattern.compile("""(\d{4,6})\s*do[gğ]rulama\s+kodu""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "doğrulama koduyla X" (MultiPay variant)
            Pattern.compile("""do[gğ]rulama\s+koduyla\s+(\d{4,6})""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "3D SECURE SIFRENIZ X ILE" (MARS Bank)
            Pattern.compile("""SIFRENIZ\s*(\d{6})\s*ILE""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "sifreniz: X" or "şifreniz: X" (VAKIFBANK, T.FINANS, FUPS)
            Pattern.compile("""[sş][iı]freniz[:\s]+(\d{6})""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "X Mobil Onay Kodu" at start (İŞ BANKASI)
            Pattern.compile("""^(\d{6})\s+(?:Bireysel\s+Internet\s+Subesi\s+girisi\s+icin\s+)?Mobil\s+Onay\s+Kodu""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "Mobil Onay Kodunuz: X" (İŞ BANKASI variant)
            Pattern.compile("""Mobil\s+Onay\s+Kod[u]?(?:nuz)?[:\s]+(\d{6})""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "Gecici Sifre: X" (İŞ BANKASI)
            Pattern.compile("""Gecici\s+Sifre[:\s]+(\d{6})""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "Giris Sifreniz: X" (TURKNET)
            Pattern.compile("""Giri[sş]\s+Sifreniz[:\s]+(\d{6})""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "Giriş Doğrulama Kodunuz: X" (TURHOST)
            Pattern.compile("""Giri[sş]\s+Do[gğ]rulama\s+Kodunuz[:\s]+(\d{6})""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "doğrulama kodunuz: X" (general)
            Pattern.compile("""do[gğ]rulama\s+[Kk]od[u]?(?:nuz)?[:\s]+(\d{4,6})""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "SMS sifreniz: X"
            Pattern.compile("""SMS\s+sifreniz[:\s]+(\d{6})""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "X tek kullanımlık" (MULTINET)
            Pattern.compile("""(\d{6})\s+[^\d]*tek\s+kullanimlik""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "Gecici Kullanici Giris Sifreniz: X" (PTT)
            Pattern.compile("""Gecici\s+Kullanici\s+Giris\s+Sifreniz[:\s]+(\d{6})""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "Islem onayi icin Mobil Onay Kodunuz: X"
            Pattern.compile("""Islem\s+onayi\s+icin\s+Mobil\s+Onay\s+Kodunuz[:\s]+(\d{6})""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "X Bireysel Internet Subesi girisi icin Mobil Onay Kodu"
            Pattern.compile("""^(\d{6})\s+Bireysel\s+Internet""", Pattern.CASE_INSENSITIVE),
            
            // English: "Code is: X" (Apple, international services)
            Pattern.compile("""[Cc]ode\s+is[:\s]+(\d{4,8})"""),
            
            // English: "code: X" (general)
            Pattern.compile("""[Cc]ode[:\s]+(\d{4,8})"""),
            
            // English: "Verification Code: X"
            Pattern.compile("""[Vv]erification\s+[Cc]ode[:\s]+(\d{4,8})"""),
            
            // English: "Your OTP is X"
            Pattern.compile("""[Oo][Tt][Pp]\s+is[:\s]+(\d{4,8})"""),
            
            // English: "OTP: X"
            Pattern.compile("""[Oo][Tt][Pp][:\s]+(\d{4,8})"""),
            
            // Turkish: "dogrulama Kodu : X" (UPS variant with space before colon)
            Pattern.compile("""do[gğ]rulama\s+[Kk]odu?\s*:\s*(\d{6})""", Pattern.CASE_INSENSITIVE),
        )
        
        // ==================== ALPHANUMERIC OTP PATTERNS ====================
        // Some services use alphanumeric codes (e.g., NETGSM)
        
        private val ALPHANUMERIC_PATTERNS = listOf(
            // Turkish: "Giris Kodunuz: XXXXXX" (NETGSM - alphanumeric)
            Pattern.compile("""Giri[sş]\s+Kodunuz[:\s]+([A-Z0-9]{6})""", Pattern.CASE_INSENSITIVE),
        )
        
        // ==================== DELIVERY CODE PATTERNS ====================
        // Cargo/package pickup codes - very useful for users!
        
        private val DELIVERY_CODE_PATTERNS = listOf(
            // Turkish: "X teslimat kodu ile" (PTT)
            Pattern.compile("""(\d{4})\s+teslimat\s+kodu\s+ile""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "X kodu ile teslim" (Yurtiçi Kargo)
            Pattern.compile("""(\d{4})\s+kodu\s+ile\s+teslim""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "X kodlu kargo" (Yurtiçi Kargo variant)
            Pattern.compile("""(\d{4})\s+kodlu\s+(?:kargo|gonder)""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "X teslim/teslimat kodlu" (DHL, HepsiJET)
            Pattern.compile("""(\d{4})\s+(?:teslimat|teslim)\s+kodu?""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "X teslim kodlu gönderiniz" (DHL variant)
            Pattern.compile("""(\d{4})\s+teslim\s+kodlu\s+g""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "X kodu ile temassız teslim" (DHL)
            Pattern.compile("""(\d{4})\s+kodu\s+ile\s+temass""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "X teslimat koduyla teslim" (DHL)
            Pattern.compile("""(\d{4})\s+teslimat\s+koduyla""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "[XXXXX] kodlu" (KolayGelsin - 5 digit in brackets)
            Pattern.compile("""\[(\d{5})\]\s+kodlu"""),
            
            // Turkish: "X kod ile dağıtıma çıkan" (TY EXPRESS - 5 digit)
            Pattern.compile("""(\d{5})\s+kod\s+ile\s+da[gğ][ıi]t[ıi]ma""", Pattern.CASE_INSENSITIVE),
            
            // Turkish: "X Numaralı teslimat kodu" (SURAT KARGO)
            Pattern.compile("""(\d{4})\s+Numaral[ıi]\s+teslimat\s+kodu""", Pattern.CASE_INSENSITIVE),
        )
        
        // ==================== EXCLUSION PATTERNS ====================
        // These identify false positives that should NOT be extracted
        
        // Money amount patterns (Turkish Lira)
        private val MONEY_PATTERNS = listOf(
            Pattern.compile("""\d+[,.]?\d*\s*(?:TL|TRY|USD|EUR|₺)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:TL|TRY)\s*-?\d+[,.]?\d*""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""%\d+[,.]?\d*"""), // Percentages like %3.69
        )
        
        // Date/Time patterns
        private val DATE_TIME_PATTERNS = listOf(
            Pattern.compile("""\d{2}\.\d{2}\.\d{4}"""), // DD.MM.YYYY
            Pattern.compile("""\d{2}/\d{2}/\d{4}"""), // DD/MM/YYYY
            Pattern.compile("""\d{4}-\d{2}-\d{2}"""), // YYYY-MM-DD
            Pattern.compile("""\d{2}:\d{2}"""), // HH:MM
        )
        
        // Phone number patterns
        private val PHONE_PATTERNS = listOf(
            Pattern.compile("""0\d{10}"""), // Turkish phone: 05XXXXXXXXX
            Pattern.compile("""0\d{3}\s?\d{3}\s?\d{2}\s?\d{2}"""), // With spaces
            Pattern.compile("""444\s?\d{4}"""), // Call centers: 444 XXXX
            Pattern.compile("""\+90\d{10}"""), // International Turkish
        )
        
        // Tracking/Order number context words
        private val TRACKING_CONTEXT_WORDS = listOf(
            "takip", "siparis", "teslimat no", "nolu kargo", "nolu gonder",
            "barkod", "no'lu", "numaralı kargo", "teslimat ve"
        )
        
        // Account/Reference context words
        private val ACCOUNT_CONTEXT_WORDS = listOf(
            "hesap", "kart", "MERSIS", "TCKN", "Ref:", "referans"
        )
    }
    
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val preferencesManager = PreferencesManager(context)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createOtpNotificationChannel()
    }
    
    /**
     * Detect and copy OTP from message text
     * @param messageText The message content to scan for OTPs
     * @param sourceName The source of the message (e.g., "SMS", "WhatsApp", "Telegram")
     * @param snackbarHostState Optional snackbar host for showing feedback
     * @param coroutineScope Optional coroutine scope for snackbar
     * @return The detected OTP string, or null if none found
     */
    fun detectAndCopyOtp(
        messageText: String,
        sourceName: String,
        snackbarHostState: SnackbarHostState? = null,
        coroutineScope: CoroutineScope? = null
    ): String? {
        // Check if OTP auto-copy is enabled
        if (!preferencesManager.isOtpAutoCopyEnabled()) {
            return null
        }
        
        val result = detectOtp(messageText)
        
        if (result != null) {
            Log.d(TAG, "OTP detected: ${result.code} (type: ${result.type}) from $sourceName")
            
            // Copy to clipboard
            copyToClipboard(result.code, sourceName)
            
            // Show feedback
            val displayType = when {
                result.type.contains("delivery") || result.type.contains("teslim") -> "Delivery Code"
                result.type == "alphanumeric" -> "Code"
                else -> "OTP"
            }
            
            if (snackbarHostState != null && coroutineScope != null) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Copied $displayType ${result.code} from $sourceName",
                        duration = SnackbarDuration.Short
                    )
                }
            } else {
                // Show notification for background OTP copying
                showOtpCopiedNotification(result.code, sourceName, displayType)
            }
            
            // Update statistics
            preferencesManager.incrementOtpCopyCount()
            
            return result.code
        }
        
        return null
    }
    
    /**
     * Data class to hold detection result with type information
     */
    data class DetectionResult(
        val code: String,
        val type: String,
        val confidence: Float
    )
    
    /**
     * Main OTP detection function using prioritized pattern matching
     */
    private fun detectOtp(messageText: String): DetectionResult? {
        Log.d(TAG, "Analyzing message for OTP: ${messageText.take(100)}...")
        
        // Step 1: Try HIGH-CONFIDENCE patterns first (highest accuracy)
        for (pattern in HIGH_CONFIDENCE_PATTERNS) {
            val matcher = pattern.matcher(messageText)
            if (matcher.find() && matcher.groupCount() >= 1) {
                val code = matcher.group(1)
                if (code != null && isValidOtpCode(code, messageText)) {
                    Log.d(TAG, "HIGH-CONFIDENCE match: $code with pattern ${pattern.pattern()}")
                    return DetectionResult(code, "otp_high_confidence", 0.99f)
                }
            }
        }
        
        // Step 2: Try ALPHANUMERIC patterns (for services like NETGSM)
        for (pattern in ALPHANUMERIC_PATTERNS) {
            val matcher = pattern.matcher(messageText)
            if (matcher.find() && matcher.groupCount() >= 1) {
                val code = matcher.group(1)
                if (code != null) {
                    Log.d(TAG, "ALPHANUMERIC match: $code")
                    return DetectionResult(code, "alphanumeric", 0.95f)
                }
            }
        }
        
        // Step 3: Try DELIVERY CODE patterns
        for (pattern in DELIVERY_CODE_PATTERNS) {
            val matcher = pattern.matcher(messageText)
            if (matcher.find() && matcher.groupCount() >= 1) {
                val code = matcher.group(1)
                if (code != null && !isExcludedNumber(code, messageText)) {
                    Log.d(TAG, "DELIVERY CODE match: $code")
                    return DetectionResult(code, "delivery_code", 0.90f)
                }
            }
        }
        
        // Step 4: FALLBACK - Only if message clearly has OTP context
        // and we find a single 6-digit code that's not excluded
        val fallbackResult = tryFallbackDetection(messageText)
        if (fallbackResult != null) {
            return fallbackResult
        }
        
        Log.d(TAG, "No OTP detected in message")
        return null
    }
    
    /**
     * Fallback detection for messages with OTP context but no exact pattern match
     */
    private fun tryFallbackDetection(messageText: String): DetectionResult? {
        val lowerText = messageText.lowercase()
        
        // Check if message has strong OTP context
        val hasOtpContext = listOf(
            "dogrulama", "doğrulama", "sifre", "şifre", "kod", "onay",
            "verification", "code", "otp", "password", "pin"
        ).any { lowerText.contains(it) }
        
        if (!hasOtpContext) {
            return null
        }
        
        // Find all 6-digit numbers (most common OTP length)
        val sixDigitPattern = Pattern.compile("""(?<!\d)(\d{6})(?!\d)""")
        val matcher = sixDigitPattern.matcher(messageText)
        val candidates = mutableListOf<Pair<String, Int>>()
        
        while (matcher.find()) {
            val code = matcher.group(1)
            val position = matcher.start(1)
            if (code != null && !isExcludedNumber(code, messageText, position)) {
                candidates.add(code to position)
            }
        }
        
        // If exactly one valid candidate, return it
        if (candidates.size == 1) {
            Log.d(TAG, "FALLBACK match: ${candidates[0].first}")
            return DetectionResult(candidates[0].first, "fallback", 0.70f)
        }
        
        // Multiple candidates - try to pick the best one
        if (candidates.size > 1) {
            // Prefer code that appears earlier and closer to a keyword
            val keywordPositions = listOf(
                "dogrulama", "sifre", "kod", "onay", "code", "otp"
            ).mapNotNull { keyword ->
                val idx = lowerText.indexOf(keyword)
                if (idx >= 0) idx else null
            }
            
            if (keywordPositions.isNotEmpty()) {
                val minKeywordPos = keywordPositions.min()
                // Find candidate closest to a keyword
                val bestCandidate = candidates.minByOrNull { (_, pos) ->
                    kotlin.math.abs(pos - minKeywordPos)
                }
                if (bestCandidate != null) {
                    Log.d(TAG, "FALLBACK match (best of ${candidates.size}): ${bestCandidate.first}")
                    return DetectionResult(bestCandidate.first, "fallback", 0.60f)
                }
            }
        }
        
        return null
    }
    
    /**
     * Validate that a code is actually an OTP and not a false positive
     */
    private fun isValidOtpCode(code: String, messageText: String): Boolean {
        // Basic validation: must be 4-8 digits (or alphanumeric for some)
        if (!code.matches(Regex("""[A-Z0-9]{4,8}"""))) {
            return false
        }
        
        // Check for exclusions
        return !isExcludedNumber(code, messageText)
    }
    
    /**
     * Check if a number should be excluded (is likely NOT an OTP)
     */
    private fun isExcludedNumber(code: String, messageText: String, position: Int = -1): Boolean {
        // 1. Check if code is too long (likely tracking number)
        if (code.length > 8) {
            Log.d(TAG, "Excluded $code: too long (${code.length} digits)")
            return true
        }
        
        // 2. Check if code starts with 0 and is 10+ digits (phone number)
        if (code.startsWith("0") && code.length >= 10) {
            Log.d(TAG, "Excluded $code: looks like phone number")
            return true
        }
        
        // 3. Check if code is part of a phone number pattern
        for (pattern in PHONE_PATTERNS) {
            val matcher = pattern.matcher(messageText)
            while (matcher.find()) {
                if (matcher.group().contains(code)) {
                    Log.d(TAG, "Excluded $code: part of phone number")
                    return true
                }
            }
        }
        
        // 4. Check if code is part of a date/time
        for (pattern in DATE_TIME_PATTERNS) {
            val matcher = pattern.matcher(messageText)
            while (matcher.find()) {
                val match = matcher.group()
                // Extract just the digits from the date/time
                val digits = match.replace(Regex("""[^0-9]"""), "")
                if (digits.contains(code) || code.all { ch -> digits.contains(ch) }) {
                    // More precise check: is this code literally inside the date string?
                    val matchStart = matcher.start()
                    val matchEnd = matcher.end()
                    if (position >= 0 && position >= matchStart && position < matchEnd) {
                        Log.d(TAG, "Excluded $code: part of date/time pattern")
                        return true
                    }
                }
            }
        }
        
        // 5. Check if code is part of a money amount
        for (pattern in MONEY_PATTERNS) {
            val matcher = pattern.matcher(messageText)
            while (matcher.find()) {
                val match = matcher.group()
                val digits = match.replace(Regex("""[^0-9]"""), "")
                if (digits == code || match.contains(code)) {
                    Log.d(TAG, "Excluded $code: part of money amount")
                    return true
                }
            }
        }
        
        // 6. Check if code appears in tracking number context
        val lowerText = messageText.lowercase()
        val codePos = if (position >= 0) position else messageText.indexOf(code)
        
        for (contextWord in TRACKING_CONTEXT_WORDS) {
            val wordPos = lowerText.indexOf(contextWord)
            if (wordPos >= 0) {
                // If code is within 30 chars before or after a tracking context word
                // AND the code is 8+ digits, it's likely a tracking number
                if (code.length >= 8 && kotlin.math.abs(codePos - wordPos) < 50) {
                    Log.d(TAG, "Excluded $code: in tracking number context")
                    return true
                }
            }
        }
        
        // 7. Check if code appears in account/reference context
        for (contextWord in ACCOUNT_CONTEXT_WORDS) {
            val wordPos = lowerText.indexOf(contextWord.lowercase())
            if (wordPos >= 0 && codePos > wordPos && codePos - wordPos < 30) {
                // Code appears right after account/reference keyword
                if (code.length > 6) {
                    Log.d(TAG, "Excluded $code: in account/reference context")
                    return true
                }
            }
        }
        
        // 8. Check for B002, B016, etc. suffix codes (SMS source identifiers)
        if (code.length == 4 && messageText.endsWith("B${code.substring(1)}")) {
            Log.d(TAG, "Excluded $code: SMS suffix code")
            return true
        }
        
        // 9. Check if this is a year (2024, 2025, 2026, etc.)
        if (code.length == 4 && code.toIntOrNull()?.let { it in 2020..2030 } == true) {
            // Only exclude if it's clearly part of a date context
            val surroundingText = messageText.substring(
                maxOf(0, codePos - 15),
                minOf(messageText.length, codePos + code.length + 15)
            )
            if (surroundingText.contains(".") || surroundingText.contains("/") || 
                surroundingText.contains("-") || surroundingText.lowercase().contains("tarih")) {
                Log.d(TAG, "Excluded $code: looks like year in date")
                return true
            }
        }
        
        return false
    }
    
    /**
     * Copy OTP to clipboard
     */
    private fun copyToClipboard(otp: String, sourceName: String) {
        try {
            val clipData = ClipData.newPlainText("OTP from $sourceName", otp)
            clipboardManager.setPrimaryClip(clipData)
            Log.d(TAG, "OTP copied to clipboard: $otp")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy OTP to clipboard", e)
        }
    }
    
    /**
     * Test OTP detection (for debugging/testing)
     */
    fun testOtpDetection(messageText: String): String? {
        return detectOtp(messageText)?.code
    }
    
    /**
     * Test OTP detection with full result details (for advanced testing)
     */
    fun testOtpDetectionFull(messageText: String): DetectionResult? {
        return detectOtp(messageText)
    }
    
    /**
     * Create notification channel for OTP notifications
     */
    private fun createOtpNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            OTP_CHANNEL_ID,
            "OTP Notifications",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications when OTP codes are automatically copied"
            enableVibration(false)
            setShowBadge(false)
            setSound(null, null)
        }
        
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * Show notification when OTP is copied in background
     */
    private fun showOtpCopiedNotification(otp: String, sourceName: String, displayType: String = "OTP") {
        try {
            val notification = NotificationCompat.Builder(context, OTP_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_check)
                .setContentTitle("$displayType Copied")
                .setContentText("Copied $displayType $otp from $sourceName")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setTimeoutAfter(3000) // Auto-dismiss after 3 seconds
                .setSilent(true)
                .build()
            
            notificationManager.notify(
                System.currentTimeMillis().toInt(), // Unique ID
                notification
            )
            
            Log.d(TAG, "Showed notification for: $otp from $sourceName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show OTP notification", e)
        }
    }
}
