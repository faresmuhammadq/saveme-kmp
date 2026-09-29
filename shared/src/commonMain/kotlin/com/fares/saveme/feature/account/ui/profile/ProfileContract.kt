package com.fares.saveme.feature.account.ui.profile

import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.account.domain.model.ProfileField
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.auth.domain.FieldError

data class ProfileState(
    val email: String = "",
    val emailVerified: Boolean = true,
    val view: ProfileView? = null,
    /** What the person is editing; starts from what the device holds (pending edit or server copy). */
    val form: ProfileDraft? = null,
    val displayNameError: FieldError? = null,
    val picker: ProfilePicker? = null,
    val banner: AccountBanner? = null,
    val resolving: Boolean = false,
) {
    val loaded: Boolean get() = view != null && form != null
    val dirty: Boolean get() = form != null && view != null && form != view.effective
    val waitingToSync: Boolean get() = view?.pending != null && view.conflict == null && view.rejection.isEmpty()
    val currencyLocked: Boolean get() = view?.server?.currencyLocked == true
}

enum class ProfilePicker { Timezone, Currency, BudgetDay }

sealed interface ProfileIntent {
    data class DisplayNameChanged(val value: String) : ProfileIntent
    data class LanguageChanged(val locale: String) : ProfileIntent
    data class OpenPicker(val picker: ProfilePicker) : ProfileIntent
    data class Picked(val value: String) : ProfileIntent
    data object ClosePicker : ProfileIntent
    data object Save : ProfileIntent
    data object KeepMine : ProfileIntent
    data object UseTheirs : ProfileIntent
    data object Discard : ProfileIntent
    data object Retry : ProfileIntent
    data object Back : ProfileIntent
}

sealed interface ProfileEffect {
    data object Saved : ProfileEffect
    data object NavigateBack : ProfileEffect
}

sealed interface ProfileChange {
    data class Account(val email: String, val verified: Boolean) : ProfileChange
    data class Stored(val view: ProfileView?) : ProfileChange
    data class Edit(val form: ProfileDraft) : ProfileChange
    data class Picker(val picker: ProfilePicker?) : ProfileChange
    data class DisplayNameInvalid(val error: FieldError?) : ProfileChange
    data class Banner(val banner: AccountBanner?) : ProfileChange
    data class Resolving(val busy: Boolean) : ProfileChange
}

fun reduce(state: ProfileState, change: ProfileChange): ProfileState = when (change) {
    is ProfileChange.Account -> state.copy(email = change.email, emailVerified = change.verified)
    // A new stored copy replaces the form unless the person is mid-edit.
    is ProfileChange.Stored -> state.copy(
        view = change.view,
        form = if (state.form == null || !state.dirty) change.view?.effective else state.form,
    )
    is ProfileChange.Edit -> state.copy(form = change.form, displayNameError = null)
    is ProfileChange.Picker -> state.copy(picker = change.picker)
    is ProfileChange.DisplayNameInvalid -> state.copy(displayNameError = change.error)
    is ProfileChange.Banner -> state.copy(banner = change.banner)
    is ProfileChange.Resolving -> state.copy(resolving = change.busy)
}

/** Fields where the person's edit and the other device's copy differ (shown side by side, FR-012). */
fun ProfileView.conflictingFields(): List<Triple<ProfileField, String?, String?>> {
    val mine = pending ?: return emptyList()
    val theirs = conflict?.toDraft() ?: return emptyList()
    return listOfNotNull(
        Triple(ProfileField.DisplayName, mine.displayName, theirs.displayName).takeIf { it.second != it.third },
        Triple(ProfileField.Locale, mine.locale, theirs.locale).takeIf { it.second != it.third },
        Triple(ProfileField.Timezone, mine.timezone, theirs.timezone).takeIf { it.second != it.third },
        Triple(ProfileField.DefaultCurrency, mine.defaultCurrency, theirs.defaultCurrency).takeIf { it.second != it.third },
        Triple(ProfileField.BudgetStartDay, mine.budgetStartDay.toString(), theirs.budgetStartDay.toString()).takeIf { it.second != it.third },
    )
}
