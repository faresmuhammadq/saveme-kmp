package com.fares.saveme.feature.wallets.ui.editor

import com.fares.saveme.feature.wallets.domain.AmountInput
import com.fares.saveme.feature.wallets.domain.model.WalletField
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem
import com.fares.saveme.feature.wallets.domain.model.WalletType

data class WalletEditorState(
    /** Null while creating; the wallet's id while editing. */
    val walletId: String? = null,
    val loaded: Boolean = false,
    val type: WalletType = WalletType.Cash,
    /** The wire value of the type being edited, so an unknown type is sent back unchanged. */
    val typeWire: String = WalletType.Cash.wire,
    val name: String = "",
    val currency: String = "",
    val currencyLocked: Boolean = false,
    /** What is typed: the opening balance, or for a liability what is owed. */
    val amountText: String = "",
    val iconKey: String? = null,
    val accentKey: String? = null,
    val errors: Map<WalletField, WalletFieldProblem> = emptyMap(),
    val currencyPickerVisible: Boolean = false,
    val saving: Boolean = false,
    /** The version shown when editing began; the save is sent on top of it (FR-010). */
    val baseVersion: Long = 0,
    /** Carried through a save so editing never drops a waiting archive. */
    val archive: Boolean = false,
) {
    val isCreate: Boolean get() = walletId == null
    val typeEditable: Boolean get() = isCreate
    val currencyEditable: Boolean get() = !currencyLocked
    val isLiability: Boolean get() = type.isLiability
    val canSave: Boolean get() = loaded && name.isNotBlank() && !saving
}

sealed interface WalletEditorIntent {
    data class TypeChosen(val type: WalletType) : WalletEditorIntent
    data class NameChanged(val value: String) : WalletEditorIntent
    data object CurrencyClicked : WalletEditorIntent
    data class CurrencyChosen(val currency: String) : WalletEditorIntent
    data object DismissCurrencyPicker : WalletEditorIntent
    data class AmountChanged(val value: String) : WalletEditorIntent
    data class IconChosen(val key: String?) : WalletEditorIntent
    data class AccentChosen(val key: String?) : WalletEditorIntent
    data object SaveClicked : WalletEditorIntent
    data object Back : WalletEditorIntent
}

sealed interface WalletEditorEffect {
    data class Saved(val id: String) : WalletEditorEffect
    data object NavigateBack : WalletEditorEffect
    data object RequireVerification : WalletEditorEffect
    data object NotEntitled : WalletEditorEffect
}

sealed interface WalletEditorChange {
    /** The initial values: the profile's currency when creating, the wallet as shown when editing. */
    data class Loaded(val state: WalletEditorState) : WalletEditorChange
    data class TypeChosen(val type: WalletType) : WalletEditorChange
    data class Name(val value: String) : WalletEditorChange
    data class Currency(val currency: String) : WalletEditorChange
    data class CurrencyPicker(val visible: Boolean) : WalletEditorChange
    data class Amount(val value: String) : WalletEditorChange
    data class Icon(val key: String?) : WalletEditorChange
    data class Accent(val key: String?) : WalletEditorChange
    data class Errors(val errors: Map<WalletField, WalletFieldProblem>) : WalletEditorChange
    data class Saving(val busy: Boolean) : WalletEditorChange
}

fun reduce(state: WalletEditorState, change: WalletEditorChange): WalletEditorState = when (change) {
    is WalletEditorChange.Loaded -> change.state.copy(loaded = true)
    is WalletEditorChange.TypeChosen ->
        if (state.typeEditable) {
            // Liability amounts are typed as what is owed, so a minus typed for an asset is no longer valid.
            state.copy(type = change.type, typeWire = change.type.wire).withAmountProblem()
        } else {
            state
        }
    is WalletEditorChange.Name -> state.copy(name = change.value, errors = state.errors.withNameProblem(change.value))
    is WalletEditorChange.Currency -> state.copy(currency = change.currency, currencyPickerVisible = false).withAmountProblem()
    is WalletEditorChange.CurrencyPicker -> state.copy(currencyPickerVisible = change.visible)
    is WalletEditorChange.Amount -> state.copy(amountText = change.value).withAmountProblem()
    is WalletEditorChange.Icon -> state.copy(iconKey = change.key, errors = state.errors - WalletField.Icon)
    is WalletEditorChange.Accent -> state.copy(accentKey = change.key, errors = state.errors - WalletField.Color)
    is WalletEditorChange.Errors -> state.copy(errors = change.errors)
    is WalletEditorChange.Saving -> state.copy(saving = change.busy)
}

private fun Map<WalletField, WalletFieldProblem>.withNameProblem(name: String): Map<WalletField, WalletFieldProblem> = when {
    name.isBlank() -> this + (WalletField.Name to WalletFieldProblem.NameBlank)
    name.trim().length > 100 -> this + (WalletField.Name to WalletFieldProblem.NameTooLong)
    else -> this - WalletField.Name
}

/** Re-checks what is typed whenever the text, the currency or the type changes; empty text shows no error yet. */
private fun WalletEditorState.withAmountProblem(): WalletEditorState {
    if (amountText.isBlank()) return copy(errors = errors - WalletField.OpeningBalance)
    return when (val result = AmountInput.parse(amountText, currency, isLiability)) {
        is AmountInput.Result.Valid -> copy(errors = errors - WalletField.OpeningBalance)
        is AmountInput.Result.Invalid -> copy(errors = errors + (WalletField.OpeningBalance to result.problem))
    }
}
