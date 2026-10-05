package com.fares.saveme.feature.sharing.domain

/**
 * The text shared with a link (spec 013 FR-006): the app's display name, a fixed sentence in the app's language and the link.
 * Never the wallet's name, an amount or a balance - the function takes none. [template] has `%1$s` for the app name and `%2$s`
 * for the link (the string resource `invite_share_text`).
 */
object ShareText {
    fun build(appName: String, url: String, template: String): String =
        template.replace("%1\$s", appName).replace("%2\$s", url)
}
