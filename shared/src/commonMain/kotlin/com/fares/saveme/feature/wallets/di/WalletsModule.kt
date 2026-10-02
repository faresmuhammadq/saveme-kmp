package com.fares.saveme.feature.wallets.di

import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.core.sync.WalletScopes
import com.fares.saveme.feature.account.domain.ProfileSyncer
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.wallets.data.OwnMembershipFeedApplier
import com.fares.saveme.feature.wallets.data.WalletApi
import com.fares.saveme.feature.wallets.data.WalletFeedApplier
import com.fares.saveme.feature.wallets.data.WalletMemberFeedApplier
import com.fares.saveme.feature.wallets.data.WalletRepository
import com.fares.saveme.feature.wallets.data.WalletSyncHandler
import com.fares.saveme.feature.wallets.domain.DefaultWalletScopes
import com.fares.saveme.feature.wallets.domain.DefaultWalletsService
import com.fares.saveme.feature.wallets.domain.WalletCopyParticipant
import com.fares.saveme.feature.wallets.domain.WalletDeletionParticipant
import com.fares.saveme.feature.wallets.domain.WalletsService
import com.fares.saveme.feature.wallets.domain.WalletsSyncCoordinator
import com.fares.saveme.feature.wallets.ui.detail.WalletDetailViewModel
import com.fares.saveme.feature.wallets.ui.editor.WalletEditorViewModel
import com.fares.saveme.feature.wallets.ui.list.WalletsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val walletsModule = module {
    single { WalletApi(get()) }
    single {
        val db = get<AppDatabase>()
        val sessions = get<SessionManager>()
        WalletRepository(
            api = get(),
            wallets = db.walletDao(),
            members = db.walletMemberDao(),
            scopes = db.syncScopeDao(),
            accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId },
            // Resolved on use: the engine is built from every SyncHandler, and the wallet handler needs
            // this repository, so resolving the engine here would loop (Koin stack overflow at start).
            hasQueuedChange = { id -> get<SyncEngine>().get(WalletSyncHandler.RECORD_TYPE, id) != null },
            // Resolved on use, so participants registered by later features are found.
            onWalletRemoved = { id -> getAll<WalletDeletionParticipant>().forEach { it.onWalletDeleted(id) } },
            onWalletCopy = { id, changeSeq -> getAll<WalletCopyParticipant>().forEach { it.onWalletCopy(id, changeSeq) } },
        )
    }
    single {
        val sessions = get<SessionManager>()
        WalletSyncHandler(get(), get(), accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId })
    } bind SyncHandler::class
    // Feed appliers (spec 009): the wallet and its members from a wallet's feed, the person's own memberships from theirs.
    single { WalletFeedApplier(get()) } bind FeedApplier::class
    single { WalletMemberFeedApplier(get()) } bind FeedApplier::class
    single { OwnMembershipFeedApplier(get()) } bind FeedApplier::class
    single {
        val sessions = get<SessionManager>()
        DefaultWalletScopes(get(), accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId })
    } bind WalletScopes::class
    single<WalletsService> {
        val sessions = get<SessionManager>()
        DefaultWalletsService(
            repository = get(),
            engine = get(),
            authState = sessions.state,
            entitlements = get(),
            postings = get(),
            // The profile's default currency orders the totals; the device has none until the profile is read.
            defaultCurrency = get<ProfileSyncer>().view.map { it?.effective?.defaultCurrency },
            runner = get(),
        )
    }
    single(createdAtStart = true) {
        WalletsSyncCoordinator(
            authState = get<SessionManager>().state,
            wallets = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            scheduler = get(),
        ).also { it.start() }
    }
    viewModel {
        val wallets = get<WalletsService>()
        val sessions = get<SessionManager>()
        val profile = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        WalletsViewModel(
            list = wallets.observeList(),
            refresh = { force -> wallets.refresh(force) },
            canCreateAnother = { wallets.canCreateAnother() },
            isEmailVerified = { (sessions.state.value as? AuthState.SignedIn)?.account?.emailVerified == true },
            // Times show in the profile timezone (P-05); the device's until the profile is known.
            timeZoneId = profile.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() },
        )
    }
    viewModel { params ->
        val id = params.get<String>()
        val wallets = get<WalletsService>()
        val profile = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        WalletDetailViewModel(
            walletId = id,
            wallet = wallets.observeWallet(id),
            members = wallets.observeMembers(id),
            refreshMembers = { wallets.refreshMembers(id) },
            archive = { wallets.archive(id) },
            delete = { wallets.delete(id) },
            keepMine = { wallets.keepMine(id) },
            useTheirs = { wallets.useTheirs(id) },
            tryAgain = { wallets.tryAgain(id) },
            discard = { wallets.discard(id) },
            transfer = { userId -> wallets.transferOwnership(id, userId) },
            timeZoneId = profile.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() },
        )
    }
    viewModel { params ->
        val wallets = get<WalletsService>()
        val profile = get<ProfileSyncer>()
        WalletEditorViewModel(
            walletId = params.get<String>().ifBlank { null },
            observeWallet = { wallets.observeWallet(it) },
            defaultCurrency = { profile.view.first()?.effective?.defaultCurrency },
            create = { wallets.create(it) },
            save = { id, draft, base -> wallets.save(id, draft, base) },
        )
    }
}
