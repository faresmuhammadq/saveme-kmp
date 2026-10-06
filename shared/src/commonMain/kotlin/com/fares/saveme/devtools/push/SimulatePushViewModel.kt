package com.fares.saveme.devtools.push

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Builds the contract's data map for a type, with ids from the phone where there are any, and gives it with fixed
 * generic text to [post] (the receiver's own entry point, the path a real foreground message takes).
 */
class SimulatePushViewModel(
    private val phoneIds: suspend () -> PhoneIds,
    /** The fixed generic title and body in the app language (FR-036). */
    private val texts: suspend () -> Pair<String, String>,
    private val post: suspend (Map<String, String>, String, String) -> Unit,
    private val rotate: suspend () -> Unit,
    private val randomId: () -> String = { Uuid.random().toString() },
) : MviViewModel<SimulatePushState, SimulatePushIntent, SimulatePushEffect>(SimulatePushState()) {

    override fun onIntent(intent: SimulatePushIntent) {
        when (intent) {
            SimulatePushIntent.Opened -> viewModelScope.launch {
                val rows = SimulatePayloads.rows(phoneIds())
                setState { reduce(it, SimulatePushChange.Rows(rows)) }
            }
            is SimulatePushIntent.Post -> viewModelScope.launch {
                val (title, body) = texts()
                post(SimulatePayloads.build(intent.type, phoneIds(), randomId), title, body)
                setState { reduce(it, SimulatePushChange.Posted(intent.type)) }
            }
            SimulatePushIntent.Rotate -> viewModelScope.launch {
                rotate()
                setState { reduce(it, SimulatePushChange.Rotated) }
            }
            SimulatePushIntent.Back -> sendEffect(SimulatePushEffect.NavigateBack)
        }
    }
}
