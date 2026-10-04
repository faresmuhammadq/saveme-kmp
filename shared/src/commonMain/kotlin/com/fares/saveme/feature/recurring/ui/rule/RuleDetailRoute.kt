package com.fares.saveme.feature.recurring.ui.rule

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
import saveme.shared.generated.resources.recurring_reason_gone
import saveme.shared.generated.resources.recurring_reason_no_right
import saveme.shared.generated.resources.recurring_readonly_ended

@Composable
fun RuleDetailRoute(
    ruleId: String,
    onEffect: (RuleDetailEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: RuleDetailViewModel = koinViewModel(key = "rule-$ruleId") { parametersOf(RuleDetailArgs(ruleId)) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RuleDetailEffect.ShowMessage -> scope.launch {
                    snackbar.showSnackbar(
                        getString(
                            when (effect.message) {
                                RuleDetailMessage.Gone -> Res.string.recurring_reason_gone
                                RuleDetailMessage.NotAllowed -> Res.string.recurring_reason_no_right
                                RuleDetailMessage.ReadOnly -> Res.string.recurring_readonly_ended
                            },
                        ),
                    )
                }
                RuleDetailEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    RuleDetailScreen(state, viewModel::onIntent, snackbar)
}
