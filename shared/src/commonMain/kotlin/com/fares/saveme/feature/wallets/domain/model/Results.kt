package com.fares.saveme.feature.wallets.domain.model

/** A problem with one field the person typed, or one the server reported. */
sealed interface WalletFieldProblem {
    data object NameBlank : WalletFieldProblem
    data object NameTooLong : WalletFieldProblem
    data object AmountEmpty : WalletFieldProblem
    data object AmountInvalid : WalletFieldProblem
    data object AmountOutOfRange : WalletFieldProblem
    data class AmountDigits(val digits: Int) : WalletFieldProblem

    /** The server's own error code for the field (IMMUTABLE_FIELD, OUT_OF_RANGE, …). */
    data class Server(val code: String) : WalletFieldProblem
}

sealed interface CreateResult {
    data class Created(val id: String) : CreateResult
    data object NotVerified : CreateResult
    data object NotEntitled : CreateResult
    data class Invalid(val errors: Map<WalletField, WalletFieldProblem>) : CreateResult
}

sealed interface SaveResult {
    data object Saved : SaveResult
    data object NotVerified : SaveResult
    data object NotOwner : SaveResult
    data object Gone : SaveResult
    data class Invalid(val errors: Map<WalletField, WalletFieldProblem>) : SaveResult
}

sealed interface TransferResult {
    data object Done : TransferResult
    data object Offline : TransferResult
    data object NoLongerMember : TransferResult
    data object Changed : TransferResult
    data object NotOwner : TransferResult
    data class Failed(val requestId: String?) : TransferResult
}
