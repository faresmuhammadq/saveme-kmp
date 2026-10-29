package com.fares.saveme.feature.server

enum class ServerTestResult { Idle, Testing, Reachable, Unreachable }

data class ServerAddressState(
    val input: String = "",
    val default: String = "",
    val invalid: Boolean = false,
    val test: ServerTestResult = ServerTestResult.Idle,
)

sealed interface ServerAddressIntent {
    data class InputChanged(val value: String) : ServerAddressIntent
    data object TestClicked : ServerAddressIntent
    data object SaveClicked : ServerAddressIntent
    data object ResetClicked : ServerAddressIntent
    data object Back : ServerAddressIntent
}

sealed interface ServerAddressEffect {
    data object NavigateBack : ServerAddressEffect
}
