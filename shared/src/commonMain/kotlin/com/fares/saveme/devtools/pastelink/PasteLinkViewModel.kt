package com.fares.saveme.devtools.pastelink

import com.fares.saveme.core.presentation.MviViewModel

class PasteLinkViewModel(
    private val isAuthLink: (String) -> Boolean,
) : MviViewModel<PasteLinkState, PasteLinkIntent, PasteLinkEffect>(PasteLinkState()) {

    override fun onIntent(intent: PasteLinkIntent) {
        when (intent) {
            is PasteLinkIntent.TextChanged -> setState { reduce(it, PasteLinkChange.Edited(intent.value)) }
            PasteLinkIntent.OpenClicked -> {
                val text = currentState.text.trim()
                if (isAuthLink(text)) sendEffect(PasteLinkEffect.SubmitLink(text)) else setState { reduce(it, PasteLinkChange.Rejected) }
            }
            PasteLinkIntent.Back -> sendEffect(PasteLinkEffect.NavigateBack)
        }
    }
}
