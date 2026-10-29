package com.ovehbe.junkboy.pipeline

import com.ovehbe.junkboy.database.MessageCategory
import com.ovehbe.junkboy.filters.AllowRule
import com.ovehbe.junkboy.filters.CustomFilter
import com.ovehbe.junkboy.filters.FilterResult

/**
 * A fixed set of messages for exercising a pipeline. They are deliberately ordinary: none of the
 * bodies rely on private data, and the verdicts come from the built-in keyword/regex rules so the
 * same expectations hold in unit tests and in the in-app lab.
 */
data class SampleMessage(
    val id: String,
    val title: String,
    val sender: String,
    val body: String,
    val fromContact: Boolean = false,
    val hasOutboundHistory: Boolean = false,
    val hasOtp: Boolean = false,
    val expectBlocked: Boolean,
    val expectReply: Boolean
)

data class SampleResult(
    val sample: SampleMessage,
    val evaluation: Evaluation,
    val verdict: FilterResult
) {
    val blockedMatches: Boolean get() = evaluation.blocked == sample.expectBlocked
    val replyMatches: Boolean get() = evaluation.replyStop == sample.expectReply
    val matches: Boolean get() = blockedMatches && replyMatches
}

object SampleMessages {

    /** Whole-word rules used by the reference pipeline, so "vote" matches but "voter fraud" does not. */
    val politicalWords = listOf(
        "democrat", "republican", "gop", "senate", "campaign", "vote", "election", "candidate"
    )

    /** The pipeline the expectations are written against. */
    fun samplePipeline(): Pipeline = Pipeline(
        version = 1,
        steps = listOf(
            PipelineDefaults.politicalReplyStep(politicalWords.map { AllowRule(it) }),
            Step(
                id = "sample-block",
                name = "Block junk",
                mode = MatchMode.ALL,
                checks = listOf(CheckSpec(type = CheckType.SPAM_VERDICT)),
                actions = listOf(ActionSpec(ActionType.BLOCK))
            )
        )
    )

