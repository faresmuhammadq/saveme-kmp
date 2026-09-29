package com.fares.saveme.core.platform

import android.os.Build
import android.content.res.Resources
import java.util.TimeZone

class AndroidClientInfo(override val appVersion: String) : ClientInfo {
    override val platform = "android"
    override val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100)
    override val liveApiSupported = true

    override fun timezoneId(): String = TimeZone.getDefault().id

    // The system locale, not Locale.getDefault(): the app-language override rewrites the default (R-06).
    override fun languageTag(): String = supportedLanguage(Resources.getSystem().configuration.locales[0].language)
}
