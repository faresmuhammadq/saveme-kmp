package com.fares.saveme.feature.sharing.domain

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/** Collects every Kermit line while open, so tests can assert nothing secret was logged (P-09.2). */
class LogCapture : LogWriter() {
    val lines = mutableListOf<String>()
    private val previous = Logger.config.logWriterList.toList()

    init {
        Logger.setLogWriters(this)
        Logger.setMinSeverity(Severity.Verbose)
    }

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        lines += "$severity $tag $message ${throwable?.message.orEmpty()}"
    }

    fun close() {
        Logger.setLogWriters(previous)
    }
}