    val all: List<SampleMessage> = listOf(
        // --- OTP codes: detected, never replied to -------------------------------------------
        SampleMessage(
            id = "otp-bank",
            title = "Bank OTP",
            sender = "+15550001111",
            body = "Your verification code is 482913. It expires in 10 minutes. Never share this code.",
            hasOtp = true,
            expectBlocked = false,
            expectReply = false
        ),
        SampleMessage(
            id = "otp-apple",
            title = "Apple ID code",
            sender = "Apple",
            body = "Your Apple ID verification code is 883021. Do not share it with anyone.",
            hasOtp = true,
            expectBlocked = false,
            expectReply = false
        ),
        SampleMessage(
            id = "otp-delivery",
            title = "Delivery code",
            sender = "DHL",
            body = "DHL delivery code: 5521. Show this code to the courier.",
            hasOtp = true,
            expectBlocked = false,
            expectReply = false
        ),
        SampleMessage(
            id = "otp-alphanumeric",
            title = "Alphanumeric code",
            sender = "NETGSM",
            body = "NETGSM verification code: A7K2P9. Valid for 5 minutes.",
            hasOtp = true,
            expectBlocked = false,
            expectReply = false
        ),
        SampleMessage(
            id = "otp-in-political",
            title = "OTP inside a political text",
            sender = "+18885550123",
            body = "Your verification code is 112233. This message was paid for by the Republican National Committee. Reply STOP to opt out.",
            hasOtp = true,
            expectBlocked = false,
            expectReply = false
        ),

        // --- Political spam: replied to, not blocked -----------------------------------------
        SampleMessage(
            id = "politics-donation",
            title = "Donation ask",
            sender = "+12025550143",
            body = "Donate $5 before midnight to help elect our candidate. Reply STOP to opt out.",
            expectBlocked = false,
            expectReply = true
        ),
        SampleMessage(
            id = "politics-rally",
            title = "Rally invite",
            sender = "+12025550188",
            body = "Join the rally for our senate candidate this Saturday at the fairgrounds. Reply STOP to end.",
            expectBlocked = false,
            expectReply = true
        ),
        SampleMessage(
            id = "politics-poll",
            title = "Poll text with no opt-out line",
            sender = "POLITEAM",
            body = "Who will you vote for in November? Take our two minute poll.",
            expectBlocked = false,
            expectReply = true
        ),
        SampleMessage(
            id = "politics-contact",
            title = "Political text from a contact",
            sender = "+12025550199",
            body = "Donate $5 before midnight to help elect our candidate. Reply STOP to opt out.",
            fromContact = true,
            expectBlocked = false,
            expectReply = false
        ),
        SampleMessage(
            id = "politics-history",
            title = "Political text you have answered before",
            sender = "+12025550177",
            body = "Our campaign needs volunteers this weekend. Reply STOP to opt out.",
            hasOutboundHistory = true,
            expectBlocked = false,
            expectReply = false
        ),

        // --- Appointments: informational, never replied to -----------------------------------
        SampleMessage(
            id = "appointment-doctor",
            title = "Doctor appointment",
            sender = "+18005550100",
            body = "Reminder: your appointment with Dr. Lee is Tuesday at 3:15 PM. Reply C to confirm.",
            expectBlocked = false,
            expectReply = false
        ),
        SampleMessage(
            id = "appointment-stop2end",
            title = "Clinic message that says STOP2END",
            sender = "+18005550101",
            body = "Your appointment is confirmed for Monday 9 AM. Reply STOP2END to opt out.",
            expectBlocked = false,
            expectReply = false
        ),

        // --- Personal: ordinary conversation ------------------------------------------------
        SampleMessage(
            id = "personal-uncle",
            title = "Birthday from an unknown uncle",
            sender = "+15550002222",
            body = "Happy birthday! Hope you have a great day. Love, Uncle Bob.",
            expectBlocked = false,
            expectReply = false
        ),
        SampleMessage(
            id = "personal-friend",
            title = "Birthday from a contact",
            sender = "+15550003333",
            body = "Happy birthday! Hope you have a great day.",
            fromContact = true,
            expectBlocked = false,
            expectReply = false
        ),

        // --- Scams: blocked, never replied to -----------------------------------------------
        SampleMessage(
            id = "scam-lottery",
            title = "Lottery win",
            sender = "+12345678901",
            body = "Congratulations! You won $1,000,000 in the international lottery. Claim your prize now.",
            expectBlocked = true,
            expectReply = false
        ),
        SampleMessage(
            id = "scam-prince",
            title = "Nigerian prince",
            sender = "+2348012345678",
            body = "I am a prince with $10 million to transfer. Send your bank account and a small fee urgently.",
            expectBlocked = true,
            expectReply = false
        ),
        SampleMessage(
            id = "scam-crypto",
            title = "Double your money",
            sender = "+19876543210",
            body = "Double your money in 24 hours. Act now and send 0.1 BTC.",
            expectBlocked = true,
            expectReply = false
        ),
        SampleMessage(
            id = "scam-parcel",
            title = "Parcel held for a fee",
            sender = "+14445556666",
            body = "Urgent: your parcel is held. Pay a small fee at example-delivery.top to release it.",
            expectBlocked = true,
            expectReply = false
        ),

        // --- Noise: neither blocked nor replied to ------------------------------------------
        SampleMessage(
            id = "marketing-stop",
            title = "Shoe sale with an opt-out",
            sender = "SHOES",
            body = "SALE: 50% off all shoes this weekend. Reply STOP to unsubscribe.",
            expectBlocked = false,
            expectReply = false
        ),
        SampleMessage(
            id = "wrong-number-vote",
            title = "Wrong number that mentions a vote",
            sender = "+15550004444",
            body = "Hey, are you coming to the vote counting party tonight?",
            expectBlocked = false,
            expectReply = true
        )
    )

    /** The same verdict the rule-based tier would produce, so the lab and tests agree. */
    fun verdict(sample: SampleMessage): FilterResult =
        CustomFilter.filterMessage(
            message = sample.body,
            sender = sample.sender,
            isUnderAttackMode = false,
            customKeywords = emptyList(),
            customRegexPatterns = emptyList()
        )

    fun context(sample: SampleMessage, resolvedHasOtp: Boolean = sample.hasOtp): MessageContext {
        val verdict = verdict(sample)
        return MessageContext(
            sender = sample.sender,
            body = sample.body,
            timestamp = 0L,
            category = verdict.category,
            isBlocked = verdict.isBlocked,
            isContact = sample.fromContact,
            hasOutboundHistory = sample.hasOutboundHistory,
            hasOtp = resolvedHasOtp,
            hasOptOutDirective = com.ovehbe.junkboy.filters.Stop2EndDetector.detect(sample.body) !=
                com.ovehbe.junkboy.filters.Stop2EndDetector.Directive.NONE,
            allowedBySender = false,
            allowedByContent = false
        )
    }

    fun run(pipeline: Pipeline, otpResolver: (SampleMessage) -> Boolean = { it.hasOtp }): List<SampleResult> =
        all.map { sample ->
            val context = context(sample, otpResolver(sample))
            SampleResult(
                sample = sample,
                evaluation = PipelineRunner.evaluate(pipeline, context),
                verdict = verdict(sample)
            )
        }
}
