package com.fares.saveme.feature.account.domain.export

import kotlin.jvm.JvmInline

/**
 * The signed, 15-minute link to an export's ZIP archive. It is a bearer secret to the person's history (P-09.2): it exists only
 * between a fresh read and the browser hand-off, in one one-shot Effect. It is never stored, shown, copied, shared or logged,
 * so it prints redacted wherever it lands.
 */
@JvmInline
value class DownloadLink(val value: String) {
    override fun toString() = "DownloadLink(***)"
}
