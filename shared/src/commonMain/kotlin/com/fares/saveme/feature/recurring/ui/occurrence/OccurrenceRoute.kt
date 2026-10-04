package com.fares.saveme.feature.recurring.ui.occurrence

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.recurring_confirmed_body
import saveme.shared.generated.resources.recurring_reason_no_right
import saveme.shared.generated.resources.recurring_reason_occurrence_gone

/** Which occurrence to open, and whether its confirm form opens at once; one object so Koin never confuses two values. */
data class OccurrenceArgs(val occurrenceId: String, val confirm: Boolean)

@Composable
fun OccurrenceRoute(
    occurrenceId: String,
    confirm: Boolean,
    onEffect: (OccurrenceEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: OccurrenceViewModel = koinViewModel(key = "occurrence-$occurrenceId-$confirm") {
        parametersOf(OccurrenceArgs(occurrenceId, confirm))
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OccurrenceEffect.ShowMessage -> scope.launch {
                    snackbar.showSnackbar(
                        getString(
                            when (effect.message) {
                                OccurrenceMessage.Gone -> Res.string.recurring_reason_occurrence_gone
                                OccurrenceMessage.NotAllowed -> Res.string.recurring_reason_no_right
                                OccurrenceMessage.ConfirmedFinal -> Res.string.recurring_confirmed_body
                            },
                        ),
                    )
                }
                OccurrenceEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    OccurrenceScreen(state, viewModel::onIntent, snackbar)
}
