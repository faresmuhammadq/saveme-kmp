package com.fares.saveme.feature.sharing.di

import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.account.domain.ProfileSyncer
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.HeldInvitationGlimpse
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.billing.domain.Entitlements
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.recurring.domain.RecurringService
import com.fares.saveme.feature.sharing.data.ActivityApi
import com.fares.saveme.feature.sharing.data.InvitationApi
import com.fares.saveme.feature.sharing.data.InvitationFeedApplier
import com.fares.saveme.feature.sharing.data.InvitationRepository
import com.fares.saveme.feature.sharing.data.InvitationSyncHandler
import com.fares.saveme.feature.sharing.data.MemberApi
import com.fares.saveme.feature.sharing.domain.ActivityNameSource
import com.fares.saveme.feature.sharing.domain.DefaultActivityNameSource
import com.fares.saveme.feature.sharing.domain.DefaultHeldInvitationGlimpse
import com.fares.saveme.feature.sharing.domain.DefaultSharingGlance
import com.fares.saveme.feature.sharing.domain.DefaultSharingService
import com.fares.saveme.feature.sharing.domain.HeldInvitation
import com.fares.saveme.feature.sharing.domain.InvitationLink
import com.fares.saveme.feature.sharing.domain.InvitationLinkParser
import com.fares.saveme.feature.sharing.domain.InvitationsWalletCleanup
import com.fares.saveme.feature.sharing.domain.PlanDemotionNotes
import com.fares.saveme.feature.sharing.domain.ShareLinks
import com.fares.saveme.feature.sharing.domain.ShareText
import com.fares.saveme.feature.sharing.domain.SharingNotices
import com.fares.saveme.feature.sharing.domain.SharingService
import com.fares.saveme.feature.sharing.ui.activity.ActivityViewModel
import com.fares.saveme.feature.sharing.ui.invitation.InvitationViewModel
import com.fares.saveme.feature.sharing.ui.invite.InviteArgs
import com.fares.saveme.feature.sharing.ui.invite.InviteViewModel
import com.fares.saveme.feature.sharing.ui.members.MembersViewModel
import com.fares.saveme.feature.transactions.domain.TransactionsService
import com.fares.saveme.feature.wallets.domain.SharingGlance
import com.fares.saveme.feature.wallets.domain.WalletDeletionParticipant
import com.fares.saveme.feature.wallets.domain.WalletMemberships
import com.fares.saveme.feature.wallets.domain.WalletsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.getString
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.app_name
import saveme.shared.generated.resources.invite_share_text

/**
 * Sharing & invitations (spec 013). Listed after the auth, wallets, transactions, recurring and budgets modules so its rebinding
 * of [SharingGlance] and [HeldInvitationGlimpse] wins. The engine is resolved on use wherever a sharing class could otherwise
 * close a cycle (the engine is built from every sync handler; spec 006 lesson), and so are the wallets and transactions services.
 */
