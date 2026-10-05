package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.HeldInvitationGlimpse
import com.fares.saveme.feature.sharing.data.InvitationRepository
import com.fares.saveme.feature.wallets.domain.SharingGlance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The wallet screens' view of sharing (spec 013 R-15): the number of pending invitations and the plan note. */
class DefaultSharingGlance(
    private val repository: InvitationRepository,
    private val authState: StateFlow<AuthState>,
    private val planNotes: PlanDemotionNotes,
) : SharingGlance {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePendingCount(walletId: String): Flow<Int> =
        authState.map { (it as? AuthState.SignedIn)?.account?.userId }.distinctUntilChanged().flatMapLatest { account ->
            if (account == null) flowOf(0) else repository.observeLivePendingCount(account, walletId)
        }

    override fun observePlanLimited(walletId: String): Flow<Boolean> = planNotes.observe(walletId)
}

/** Home's card (spec 013 R-16): an invitation link is held in memory. */
class DefaultHeldInvitationGlimpse(private val held: HeldInvitation, private val notices: SharingNotices = SharingNotices()) : HeldInvitationGlimpse {
    override fun observeHeld(): Flow<Boolean> = held.isHeld()

    override fun observeDeclined(): Flow<Unit> = notices.declinedNotices
}
