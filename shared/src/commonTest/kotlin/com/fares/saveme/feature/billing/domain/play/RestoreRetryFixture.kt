package com.fares.saveme.feature.billing.domain.play

/** A [RestoreRetry] for store tests: the type's constructor is internal to the module, which includes its tests. */
fun testRetry(): RestoreRetry = RestoreRetry("user-1", emptyList(), 0, RestoreSummary())
