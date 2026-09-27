package com.fares.saveme.core.platform

import android.os.Build
import java.util.Locale
import java.util.TimeZone

class AndroidClientInfo(override val appVersion: String) : ClientInfo {
    override val platform = "android"
    override val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100)
    override val liveApiSupported = true

    override fun timezoneId(): String = TimeZone.getDefault().id

    override fun languageTag(): String = supportedLanguage(Locale.getDefault().language)
}
