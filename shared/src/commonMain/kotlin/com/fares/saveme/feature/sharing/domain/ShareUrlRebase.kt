package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.core.config.AppConfig

/**
 * The server builds a one-time link from its own public URL, which is not this build's: the flavor decides the scheme, host
 * and port of links the app can open (see [InvitationLinkParser]). Keeps the server's path, query and fragment, swaps the
 * authority. A value without a scheme is returned as is. Plain string handling (P-10.3).
 */
fun interface ShareUrlRebase {
    fun rebase(url: String): String

    companion object {
        val None = ShareUrlRebase { it }

        fun of(config: AppConfig): ShareUrlRebase = ShareUrlRebase { url ->
            val schemeEnd = url.indexOf("://").takeIf { it > 0 } ?: return@ShareUrlRebase url
            val rest = url.substring(schemeEnd + 3)
            val pathStart = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.takeIf { it >= 0 } ?: rest.length
            val port = config.linkPort?.let { ":$it" }.orEmpty()
            "${config.linkScheme}://${config.linkHost}$port${rest.substring(pathStart)}"
        }
    }
}
