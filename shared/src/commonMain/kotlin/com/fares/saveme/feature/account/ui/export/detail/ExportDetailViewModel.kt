package com.fares.saveme.feature.account.ui.export.detail

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.account.domain.export.ExportStatus
import com.fares.saveme.feature.account.domain.export.LinkOutcome
import com.fares.saveme.feature.account.domain.export.WatchEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * One export's status and download (spec 016 FR-010 to FR-016). Reads start when the screen is in the foreground and stop when
 * it leaves, on sign-out, and by the service's own end conditions. "Open download" reads a fresh link for each tap and hands it
 * to the screen in one Effect; the link is never kept (P-09.2). Domain lambdas only (P-16.3).
 */
class ExportDetailViewModel(
    private val exportId: String,
    private val watch: (id: String, windowStart: Instant?) -> Flow<WatchEvent>,
    private val openableLink: suspend (id: String) -> LinkOutcome,
    walletNames: Flow<Map<String, String>> = emptyFlow(),
    signedIn: Flow<Boolean> = emptyFlow(),
    timeZoneId: Flow<String> = emptyFlow(),
    private val now: () -> Instant = { Clock.System.now() },
) : MviViewModel<ExportDetailState, ExportDetailIntent, ExportDetailEffect>(ExportDetailState()) {

    private var watchJob: Job? = null
    private var isSignedIn = true

    init {
        walletNames.onEach { names -> setState { reduce(it, ExportDetailChange.WalletNames(names)) } }.launchIn(viewModelScope)
        timeZoneId.onEach { id -> setState { reduce(it, ExportDetailChange.TimeZone(id)) } }.launchIn(viewModelScope)
        signedIn.onEach { signed ->
            isSignedIn = signed
            if (!signed) {
                watchJob?.cancel()
                setState { reduce(it, ExportDetailChange.SignedOut) }
            }
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: ExportDetailIntent) {
        when (intent) {
            ExportDetailIntent.Started -> startWatching(windowStart = null)
            ExportDetailIntent.Stopped -> stopWatching()
            // A deliberate check begins a new 10-minute window (R-03).
            ExportDetailIntent.CheckAgain -> startWatching(windowStart = now())
            ExportDetailIntent.OpenDownload -> openDownload()
            ExportDetailIntent.OpenFailed -> setState { reduce(it, ExportDetailChange.Problem(ExportDetailMessage.OpenFailed)) }
            ExportDetailIntent.RequestAgain -> sendEffect(ExportDetailEffect.OpenRequest(currentState.export?.presetWalletId()))
            ExportDetailIntent.Back -> sendEffect(ExportDetailEffect.NavigateBack)
        }
    }

    private fun startWatching(windowStart: Instant?) {
        if (!isSignedIn) return
        watchJob?.cancel()
        setState { reduce(it, ExportDetailChange.Polling(true)) }
        watchJob = viewModelScope.launch {
            try {
                watch(exportId, windowStart).collect { event ->
                    when (event) {
                        is WatchEvent.Status -> setState { reduce(it, ExportDetailChange.Status(event.export)) }
                        is WatchEvent.StillPreparing -> setState { reduce(it, ExportDetailChange.StillPreparing(event.export)) }
                        WatchEvent.NotFound -> setState { reduce(it, ExportDetailChange.Problem(ExportDetailMessage.NotFound)) }
                        WatchEvent.Offline -> setState { reduce(it, ExportDetailChange.Problem(ExportDetailMessage.Offline)) }
                        is WatchEvent.Failed ->
                            setState { reduce(it, ExportDetailChange.Problem(ExportDetailMessage.Error(event.code, event.requestId))) }
                    }
                }
            } finally {
                setState { reduce(it, ExportDetailChange.Polling(false)) }
            }
        }
    }

    private fun stopWatching() {
        watchJob?.cancel()
        setState { reduce(it, ExportDetailChange.Polling(false)) }
    }

    private fun openDownload() {
        val state = currentState
        if (state.opening || state.status != ExportStatus.Ready) return
        setState { reduce(it, ExportDetailChange.Opening(true)) }
        viewModelScope.launch {
            when (val outcome = openableLink(exportId)) {
                is LinkOutcome.Open -> {
                    setState { reduce(it, ExportDetailChange.Opening(false)) }
                    sendEffect(ExportDetailEffect.OpenDownload(outcome.link))
                }
                is LinkOutcome.NotReady -> {
                    setState { reduce(it, ExportDetailChange.Status(outcome.export)) }
                    setState { reduce(it, ExportDetailChange.Opening(false)) }
                    // Ready without a link: nothing opens; the screen keeps "Ready" and asks for another tap.
                    if (outcome.export.status == ExportStatus.Ready) {
                        setState { reduce(it, ExportDetailChange.Problem(ExportDetailMessage.LinkMissing)) }
                    }
                }
                LinkOutcome.NotFound -> setState { reduce(it, ExportDetailChange.Problem(ExportDetailMessage.NotFound)) }
                LinkOutcome.Offline -> setState { reduce(it, ExportDetailChange.Problem(ExportDetailMessage.Offline)) }
                is LinkOutcome.Failed ->
                    setState { reduce(it, ExportDetailChange.Problem(ExportDetailMessage.Error(outcome.code, outcome.requestId))) }
            }
        }
    }
}
