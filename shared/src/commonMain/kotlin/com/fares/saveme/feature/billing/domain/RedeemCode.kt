package com.fares.saveme.feature.billing.domain

/**
 * The mailed redeem code: 16 characters of the Crockford base32 alphabet shown as four groups of four.
 * Folding matches the server's, so a code the field accepts is never a format refusal (spec 015 R-02).
 */
object RedeemCode {
    const val ALPHABET: String = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH: Int = 16
    private const val GROUP = 4

    private val hyphenated = Regex("(?i)\\b([0-9A-Z]{4})-([0-9A-Z]{4})-([0-9A-Z]{4})-([0-9A-Z]{4})\\b")
    private val spaced = Regex("(?i)\\b([0-9A-Z]{4})[- ]([0-9A-Z]{4})[- ]([0-9A-Z]{4})[- ]([0-9A-Z]{4})\\b")

    /**
     * Upper-case, `O` to `0`, `I` and `L` to `1`; anything outside [ALPHABET] is dropped (spaces, hyphens, line
     * breaks, `U`, punctuation, non-ASCII letters and digits); cut to [LENGTH].
     */
    fun normalise(input: String): String {
        val out = StringBuilder()
        for (char in input) {
            val folded = when (val upper = if (char in 'a'..'z') char - 32 else char) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> upper
            }
            if (folded in ALPHABET) {
                out.append(folded)
                if (out.length == LENGTH) break
            }
        }
        return out.toString()
    }

    /** The first grouped code in [text] (a whole line of the email), else [normalise] of the whole text. */
    fun fromPaste(text: String): String {
        // The mailed form (hyphens) first, so ordinary words separated by spaces are never taken for a code.
        for (pattern in listOf(hyphenated, spaced)) {
            for (match in pattern.findAll(text)) {
                val candidate = normalise(match.value)
                if (candidate.length == LENGTH) return candidate
            }
        }
        return normalise(text)
    }

    /** `XXXX-XXXX-XXXX-XXXX` for a complete [raw] code; a partial one is grouped as far as it goes. */
    fun grouped(raw: String): String = raw.chunked(GROUP).joinToString("-")

    fun isComplete(raw: String): Boolean = raw.length == LENGTH
}
