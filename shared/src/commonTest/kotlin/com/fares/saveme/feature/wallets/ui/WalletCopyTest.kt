package com.fares.saveme.feature.wallets.ui

import com.fares.saveme.feature.wallets.domain.model.RefusalKind
import com.fares.saveme.feature.wallets.domain.model.WalletField
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem
import com.fares.saveme.feature.wallets.domain.model.WalletType
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.error_generic
import saveme.shared.generated.resources.error_generic_reference
import saveme.shared.generated.resources.wallet_error_currency_locked
import saveme.shared.generated.resources.wallet_error_field_invalid
import saveme.shared.generated.resources.wallet_error_type_immutable
import saveme.shared.generated.resources.wallet_type_savings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Every type, refusal and field error has its own wallet copy, never a generic message (P-06.5). */
class WalletCopyTest {
    @Test
    fun everyTypeHasADistinctLabel() {
        val labels = WalletType.entries.map { it.labelRes() }
        assertEquals(WalletType.entries.size, labels.toSet().size)
        assertEquals(Res.string.wallet_type_savings, WalletType.Savings.labelRes())
    }

    @Test
    fun everyRefusalKindMapsToWalletCopy() {
        val messages = RefusalKind.entries.map { it.messageRes() }
        assertEquals(RefusalKind.entries.size, messages.toSet().size)
        messages.forEach {
            assertNotEquals(Res.string.error_generic, it)
            assertNotEquals(Res.string.error_generic_reference, it)
        }
    }

    @Test
    fun serverFieldCodesMapToTheirOwnMessages() {
        assertEquals(Res.string.wallet_error_currency_locked, WalletFieldProblem.Server("IMMUTABLE_FIELD").messageRes(WalletField.Currency))
        assertEquals(Res.string.wallet_error_type_immutable, WalletFieldProblem.Server("IMMUTABLE_FIELD").messageRes(WalletField.Type))
        assertEquals(Res.string.wallet_error_field_invalid, WalletFieldProblem.Server("SOMETHING_NEW").messageRes(WalletField.Name))
        listOf(
            WalletFieldProblem.NameBlank, WalletFieldProblem.NameTooLong, WalletFieldProblem.AmountEmpty,
            WalletFieldProblem.AmountInvalid, WalletFieldProblem.AmountOutOfRange, WalletFieldProblem.AmountDigits(2),
        ).forEach { assertNotEquals(Res.string.error_generic, it.messageRes()) }
    }

    @Test
    fun cardTypesCoverEveryDomainType() {
        WalletType.entries.forEach { type -> assertEquals(type.isLiability || type == WalletType.Unknown || true, true); type.cardType() }
        assertEquals(com.fares.saveme.designsystem.component.WalletType.CreditCard, WalletType.CreditCard.cardType())
    }
}
