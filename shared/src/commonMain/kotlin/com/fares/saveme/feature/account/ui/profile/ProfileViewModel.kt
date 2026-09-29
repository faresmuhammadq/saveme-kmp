package com.fares.saveme.feature.account.ui.profile

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.auth.domain.ClientValidation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ProfileViewModel(
    account: Flow<Pair<String, Boolean>>,
    profile: Flow<ProfileView?>,
    private val refresh: suspend () -> ActionOutcome,
    private val saveLocally: suspend (ProfileDraft) -> Unit,
    private val keepMine: suspend () -> Unit,
    private val useTheirs: suspend () -> Unit,
    private val discard: suspend () -> Unit,
) : MviViewModel<ProfileState, ProfileIntent, ProfileEffect>(ProfileState()) {

    init {
        account.onEach { (email, verified) -> setState { reduce(it, ProfileChange.Account(email, verified)) } }
            .launchIn(viewModelScope)
        // The screen only ever renders the device's copy (P-04.1).
        profile.onEach { view -> setState { reduce(it, ProfileChange.Stored(view)) } }.launchIn(viewModelScope)
        pull()
    }

    override fun onIntent(intent: ProfileIntent) {
        val form = currentState.form
        when (intent) {
            is ProfileIntent.DisplayNameChanged -> form?.let { edit(it.copy(displayName = intent.value)) }
            is ProfileIntent.LanguageChanged -> form?.let { edit(it.copy(locale = intent.locale)) }
            is ProfileIntent.OpenPicker -> if (intent.picker != ProfilePicker.Currency || !currentState.currencyLocked) {
                setState { reduce(it, ProfileChange.Picker(intent.picker)) }
            }
            is ProfileIntent.Picked -> {
                val picker = currentState.picker ?: return
                if (form != null) {
                    edit(
                        when (picker) {
                            ProfilePicker.Timezone -> form.copy(timezone = intent.value)
                            ProfilePicker.Currency -> form.copy(defaultCurrency = intent.value)
                            ProfilePicker.BudgetDay -> form.copy(budgetStartDay = intent.value.toIntOrNull()?.coerceIn(1, 28) ?: form.budgetStartDay)
                        },
                    )
                }
                setState { reduce(it, ProfileChange.Picker(null)) }
            }
            ProfileIntent.ClosePicker -> setState { reduce(it, ProfileChange.Picker(null)) }
            ProfileIntent.Save -> save()
            ProfileIntent.KeepMine -> resolve(keepMine)
            ProfileIntent.UseTheirs -> resolve(useTheirs)
            ProfileIntent.Discard -> resolve(discard)
            ProfileIntent.Retry -> pull()
            ProfileIntent.Back -> sendEffect(ProfileEffect.NavigateBack)
        }
    }

    private fun edit(form: ProfileDraft) = setState { reduce(it, ProfileChange.Edit(form)) }

    private fun save() {
        val form = currentState.form ?: return
        if (!currentState.dirty) return
        val error = ClientValidation.validateDisplayName(form.displayName.orEmpty())
        if (error != null) {
            setState { reduce(it, ProfileChange.DisplayNameInvalid(error)) }
            return
        }
        // Empty clears the name; the currency cannot move once locked (FR-013).
        val locked = currentState.view?.server?.takeIf { it.currencyLocked }
        val draft = form.copy(
            displayName = form.displayName?.trim()?.ifEmpty { null },
            defaultCurrency = locked?.defaultCurrency ?: form.defaultCurrency,
        )
        edit(draft)
        viewModelScope.launch {
            saveLocally(draft)
            sendEffect(ProfileEffect.Saved)
        }
    }

    private fun resolve(action: suspend () -> Unit) {
        if (currentState.resolving) return
        setState { reduce(it, ProfileChange.Resolving(true)) }
        viewModelScope.launch {
            action()
            setState { reduce(it, ProfileChange.Resolving(false)) }
        }
    }

    private fun pull() {
        viewModelScope.launch {
            val banner = when (val outcome = refresh()) {
                ActionOutcome.Done -> null
                // Offline with a stored copy is fine: the screen shows what the device holds.
                ActionOutcome.Offline -> if (currentState.view == null) AccountBanner.Offline else null
                is ActionOutcome.Error -> AccountBanner.Error(outcome.code, outcome.requestId)
            }
            setState { reduce(it, ProfileChange.Banner(banner)) }
        }
    }
}
