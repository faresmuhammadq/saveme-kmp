package com.fares.saveme.feature.categories.domain.model

/** A problem with one field the person typed, or one the server reported. */
sealed interface CategoryFieldProblem {
    data object NameBlank : CategoryFieldProblem
    data object NameTooLong : CategoryFieldProblem

    /** The server's own error code for the field (IMMUTABLE_FIELD, OUT_OF_RANGE, …). */
    data class Server(val code: String) : CategoryFieldProblem
}

sealed interface CreateResult {
    data class Created(val id: String) : CreateResult
    data object NotVerified : CreateResult
    data class Invalid(val errors: Map<CategoryField, CategoryFieldProblem>) : CreateResult
}

sealed interface SaveResult {
    data object Saved : SaveResult
    data object NotVerified : SaveResult
    data object NoLongerAvailable : SaveResult

    /** A deletion the phone already knows would be refused (the category is in use). */
    data object InUse : SaveResult
    data class Invalid(val errors: Map<CategoryField, CategoryFieldProblem>) : SaveResult
}
