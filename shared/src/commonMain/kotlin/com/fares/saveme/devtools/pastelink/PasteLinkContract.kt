package com.fares.saveme.devtools.pastelink

/**
 * Dev-only (FR-008). The field may show the pasted link, a dev-only exemption in P-09.2 (v1.3.0);
 * the text is never logged or saved, so [toString] hides it.
 */
data class PasteLinkState(val text: String = "", val parseError: Boolean = false) {
    override fun toString() = "PasteLinkState(text=<redacted>, parseError=$parseError)"
}

sealed interface PasteLinkIntent {
    data class TextChanged(val value: String) : PasteLinkIntent
    data object OpenClicked : PasteLinkIntent
    data object Back : PasteLinkIntent
}

sealed interface PasteLinkEffect {
    /** The route hands the raw link to the deep-link bus, the same path a tapped link takes. */
    data class SubmitLink(val uri: String) : PasteLinkEffect {
        override fun toString() = "SubmitLink(uri=<redacted>)"
    }
    data object NavigateBack : PasteLinkEffect
}

sealed interface PasteLinkChange {
    data class Edited(val value: String) : PasteLinkChange
    data object Rejected : PasteLinkChange
}

fun reduce(state: PasteLinkState, change: PasteLinkChange): PasteLinkState = when (change) {
    is PasteLinkChange.Edited -> state.copy(text = change.value, parseError = false)
    PasteLinkChange.Rejected -> state.copy(parseError = true)
}
