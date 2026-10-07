package com.fares.saveme.feature.billing.domain.play

/** Links into Google Play's own subscription centre (spec 018 R-8, FR-054). They start no payment in the app. */
object PlayStoreLinks {
    private const val BASE = "https://play.google.com/store/account/subscriptions"

    /** With `package` (omitted only where the app has none, e.g. tests) and, when the product is known, `sku`. */
    fun subscriptions(applicationId: String, productId: String? = null): String {
        val query = buildList {
            if (applicationId.isNotBlank()) add("package=${encode(applicationId)}")
            if (!productId.isNullOrBlank()) add("sku=${encode(productId)}")
        }
        return if (query.isEmpty()) BASE else "$BASE?${query.joinToString("&")}"
    }

    private fun encode(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val v = byte.toInt() and 0xff
            val c = v.toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~') {
                append(c)
            } else {
                append('%').append("0123456789ABCDEF"[v shr 4]).append("0123456789ABCDEF"[v and 0x0f])
            }
        }
    }
}
