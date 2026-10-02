package com.fares.saveme.feature.transactions.domain.model

sealed interface CreateResult {
    data class Created(val id: String) : CreateResult
    data object NotVerified : CreateResult
    data object NoWallet : CreateResult
    data object NotAllowed : CreateResult
    data class Invalid(val errors: Map<TransactionField, TransactionFieldProblem>) : CreateResult
}

sealed interface SaveResult {
    data object Saved : SaveResult
    data object NotVerified : SaveResult
    data object NoLongerAvailable : SaveResult
    data object NotAllowed : SaveResult
    data class Invalid(val errors: Map<TransactionField, TransactionFieldProblem>) : SaveResult
}
