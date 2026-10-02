package com.fares.saveme.feature.billing.domain

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The one way to ask what the signed-in person may do (P-12.1, contracts/entitlements-gate.md).
 * Show, hide or explain controls with it; the server stays the authority. The plan's name and id
 * are not reachable here.
 */
interface Entitlements {
    /** Null when signed out. Emits on sign-in, on every refresh that changes the answer, and on sign-out. */
    val state: StateFlow<EntitlementSet?>

    /** False when signed out or missing. */
    fun can(flag: FlagFeature): Boolean

    /** `Limited(0)` when signed out or missing. */
    fun quota(quota: QuotaFeature): QuotaLimit

    fun allowsAnother(quota: QuotaFeature, currentCount: Int): Boolean

    /** What C10/C11 call after a billing action; never skipped, joins an in-flight refresh. */
    suspend fun refreshAfterBillingAction(): RefreshOutcome
}

class SessionEntitlements(
    private val sessions: SessionManager,
    private val refresher: EntitlementsRefresher,
    scope: CoroutineScope,
) : Entitlements {

    override val state: StateFlow<EntitlementSet?> = sessions.state
        .map { it.toSet() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, sessions.state.value.toSet())

    // Read from the session directly so an answer is never a dispatch behind a refresh.
    private fun current(): EntitlementSet? = sessions.state.value.toSet()

    override fun can(flag: FlagFeature): Boolean = current()?.can(flag) ?: false

    override fun quota(quota: QuotaFeature): QuotaLimit = current()?.quota(quota) ?: QuotaLimit.Limited(0)

    override fun allowsAnother(quota: QuotaFeature, currentCount: Int): Boolean =
        current()?.allowsAnother(quota, currentCount) ?: false

    override suspend fun refreshAfterBillingAction(): RefreshOutcome = refresher.refresh(RefreshReason.BillingAction)
}

private fun AuthState.toSet(): EntitlementSet? = (this as? AuthState.SignedIn)?.let { EntitlementSet(it.account.features) }
