package com.fares.saveme.feature.recurring.ui.editor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fares.saveme.feature.recurring.domain.model.RulePrefill
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.recurring_readonly_ended
import saveme.shared.generated.resources.recurring_reason_gone
import saveme.shared.generated.resources.recurring_reason_no_right

/**
 * [ruleId] null creates a rule in [walletId] (the default wallet when null; [prefill] comes from the transaction editor);
 * otherwise the rule is edited, or edited from a date when [fromDate]. [onRequireVerification] shows the verify prompt.
 */
@Composable
fun RuleEditorRoute(
    walletId: String?,
    ruleId: String?,
    fromDate: Boolean,
    prefill: RulePrefill?,
    onEffect: (RuleEditorEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: RuleEditorViewModel = koinViewModel(key = "rule-editor-${ruleId.orEmpty()}-${walletId.orEmpty()}-$fromDate-${prefill != null}") {
        parametersOf(RuleEditorArgs(walletId, ruleId, fromDate, prefill))
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RuleEditorEffect.ShowMessage -> scope.launch {
                    snackbar.showSnackbar(
                        getString(
                            when (effect.message) {
                                EditorMessage.Gone -> Res.string.recurring_reason_gone
                                EditorMessage.NotAllowed -> Res.string.recurring_reason_no_right
                                EditorMessage.ReadOnly -> Res.string.recurring_readonly_ended
                            },
                        ),
                    )
                }
                RuleEditorEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    RuleEditorScreen(state, viewModel::onIntent, snackbar)
}
