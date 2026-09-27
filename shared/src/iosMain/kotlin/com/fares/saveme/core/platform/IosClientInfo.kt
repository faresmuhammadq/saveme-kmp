package com.fares.saveme.core.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localTimeZone
import platform.UIKit.UIDevice

class IosClientInfo : ClientInfo {
    override val platform = "ios"
    override val appVersion: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0.0.0"
    override val deviceName: String = UIDevice.currentDevice.name.take(100)

    // The API accepts only `android` today; iOS sign-in is out of scope (P-15).
    override val liveApiSupported = false

    override fun timezoneId(): String = NSTimeZone.localTimeZone.name

    override fun languageTag(): String = supportedLanguage(NSLocale.currentLocale.languageCode)
}
