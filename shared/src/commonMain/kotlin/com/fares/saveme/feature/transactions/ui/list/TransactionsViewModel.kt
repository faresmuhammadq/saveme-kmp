package com.fares.saveme.feature.transactions.ui.list

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.transactions.domain.ConfirmationStatus
import com.fares.saveme.feature.transactions.domain.DueItem
import com.fares.saveme.feature.transactions.domain.TransactionFilter
import com.fares.saveme.feature.transactions.domain.model.TransactionList
import com.fares.saveme.feature.transactions.domain.model.TransactionQuery
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * One wallet's transactions: always the phone's copy (P-04.1), read again from the server on open,
 * on pull and when the screen returns to the foreground (FR-018). Search and filters run on the
 * phone through the domain's list flow (FR-027, FR-028). Domain lambdas only (P-16.3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val walletId: String,
    wallet: Flow<WalletView?>,
    list: (TransactionQuery) -> Flow<TransactionList>,
    displays: Flow<CategoryDisplays>,
    filterChoices: Flow<List<CategoryDisplay>>,
    private val refresh: suspend (Boolean) -> RefreshOutcome,
    private val isEmailVerified: () -> Boolean,
    private val today: () -> LocalDate,
    timeZoneId: Flow<String> = emptyFlow(),
    due: Flow<List<DueItem>> = flowOf(emptyList()),
    private val confirmationStatus: (String) -> Flow<ConfirmationStatus?> = { flowOf(null) },
    planLimited: Flow<Boolean> = flowOf(false),
) : MviViewModel<TransactionsState, TransactionsIntent, TransactionsEffect>(TransactionsState(walletId = walletId)) {

    private val query = MutableStateFlow(TransactionQuery())

    /** The category ids of the wallet's transactions, so the filter lists them even when they are not choosable any more. */
    private var usedCategoryIds: Set<String> = emptySet()
    private var choosable: List<CategoryDisplay> = emptyList()
    private var latestDisplays: CategoryDisplays? = null

    init {
        setState { reduce(it, TransactionsChange.Today(today())) }
        wallet.onEach { view -> setState { reduce(it, TransactionsChange.Wallet(view)) } }.launchIn(viewModelScope)
        planLimited.onEach { limited -> setState { reduce(it, TransactionsChange.PlanLimited(limited)) } }.launchIn(viewModelScope)
        due.onEach { items -> setState { reduce(it, TransactionsChange.Due(items)) } }.launchIn(viewModelScope)
        combine(query.flatMapLatest { q -> list(q).flatMapLatest { stored -> confirmations(stored).map { stored to it } } }, displays) { (stored, statuses), shown ->
            Triple(stored, shown, statuses)
        }
            .onEach { (stored, shown, statuses) ->
                latestDisplays = shown
                usedCategoryIds = stored.categoryIds
                val days = stored.days.map { day ->
                    DayUi(day.date, day.netMinor, day.items.map { view -> view.toRow(view.shown.categoryId?.let { shown.resolve(it) }, statuses[view.id]) })
                }
                setState { reduce(it, TransactionsChange.Data(stored, days)) }
                publishFilterChoices()
            }
            .launchIn(viewModelScope)
        filterChoices.onEach { choices ->
            choosable = choices
            publishFilterChoices()
        }.launchIn(viewModelScope)
        timeZoneId.onEach { id -> setState { reduce(it, TransactionsChange.TimeZone(id)) } }.launchIn(viewModelScope)
    }

    /** The occurrence status of each phone-only pending confirmation among [stored] (spec 010 R-07). */
    private fun confirmations(stored: TransactionList): Flow<Map<String, ConfirmationStatus>> {
        val ids = stored.days.flatMap { day -> day.items.filter { it.pendingConfirmation }.map { it.id } }
        if (ids.isEmpty()) return flowOf(emptyMap())
        return combine(ids.map { id -> confirmationStatus(id).map { id to it } }) { pairs ->
            pairs.mapNotNull { (id, status) -> status?.let { id to it } }.toMap()
        }
    }

    /** What the wallet's transactions use plus what the person can choose now, once each, by name (research R-12). */
    private fun publishFilterChoices() {
        val used = usedCategoryIds.mapNotNull { id -> latestDisplays?.resolve(id) }
        val all = (choosable + used).distinctBy { it.id }.sortedBy { it.name.lowercase() }
        setState { reduce(it, TransactionsChange.FilterChoices(all)) }
    }

    override fun onIntent(intent: TransactionsIntent) {
        when (intent) {
            TransactionsIntent.Opened, TransactionsIntent.Foregrounded -> pull(force = false)
            TransactionsIntent.PullToRefresh -> pull(force = true)
            is TransactionsIntent.SearchChanged -> {
                val text = intent.text.take(TransactionFilter.MAX_SEARCH)
                setQuery(currentState.query.copy(search = text), text)
            }
            TransactionsIntent.FiltersOpened -> setState { reduce(it, TransactionsChange.FilterSheet(true)) }
            TransactionsIntent.FiltersDismissed -> setState { reduce(it, TransactionsChange.FilterSheet(false)) }
            TransactionsIntent.ThisMonth -> presetRange(currentState.today ?: today(), last = false).let { (from, to) -> setQuery(currentState.query.withRange(from, to)) }
            TransactionsIntent.LastMonth -> presetRange(currentState.today ?: today(), last = true).let { (from, to) -> setQuery(currentState.query.withRange(from, to)) }
            is TransactionsIntent.DateRange -> setQuery(currentState.query.withRange(intent.from, intent.to))
            is TransactionsIntent.CategoryToggled -> {
                val ids = currentState.query.categoryIds
                setQuery(currentState.query.copy(categoryIds = if (intent.id in ids) ids - intent.id else ids + intent.id))
            }
            is TransactionsIntent.KindToggled -> {
                val kinds = currentState.query.kinds
                setQuery(currentState.query.copy(kinds = if (intent.kind in kinds) kinds - intent.kind else kinds + intent.kind))
            }
            is TransactionsIntent.ChipCleared -> setQuery(currentState.query.without(intent.chip))
            TransactionsIntent.ClearAll -> setQuery(TransactionQuery(), "")
            is TransactionsIntent.RowClicked -> {
                val occurrenceId = currentState.days.firstNotNullOfOrNull { day -> day.rows.firstOrNull { it.id == intent.id } }?.occurrenceId
                if (occurrenceId != null) sendEffect(TransactionsEffect.OpenOccurrence(occurrenceId))
                else sendEffect(TransactionsEffect.OpenEditor(intent.id, walletId))
            }
            is TransactionsIntent.DueClicked -> sendEffect(TransactionsEffect.OpenOccurrence(intent.id))
            TransactionsIntent.RecurringClicked -> sendEffect(TransactionsEffect.OpenRecurring(walletId))
            TransactionsIntent.AddClicked ->
                if (!currentState.canAdd) Unit
                else if (isEmailVerified()) sendEffect(TransactionsEffect.OpenEditor(null, walletId))
                else sendEffect(TransactionsEffect.RequireVerification)
            TransactionsIntent.Back -> sendEffect(TransactionsEffect.Back)
        }
    }

    private fun setQuery(next: TransactionQuery, searchText: String = currentState.searchText) {
        query.value = next
        setState { reduce(it, TransactionsChange.Query(next, searchText)) }
    }

    private fun pull(force: Boolean) {
        if (currentState.refreshing) return
        setState { reduce(it, TransactionsChange.Refreshing(true)) }
        viewModelScope.launch {
            val outcome = refresh(force)
            setState { reduce(it, TransactionsChange.RefreshDone(outcome)) }
        }
    }
}
