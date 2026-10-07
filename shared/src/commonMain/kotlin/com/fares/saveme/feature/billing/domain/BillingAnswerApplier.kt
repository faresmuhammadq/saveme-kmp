package com.fares.saveme.feature.billing.domain

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.billing.data.SubscriptionRepository
import com.fares.saveme.feature.billing.data.dto.MySubscriptionDto
import com.fares.saveme.feature.billing.data.dto.RedeemEntitlementsDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Applies a billing answer's entitlements and governing subscription (redeem, Google Play verify, Google Play restore):
 * only for the person who was signed in when the call started, then C3's re-read follows (spec 018 R-6). The re-read only
 * drives the Plan screen's stale note; the applied answer already shows.
 */
class BillingAnswerApplier(
    private val sessions: SessionManager,
    private val subscriptions: SubscriptionRepository,
    private val entitlements: Entitlements,
    private val scope: CoroutineScope,
) {
    /** The plan the stored governing subscription names, for telling a changed plan; null until one was stored on this phone. */
    fun storedPlanId(): String? = subscriptions.stored.value?.plan?.id

    /** False, with nothing written, when signed out or another person is signed in now. */
    suspend fun apply(userId: String, answer: RedeemEntitlementsDto, subscription: MySubscriptionDto): Boolean {
        val stillThem = (sessions.state.value as? AuthState.SignedIn)?.account?.userId == userId
        val applied = stillThem && sessions.applyEntitlements(
            forUserId = userId,
            planId = answer.plan.id,
            planName = answer.plan.name,
            features = answer.features,
        )
        if (!applied) return false
        subscriptions.save(subscription)
        scope.launch { entitlements.refreshAfterBillingAction() }
        return true
    }
}
