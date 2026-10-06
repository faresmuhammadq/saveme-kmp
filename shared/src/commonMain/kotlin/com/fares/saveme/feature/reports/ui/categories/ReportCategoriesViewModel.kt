package com.fares.saveme.feature.reports.ui.categories

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.categories.domain.model.ReportLabels
import com.fares.saveme.feature.reports.domain.model.ReportState
import com.fares.saveme.feature.reports.ui.reports.CategoryGroupKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** The full category list of the report just shown, from the same phone figures (P-16); it closes when its wallet is purged. */
class ReportCategoriesViewModel(
    initialKind: CategoryGroupKind,
    report: Flow<ReportState>,
    labels: Flow<ReportLabels>,
) : MviViewModel<ReportCategoriesState, ReportCategoriesIntent, ReportCategoriesEffect>(ReportCategoriesState(kind = initialKind)) {

    init {
        combine(report, labels) { stored, names -> ReportCategoriesChange.Stored(stored, names) }
            .onEach { change ->
                setState { reduce(it, change) }
                if (change.state == ReportState.WalletGone) sendEffect(ReportCategoriesEffect.Close)
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: ReportCategoriesIntent) {
        when (intent) {
            is ReportCategoriesIntent.KindPicked -> setState { reduce(it, ReportCategoriesChange.Kind(intent.kind)) }
            ReportCategoriesIntent.BackClicked -> sendEffect(ReportCategoriesEffect.Close)
        }
    }
}
