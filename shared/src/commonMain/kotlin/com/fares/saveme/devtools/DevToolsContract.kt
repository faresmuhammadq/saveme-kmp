package com.fares.saveme.devtools

/** Development-only menu; nothing to hold, so the state is empty (P-16.1: still one store). */
data object DevToolsState

sealed interface DevToolsIntent {
    data object ComponentsClicked : DevToolsIntent
    data object PasteLinkClicked : DevToolsIntent
    data object Back : DevToolsIntent
}

sealed interface DevToolsEffect {
    data object NavigateCatalog : DevToolsEffect
    data object NavigatePasteLink : DevToolsEffect
    data object NavigateBack : DevToolsEffect
}