val sharingModule = module {
    single { InvitationApi(get()) }
    single { MemberApi(get()) }
    single { ActivityApi(get()) }

    // Memory holders: the invitation secret, one-time links and plan notes never reach storage (P-09.2).
    single { SharingNotices() }
    single { HeldInvitation() } bind UserDataCleaner::class
    single { ShareLinks() } bind UserDataCleaner::class
    single { PlanDemotionNotes() } bind UserDataCleaner::class

    single {
        val sessions = get<SessionManager>()
        InvitationRepository(
            invitations = get<AppDatabase>().invitationDao(),
            accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId },
            // Resolved on use: the engine is built from every SyncHandler, and the invitation handler needs this repository.
            hasQueuedChange = { id -> get<SyncEngine>().get(InvitationSyncHandler.RECORD_TYPE, id) != null },
        )
    }
    single { InvitationFeedApplier(get()) } bind FeedApplier::class
    single {
        val links = get<ShareLinks>()
        InvitationSyncHandler(
            repository = get(),
            api = get(),
            onShareLink = { id, url -> links.offer(id, url) },
            walletVersion = { id -> get<WalletsService>().observeWallet(id).first()?.server?.version },
        )
    } bind SyncHandler::class
    single {
        InvitationsWalletCleanup(
            repository = get(),
            shareLinks = get(),
            engine = { get<SyncEngine>() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    } bind WalletDeletionParticipant::class

    single {
        val config = get<AppConfig>()
        InvitationLinkParser(config.linkHost, config.linkPort, scheme = if (config.environment == Environment.Dev) "http" else "https")
    }

    single<ActivityNameSource> {
        DefaultActivityNameSource(get<WalletsService>(), get<TransactionsService>(), get<RecurringService>(), get<CategoriesService>())
    }
    single<SharingService> {
        val sessions = get<SessionManager>()
        DefaultSharingService(
            repository = get(),
            invitationApi = get(),
            memberApi = get(),
            activityApi = get(),
            engine = { get<SyncEngine>() },
            authState = sessions.state,
            entitlements = get<Entitlements>(),
            memberships = get<WalletMemberships>(),
            wallets = get<WalletsService>(),
            runner = get(),
            shareLinks = get(),
            heldInvitation = get(),
            planNotes = get(),
            activityNames = get(),
            buildShareText = { url -> ShareText.build(getString(Res.string.app_name), url, getString(Res.string.invite_share_text)) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    viewModel { params ->
        val walletId = params.get<String>()
        val sharing = get<SharingService>()
        val wallets = get<WalletsService>()
        val profile = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        MembersViewModel(
            walletId = walletId,
            wallet = wallets.observeWallet(walletId),
            members = sharing.observeMembers(walletId),
            invitations = sharing.observeInvitations(walletId),
            inviteCheck = sharing.observeInviteCheck(walletId),
            planNote = sharing.observePlanNote(walletId),
            changeRole = { userId, role -> sharing.changeRole(walletId, userId, role) },
            remove = { userId -> sharing.remove(walletId, userId) },
            withdraw = { id -> sharing.withdraw(id) },
            shareText = { id -> sharing.shareTextFor(id) },
            discard = { id -> sharing.discard(id) },
            tryAgain = { id -> sharing.tryAgain(id) },
            leaveCheck = { sharing.leaveCheck(walletId) },
            leave = { sharing.leave(walletId) },
            // Times show in the profile timezone (P-05); the device's until the profile is known.
            timeZoneId = profile.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() },
            inviteNotices = get<SharingNotices>().inviteNotices,
        )
    }
    viewModel { params ->
        val args = params.get<InviteArgs>()
        val sharing = get<SharingService>()
        InviteViewModel(
            walletId = args.walletId,
            replacing = args.replacing?.ifBlank { null },
            inviteCheck = sharing.observeInviteCheck(args.walletId),
            invite = { draft -> sharing.invite(args.walletId, draft) },
            inviteConfirmed = { draft -> sharing.inviteConfirmed(args.walletId, draft) },
            replaceLink = { id -> sharing.replaceLink(id) },
            awaitDelivery = { id -> sharing.awaitDelivery(id) },
            linkHeld = { id -> sharing.observeLinkHeld(id) },
            shareText = { id -> sharing.shareTextFor(id) },
            announce = { notice -> get<SharingNotices>().inviteClosed(notice) },
        )
    }
    viewModel { params ->
        val sharing = get<SharingService>()
        val held = get<HeldInvitation>()
        val sessions = get<SessionManager>()
        val profile = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        InvitationViewModel(
            token = params.get<String>(),
            isSignedIn = { sessions.state.value is AuthState.SignedIn },
            preview = { token -> sharing.preview(token) },
            join = { token -> sharing.join(token) },
            decline = { token -> sharing.decline(token) },
            hold = { token -> held.hold(InvitationLink.Valid(token)) },
            clearHeld = { held.drop() },
            announceDeclined = { get<SharingNotices>().invitationDeclined() },
            timeZoneId = profile.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() },
        )
    }
    viewModel { params ->
        val walletId = params.get<String>()
        val sharing = get<SharingService>()
        val profile = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        ActivityViewModel(
            walletId = walletId,
            page = { cursor -> sharing.activityPage(walletId, cursor) },
            timeZoneId = profile.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() },
        )
    }

    // The wallet screens' and Home's views of sharing replace the defaults the other features own (R-15, R-16).
    single<SharingGlance> { DefaultSharingGlance(get(), get<SessionManager>().state, get()) }
    single<HeldInvitationGlimpse> { DefaultHeldInvitationGlimpse(get(), get()) }
}
