package com.ovehbe.junkboy.pipeline

/**
 * Curated, ready-to-add pipeline steps. Each [yaml] is a single step template; the suggestions
 * screen parses it, previews the effect on the 20-message fixture, and only saves it when the
 * user approves that card.
 */
data class SuggestedRule(
    val id: String,
    val name: String,
    val summary: String,
    val tags: List<String>,
    val yaml: String
) {
    fun toStep(): Step = PipelineYaml.importStep(yaml)
}

object SuggestedRules {

    val all: List<SuggestedRule> = listOf(
        SuggestedRule(
            id = "political-stop",
            name = "Political STOP",
            summary = "Reply STOP to political fundraising and campaign texts from senders you have " +
                "not texted. Add more words if your state or party uses others.",
            tags = listOf("reply", "politics"),
            yaml = """
                name: Political STOP
                match: all
                checks:
                  - text_matches: [democrat, republican, gop, senate, campaign, vote, election, candidate]
                  - is_contact: false
                  - has_otp: false
                  - has_outbound_history: false
                actions:
                  - reply_stop
            """.trimIndent()
        ),
        SuggestedRule(
            id = "parcel-fee",
            name = "Parcel fee scam",
            summary = "Block texts that claim a parcel is held for a fee. The regex catches " +
                "variations such as \u201cyour package needs a customs fee\u201d.",
            tags = listOf("block", "scam"),
            yaml = """
                name: Parcel fee scam
                match: any
                checks:
                  - text_matches: [parcel is held, redelivery fee, customs fee, pay a small fee]
                  - text_matches:
                      - regex: '(?i)\b(parcel|package)\b.{0,40}\b(fee|held|customs)\b'
                actions:
                  - block
            """.trimIndent()
        ),
        SuggestedRule(
            id = "crypto-doubling",
            name = "Crypto doubling scam",
            summary = "Block the classic \u201cdouble your money\u201d pitch and its common variants. " +
                "Whole phrases keep it away from ordinary finance texts.",
            tags = listOf("block", "scam"),
            yaml = """
                name: Crypto doubling scam
                match: any
                checks:
                  - text_matches: [double your money, double your bitcoin, guaranteed profit, send btc, send bitcoin]
                actions:
                  - block
            """.trimIndent()
        ),
        SuggestedRule(
            id = "lottery-prize",
            name = "Lottery prize scam",
            summary = "Block lottery and prize claims, including the \u201cclaim your winnings\u201d " +
                "phrasing. Order this after any legitimate competition you actually entered.",
            tags = listOf("block", "scam"),
            yaml = """
                name: Lottery prize scam
                match: any
                checks:
                  - text_matches: [you won, claim your prize, lottery winner, congratulations you won]
                  - text_matches:
                      - regex: '(?i)\b(claim|collect)\b.{0,30}\b(prize|winnings)\b'
                actions:
                  - block
            """.trimIndent()
        ),
        SuggestedRule(
            id = "bank-phishing",
            name = "Bank phishing link",
            summary = "Block a link combined with account-pressure language. Banks do send links, " +
                "so keep allowed senders and test this one against your own bank first.",
            tags = listOf("block", "phishing"),
            yaml = """
                name: Bank phishing link
                match: all
                checks:
                  - text_matches:
                      - regex: '(?i)https?://\S+'
                  - text_matches: [verify your account, account suspended, unlock your account, confirm your identity]
                  - allowed_sender: false
                actions:
                  - block
            """.trimIndent()
        ),
        SuggestedRule(
            id = "unsubscribe-marketing",
            name = "Unsubscribe marketing",
            summary = "Reply STOP to promotional texts that offer an opt-out, without blocking them. " +
                "Requires the promotion category, so appointment and bank texts are skipped.",
            tags = listOf("reply", "marketing"),
            yaml = """
                name: Unsubscribe marketing
                match: all
                checks:
                  - category_is: [promotion]
                  - has_opt_out_directive: true
                  - is_contact: false
                  - has_otp: false
                  - has_outbound_history: false
                actions:
                  - reply_stop
            """.trimIndent()
        )
    )

    fun byId(id: String): SuggestedRule? = all.firstOrNull { it.id == id }
}
