package com.fares.saveme.devtools.push

/** The development-only tool that hands a message to the same path as a real foreground one (FR-036). */
data class SimulatePushState(
    val rows: List<SimulateRow> = emptyList(),
    val posted: String? = null,
    val rotated: Boolean = false,
)

sealed interface SimulatePushIntent {
    data object Opened : SimulatePushIntent
    data class Post(val type: String) : SimulatePushIntent
    data object Rotate : SimulatePushIntent
    data object Back : SimulatePushIntent
}

sealed interface SimulatePushEffect {
    data object NavigateBack : SimulatePushEffect
}

sealed interface SimulatePushChange {
    data class Rows(val rows: List<SimulateRow>) : SimulatePushChange
    data class Posted(val type: String) : SimulatePushChange
    data object Rotated : SimulatePushChange
}

fun reduce(state: SimulatePushState, change: SimulatePushChange): SimulatePushState = when (change) {
    is SimulatePushChange.Rows -> state.copy(rows = change.rows)
    is SimulatePushChange.Posted -> state.copy(posted = change.type, rotated = false)
    SimulatePushChange.Rotated -> state.copy(rotated = true, posted = null)
}
