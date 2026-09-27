package com.fares.saveme.core.platform

/** What this install says about itself on every request and at sign-in. */
interface ClientInfo {
    val platform: String
    val appVersion: String
    val deviceName: String

    /** False on iOS until the API accepts an `ios` platform (constitution P-15). */
    val liveApiSupported: Boolean

    fun timezoneId(): String

    /** `ar` or `en`; anything else is reported as `en`. */
    fun languageTag(): String
}

internal fun supportedLanguage(tag: String?): String =
    if (tag != null && tag.lowercase().startsWith("ar")) "ar" else "en"
