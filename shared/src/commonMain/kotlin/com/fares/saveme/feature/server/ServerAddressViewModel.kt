package com.fares.saveme.feature.server

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.config.ServerAddress
import com.fares.saveme.core.config.ServerAddressSettings
import com.fares.saveme.core.presentation.MviViewModel
import kotlinx.coroutines.launch

/** [probe] answers whether `GET http://<host:port>/up` succeeds. */
class ServerAddressViewModel(
    private val settings: ServerAddressSettings,
    private val probe: suspend (String) -> Boolean,
) : MviViewModel<ServerAddressState, ServerAddressIntent, ServerAddressEffect>(
    ServerAddressState(input = settings.current(), default = settings.default),
) {

    override fun onIntent(intent: ServerAddressIntent) {
        when (intent) {
            is ServerAddressIntent.InputChanged ->
                setState { it.copy(input = intent.value, invalid = false, test = ServerTestResult.Idle) }
            ServerAddressIntent.TestClicked -> test()
            ServerAddressIntent.SaveClicked -> ServerAddress.parse(currentState.input)?.let {
                settings.save(it.hostPort)
                settings.restartApp()
            } ?: setState { it.copy(invalid = true) }
            ServerAddressIntent.ResetClicked -> {
                settings.reset()
                settings.restartApp()
            }
            ServerAddressIntent.Back -> sendEffect(ServerAddressEffect.NavigateBack)
        }
    }

    private fun test() {
        val address = ServerAddress.parse(currentState.input)
        if (address == null) {
            setState { it.copy(invalid = true) }
            return
        }
        setState { it.copy(invalid = false, test = ServerTestResult.Testing) }
        viewModelScope.launch {
            val reachable = runCatching { probe(address.hostPort) }.getOrDefault(false)
            setState { it.copy(test = if (reachable) ServerTestResult.Reachable else ServerTestResult.Unreachable) }
        }
    }
}
