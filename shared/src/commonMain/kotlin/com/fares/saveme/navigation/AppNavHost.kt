package com.fares.saveme.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import com.fares.saveme.designsystem.component.AppTabBar
import com.fares.saveme.designsystem.component.MainTab
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.budgets_home_row
import saveme.shared.generated.resources.home_add_transaction
import saveme.shared.generated.resources.home_title
import saveme.shared.generated.resources.home_wallets
import saveme.shared.generated.resources.reports_home_row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiEvents
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.devtools.DevToolsEffect
import com.fares.saveme.devtools.DevToolsRoute
import com.fares.saveme.feature.server.ServerAddressEffect
import com.fares.saveme.feature.server.ServerAddressRoute
import com.fares.saveme.devtools.catalog.ComponentCatalogEffect
import com.fares.saveme.devtools.catalog.ComponentCatalogRoute
import com.fares.saveme.devtools.pastelink.PasteLinkEffect
import com.fares.saveme.devtools.pastelink.PasteLinkRoute
import com.fares.saveme.feature.account.ui.account.AccountDestination
import com.fares.saveme.feature.account.ui.account.AccountEffect
import com.fares.saveme.feature.account.ui.account.AccountRoute
import com.fares.saveme.feature.account.ui.changepassword.ChangePasswordEffect
import com.fares.saveme.feature.account.ui.changepassword.ChangePasswordRoute
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountEffect
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountRoute
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailEffect
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailRoute
import com.fares.saveme.feature.account.ui.export.list.ExportsEffect
import com.fares.saveme.feature.account.ui.export.list.ExportsRoute
import com.fares.saveme.feature.account.ui.export.request.ExportRequestEffect
import com.fares.saveme.feature.account.ui.export.request.ExportRequestRoute
import com.fares.saveme.feature.account.ui.profile.ProfileEffect
import com.fares.saveme.feature.account.ui.profile.ProfileRoute
import com.fares.saveme.feature.account.ui.sessions.DeviceSessionsEffect
import com.fares.saveme.feature.account.ui.sessions.DeviceSessionsRoute
import com.fares.saveme.feature.billing.ui.plan.PlanEffect
import com.fares.saveme.feature.billing.ui.redeem.RedeemEffect
import com.fares.saveme.feature.billing.ui.redeem.RedeemRoute
import com.fares.saveme.feature.budgets.ui.editor.BudgetEditorEffect
import com.fares.saveme.devtools.playbilling.PlayBillingDevEffect
import com.fares.saveme.devtools.playbilling.PlayBillingDevRoute
import com.fares.saveme.devtools.push.SimulatePushEffect
import com.fares.saveme.devtools.push.SimulatePushRoute
import com.fares.saveme.feature.notifications.domain.NotificationPromptGate
import com.fares.saveme.feature.notifications.domain.PushCoordinator
import com.fares.saveme.feature.notifications.domain.PushPayloadParser
import com.fares.saveme.feature.notifications.domain.PushRouter
import com.fares.saveme.feature.notifications.ui.PermissionStatus
import com.fares.saveme.feature.notifications.ui.invitation.InvitationNoticeEffect
import com.fares.saveme.feature.notifications.ui.invitation.InvitationNoticeRoute
import com.fares.saveme.feature.notifications.ui.open.NotificationOpenEffect
import com.fares.saveme.feature.notifications.ui.open.NotificationOpenRoute
import com.fares.saveme.feature.notifications.ui.prompt.NotificationPromptSheet
import com.fares.saveme.feature.notifications.ui.rememberNotificationPermissionRequester
import com.fares.saveme.feature.notifications.ui.settings.NotificationSettingsEffect
import com.fares.saveme.feature.notifications.ui.settings.NotificationSettingsRoute
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.fares.saveme.feature.budgets.ui.editor.BudgetEditorRoute
import com.fares.saveme.feature.budgets.ui.list.BudgetsEffect
import com.fares.saveme.feature.budgets.ui.list.BudgetsRoute
import com.fares.saveme.feature.sharedbudgets.domain.HeldSharedBudgetInvitation
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetLinkParser
import com.fares.saveme.feature.sharedbudgets.ui.invitation.SharedBudgetInvitationEffect
import com.fares.saveme.feature.sharedbudgets.ui.invitation.SharedBudgetInvitationRoute
import com.fares.saveme.feature.sharedbudgets.ui.detail.SharedBudgetDetailEffect
import com.fares.saveme.feature.sharedbudgets.ui.editor.SharedBudgetEditorEffect
import com.fares.saveme.feature.sharedbudgets.ui.editor.SharedBudgetEditorRoute
import com.fares.saveme.feature.sharedbudgets.ui.detail.SharedBudgetDetailRoute
import com.fares.saveme.feature.sharedbudgets.ui.list.SharedBudgetsEffect
import com.fares.saveme.feature.sharedbudgets.ui.list.SharedBudgetsRoute
import com.fares.saveme.feature.categories.ui.editor.CategoryEditorEffect
import com.fares.saveme.feature.sharing.domain.HeldInvitation
import com.fares.saveme.feature.sharing.domain.InvitationLinkParser
import com.fares.saveme.feature.sharing.ui.activity.ActivityEffect
import com.fares.saveme.feature.sharing.ui.activity.ActivityRoute
import com.fares.saveme.feature.sharing.ui.invitation.InvitationEffect
import com.fares.saveme.feature.sharing.ui.invitation.InvitationRoute
import com.fares.saveme.feature.sharing.ui.invite.InviteEffect
import com.fares.saveme.feature.sharing.ui.invite.InviteRoute
import com.fares.saveme.feature.sharing.ui.members.MembersEffect
import com.fares.saveme.feature.sharing.ui.members.MembersRoute
import com.fares.saveme.feature.categories.ui.editor.CategoryEditorRoute
import com.fares.saveme.feature.categories.ui.list.CategoriesEffect
import com.fares.saveme.feature.categories.ui.list.CategoriesRoute
import com.fares.saveme.feature.transactions.ui.editor.TransactionEditorEffect
import com.fares.saveme.feature.transactions.ui.editor.TransactionEditorRoute
import com.fares.saveme.feature.transactions.ui.list.TransactionsEffect
import com.fares.saveme.feature.transactions.ui.list.TransactionsRoute
import com.fares.saveme.feature.reports.ui.categories.ReportCategoriesEffect
import com.fares.saveme.feature.reports.ui.categories.ReportCategoriesRoute
import com.fares.saveme.feature.reports.ui.reports.ReportsEffect
import com.fares.saveme.feature.reports.ui.reports.ReportsRoute
import com.fares.saveme.feature.wallets.ui.detail.WalletDetailEffect
import com.fares.saveme.feature.wallets.ui.detail.WalletDetailRoute
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorEffect
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorRoute
import com.fares.saveme.feature.recurring.domain.model.RulePrefill
import com.fares.saveme.feature.recurring.ui.list.RecurringEffect
import com.fares.saveme.feature.recurring.ui.list.RecurringRoute
import com.fares.saveme.feature.recurring.ui.rule.RuleDetailEffect
import com.fares.saveme.feature.recurring.ui.rule.RuleDetailRoute
import com.fares.saveme.feature.recurring.ui.occurrence.OccurrenceEffect
import com.fares.saveme.feature.recurring.ui.occurrence.OccurrenceRoute
import com.fares.saveme.feature.wallets.ui.editor.WalletEditorEffect
import com.fares.saveme.feature.wallets.ui.editor.WalletEditorRoute
import com.fares.saveme.feature.wallets.ui.list.WalletsEffect
import com.fares.saveme.feature.wallets.ui.list.WalletsRoute
import com.fares.saveme.feature.billing.ui.plan.PlanRoute
import com.fares.saveme.feature.auth.domain.AuthLinkParser
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxEffect
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxKind
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxRoute
import com.fares.saveme.feature.auth.ui.password.forgot.ForgotPasswordEffect
import com.fares.saveme.feature.auth.ui.password.forgot.ForgotPasswordRoute
import com.fares.saveme.feature.auth.ui.password.reset.ResetPasswordEffect
import com.fares.saveme.feature.auth.ui.password.reset.ResetPasswordRoute
import com.fares.saveme.feature.auth.ui.register.RegisterEffect
import com.fares.saveme.feature.auth.ui.register.RegisterRoute
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.ui.entry.EntryEffect
import com.fares.saveme.feature.auth.ui.entry.EntryRoute
import com.fares.saveme.feature.auth.ui.home.HomeEffect
import com.fares.saveme.feature.auth.ui.home.HomeRoute
import com.fares.saveme.feature.auth.ui.signin.SignInArgs
import com.fares.saveme.feature.auth.ui.signin.SignInEffect
import com.fares.saveme.feature.auth.ui.signin.SignInRoute
import com.fares.saveme.feature.auth.ui.update.UpdateRequiredRoute
import com.fares.saveme.feature.auth.ui.verify.EmailNotVerifiedSheet
import com.fares.saveme.feature.auth.ui.verify.VerifyEmailEffect
import com.fares.saveme.feature.auth.ui.verify.VerifyEmailRoute
import kotlinx.coroutines.flow.filterIsInstance
import org.koin.compose.koinInject

/**
 * Two back stacks chosen by the session: signed out → auth screens, signed in → the app.
 * Screens' effects become back-stack operations here; stores never touch navigation (P-16.8).
 */
@Composable
fun AppNavHost(
    sessionManager: SessionManager = koinInject(),
    apiEvents: ApiEvents = koinInject(),
    deepLinks: DeepLinkBus = koinInject(),
    linkParser: AuthLinkParser = koinInject(),
    invitationParser: InvitationLinkParser = koinInject(),
    heldInvitation: HeldInvitation = koinInject(),
    sharedBudgetParser: SharedBudgetLinkParser = koinInject(),
    heldSharedBudget: HeldSharedBudgetInvitation = koinInject(),
    pushTaps: PushTapBus = koinInject(),
    promptGate: NotificationPromptGate = koinInject(),
    pushCoordinator: PushCoordinator = koinInject(),
) {
    val authState by sessionManager.state.collectAsStateWithLifecycle()
    val authStack = remember { mutableStateListOf<NavKey>(EntryKey) }
    val appStack = remember { mutableStateListOf<NavKey>(HomeKey) }

    LaunchedEffect(sessionManager) { sessionManager.restore() }

    // Keyed on the transition, not the state: a refreshed account (same person, still signed in)
    // must not throw the person back to Home.
    val signedIn = authState is AuthState.SignedIn
    val signedOutReason = (authState as? AuthState.SignedOut)?.reason
    LaunchedEffect(signedIn, signedOutReason) {
        when (val state = authState) {
            is AuthState.SignedOut -> when (state.reason) {
                SignOutReason.SessionEnded, SignOutReason.PasswordReset ->
                    authStack.resetTo(EntryKey, SignInKey(reason = state.reason))
                SignOutReason.UserSignedOut -> authStack.resetTo(EntryKey)
                SignOutReason.None -> Unit
            }
            is AuthState.SignedIn -> {
                appStack.resetTo(HomeKey)
                // An invitation opened before sign-in is answered right after it, in the same app session (spec 013 FR-011).
                heldInvitation.held.value?.let { appStack.add(InvitationKey(it.token)) }
                heldSharedBudget.held.value?.let { appStack.add(SharedBudgetInvitationKey(it.token)) }
            }
            AuthState.Unknown -> Unit
        }
    }

    val activeStack = if (authState is AuthState.SignedIn) appStack else authStack

    // Mail links (research R-04). Keyed like the transition effect above and declared after it, so
    // a stack reset on sign-in happens first and never drops the link screen. Waits until the
    // stored session is read, so a cold-start link lands on the right stack.
    LaunchedEffect(signedIn, authState == AuthState.Unknown) {
        if (sessionManager.state.value == AuthState.Unknown) return@LaunchedEffect
        deepLinks.links.collect { raw ->
            deepLinks.consume()
            val isSignedIn = sessionManager.state.value is AuthState.SignedIn
            val route = routeLink(raw, isSignedIn, linkParser, invitationParser, sharedBudgetParser)
            val key = (route as? LinkRoute.Open)?.key ?: return@collect
            route.hold?.let(heldInvitation::hold)
            route.holdSharedBudget?.let(heldSharedBudget::hold)
            if (isSignedIn) {
                // A link opened while an invitation is showing replaces it (spec 013 edge cases).
                if (key is InvitationKey && appStack.lastOrNull() is InvitationKey) appStack.removeAt(appStack.lastIndex)
                if (key is SharedBudgetInvitationKey && appStack.lastOrNull() is SharedBudgetInvitationKey) appStack.removeAt(appStack.lastIndex)
                appStack.add(key)
            } else {
                // Replace a half-filled form on top, but keep the entry screen underneath.
                if (authStack.size > 1) authStack.removeAt(authStack.lastIndex)
                authStack.add(key)
            }
        }
    }

    // Taps on notifications (spec 017 R-10). Declared after the transition effect, like the mail links. A tap while nobody is
    // signed in is forgotten, not held (FR-029); one while the update screen blocks is dropped (FR-033); a tap only navigates.
    var tapCount by remember { mutableStateOf(0L) }
    LaunchedEffect(signedIn, authState == AuthState.Unknown) {
        if (sessionManager.state.value == AuthState.Unknown) return@LaunchedEffect
        pushTaps.taps.collect { data ->
            pushTaps.consume()
            val route = PushRouter.route(PushPayloadParser.parse(data))
            appStack.openPushTap(route, sessionManager.state.value is AuthState.SignedIn) { ++tapCount }
        }
    }

    // Any call can answer 426; the blocking screen goes on whichever stack is showing (FR-018).
    LaunchedEffect(apiEvents) {
        apiEvents.events.filterIsInstance<ApiEvent.UpdateRequired>().collect { event ->
            val stack = if (sessionManager.state.value is AuthState.SignedIn) appStack else authStack
            if (stack.lastOrNull() !is UpdateRequiredKey) stack.add(UpdateRequiredKey(event.minimumVersion))
        }
    }

    // Any action the server refuses because the address isn't verified gets the verify prompt,
    // never a bare denial (spec 003 FR-014).
    var emailNotVerifiedVisible by remember { mutableStateOf(false) }
    LaunchedEffect(apiEvents) {
        apiEvents.events.filterIsInstance<ApiEvent.EmailNotVerified>().collect {
            if (sessionManager.state.value is AuthState.SignedIn) emailNotVerifiedVisible = true
        }
    }
    if (emailNotVerifiedVisible && signedIn) {
        EmailNotVerifiedSheet(onDismiss = { emailNotVerifiedVisible = false })
    }

    // The notification explanation: once per installation, after a moment where it clearly helps, never over another sheet (FR-011).
    var promptVisible by remember { mutableStateOf(false) }
    val permissionRequester = rememberNotificationPermissionRequester()
    val scope = rememberCoroutineScope()
    val offerPrompt: () -> Unit = {
        val askable = permissionRequester.status() == PermissionStatus.Askable
        scope.launch { if (promptGate.offerIfDue(askable, otherSheetShowing = emailNotVerifiedVisible || promptVisible)) promptVisible = true }
    }

    if (promptVisible && signedIn) {
        NotificationPromptSheet(
            onTurnOn = {
                promptVisible = false
                // The person's own tap on "Turn on": the only place besides the Notifications screen that may raise the dialog (FR-010).
                permissionRequester.request { granted -> if (granted) pushCoordinator.onPermissionChanged() }
            },
            onNotNow = { promptVisible = false },
        )
    }

    if (authState == AuthState.Unknown) {
        // Nothing until the stored session is read, so the wrong screen never flashes (FR-011).
        Box(Modifier.fillMaxSize().background(AppTheme.colors.background))
        return
    }

    // The tab bar sits under the screens that are tabs of the signed-in app, outside the stack's own scaffolds.
    val topKey = if (signedIn) activeStack.lastOrNull() else null
    val tab = topKey?.let(::tabOf)
    Column(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
        Box(
            Modifier
                .weight(1f)
                .then(if (tab != null) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
        ) {
            NavDisplay(
                backStack = activeStack,
                onBack = { if (activeStack.lastOrNull() !is UpdateRequiredKey) activeStack.pop() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                modifier = Modifier.fillMaxSize().background(AppTheme.colors.background),
                entryProvider = entryProvider {
                    entry<EntryKey> {
                        EntryRoute(onEffect = { effect ->
                            when (effect) {
                                EntryEffect.NavigateSignIn -> authStack.add(SignInKey())
                                EntryEffect.NavigateRegister -> authStack.add(RegisterKey())
                                EntryEffect.NavigateDevTools -> authStack.add(DevToolsKey)
                                EntryEffect.NavigateServer -> authStack.add(ServerAddressKey)
                            }
                        })
                    }
                    entry<SignInKey> { key ->
                        SignInRoute(args = SignInArgs(key.prefillEmail, key.reason), onEffect = { effect ->
                            when (effect) {
                                is SignInEffect.NavigateForgotPassword -> authStack.add(ForgotPasswordKey(effect.email.ifBlank { null }))
                                SignInEffect.NavigateRegister -> authStack.add(RegisterKey())
                                SignInEffect.NavigateBack -> authStack.pop()
                            }
                        })
                    }
                    entry<RegisterKey> { key ->
                        RegisterRoute(prefillEmail = key.prefillEmail, onEffect = { effect ->
                            when (effect) {
                                is RegisterEffect.NavigateCheckInbox -> authStack.add(CheckInboxKey(effect.email, CheckInboxKind.Verification))
                                is RegisterEffect.NavigateSignIn -> authStack.resetTo(EntryKey, SignInKey(prefillEmail = effect.email))
                                is RegisterEffect.NavigateForgotPassword -> authStack.add(ForgotPasswordKey(effect.email))
                                RegisterEffect.NavigateBack -> authStack.pop()
                            }
                        })
                    }
                    entry<CheckInboxKey> { key ->
                        CheckInboxRoute(email = key.email, kind = key.kind, onEffect = { effect ->
                            when (effect) {
                                is CheckInboxEffect.NavigateSignIn ->
                                    if (sessionManager.state.value is AuthState.SignedIn) {
                                        appStack.resetTo(HomeKey)
                                    } else {
                                        authStack.resetTo(EntryKey, SignInKey(prefillEmail = effect.email))
                                    }
                            }
                        })
                    }
                    entry<HomeKey> {
                        HomeRoute(onEffect = { effect ->
                            when (effect) {
                                HomeEffect.NavigateAccount -> appStack.add(AccountKey)
                                HomeEffect.NavigateWallets -> appStack.add(WalletsKey)
                                HomeEffect.NavigateCategories -> appStack.add(CategoriesKey)
                                HomeEffect.NavigateAddTransaction -> appStack.add(TransactionEditorKey())
                                HomeEffect.OpenDue -> appStack.add(RecurringKey(null))
                                HomeEffect.NavigateBudgets -> appStack.add(BudgetsKey)
                                HomeEffect.NavigateReports -> appStack.add(ReportsKey())
                                HomeEffect.OpenInvitation -> heldInvitation.held.value?.let { appStack.add(InvitationKey(it.token)) }
                                is HomeEffect.OpenBudget -> appStack.add(BudgetEditorKey(effect.id))
                                is HomeEffect.OpenWallet -> appStack.add(WalletDetailKey(effect.id))
                                is HomeEffect.OpenTransaction -> appStack.add(TransactionEditorKey(effect.id, effect.walletId))
                                HomeEffect.OpenReport -> appStack.add(ReportsKey())
                                is HomeEffect.OpenReportCategories -> appStack.add(
                                    ReportCategoriesKey(null, effect.from.toString(), effect.to.toString(), effect.currency, false, "expense"),
                                )
                                HomeEffect.OpenBudgetEditor -> appStack.add(BudgetEditorKey())
                                is HomeEffect.ShowMessage -> Unit // shown by the route
                            }
                        })
                    }
                    entry<AccountKey> {
                        AccountRoute(onEffect = { effect ->
                            when (effect) {
                                is AccountEffect.Navigate -> appStack.add(
                                    when (effect.destination) {
                                        AccountDestination.Profile -> ProfileKey
                                        AccountDestination.Devices -> DeviceSessionsKey
                                        AccountDestination.ChangePassword -> ChangePasswordKey
                                        AccountDestination.DeleteAccount -> DeleteAccountKey
                                        AccountDestination.Plan -> PlanKey
                                        AccountDestination.Notifications -> NotificationSettingsKey
                                        AccountDestination.Categories -> CategoriesKey
                                        AccountDestination.DevTools -> DevToolsKey
                                        AccountDestination.Server -> ServerAddressKey
                                        AccountDestination.Exports -> ExportsKey
                                    },
                                )
                                AccountEffect.NavigateBack -> appStack.pop()
                            }
                        })
                    }
                    entry<ProfileKey> {
                        ProfileRoute(onEffect = { effect ->
                            when (effect) {
                                ProfileEffect.NavigateBack -> appStack.pop()
                                ProfileEffect.Saved -> Unit // shown by the route
                            }
                        })
                    }
                    entry<DeleteAccountKey> {
                        DeleteAccountRoute(onEffect = { effect ->
                            when (effect) {
                                DeleteAccountEffect.NavigateBack -> appStack.pop()
                                DeleteAccountEffect.OpenExports -> appStack.add(ExportsKey)
                                // Home carries the pending-deletion notice from here on (FR-019).
                                DeleteAccountEffect.Finished -> appStack.resetTo(HomeKey)
                            }
                        })
                    }
                    entry<ExportsKey> {
                        ExportsRoute(onEffect = { effect ->
                            when (effect) {
                                is ExportsEffect.OpenDetail -> appStack.add(ExportDetailKey(effect.id))
                                ExportsEffect.OpenRequest -> appStack.add(ExportRequestKey())
                                ExportsEffect.NavigateBack -> appStack.pop()
                            }
                        })
                    }
                    entry<ExportRequestKey> { key ->
                        ExportRequestRoute(walletId = key.walletId, onEffect = { effect ->
                            when (effect) {
                                // The request screen gives way to the export it made (spec 016 FR-007).
                                is ExportRequestEffect.OpenDetail -> {
                                    appStack.removeAt(appStack.lastIndex)
                                    appStack.add(ExportDetailKey(effect.id))
                                }
                                ExportRequestEffect.OpenExports -> {
                                    appStack.removeAt(appStack.lastIndex)
                                    if (appStack.lastOrNull() !is ExportsKey) appStack.add(ExportsKey)
                                }
                                ExportRequestEffect.NavigateBack -> appStack.pop()
                            }
                        })
                    }
                    entry<ExportDetailKey> { key ->
                        ExportDetailRoute(exportId = key.exportId, onEffect = { effect ->
                            when (effect) {
                                is ExportDetailEffect.OpenRequest -> appStack.add(ExportRequestKey(effect.walletId))
                                ExportDetailEffect.NavigateBack -> appStack.pop()
                                is ExportDetailEffect.OpenDownload -> Unit // handed to the browser by the route
                            }
                        })
                    }
                    entry<ChangePasswordKey> {
                        ChangePasswordRoute(onEffect = { effect ->
                            when (effect) {
                                ChangePasswordEffect.NavigateBack -> appStack.pop()
                            }
                        })
                    }
                    entry<DeviceSessionsKey> {
                        DeviceSessionsRoute(onEffect = { effect ->
                            when (effect) {
                                DeviceSessionsEffect.NavigateBack -> appStack.pop()
                                is DeviceSessionsEffect.Ended -> Unit // shown by the route
                            }
                        })
                    }
                    entry<PlanKey> {
                        PlanRoute(onEffect = { effect ->
                            when (effect) {
                                PlanEffect.NavigateBack -> appStack.pop()
                                PlanEffect.OpenRedeem -> appStack.add(RedeemCodeKey)
                                is PlanEffect.OpenUrl, is PlanEffect.ShowActivated -> Unit // handled by the route
                            }
                        })
                    }
                    entry<RedeemCodeKey> {
                        RedeemRoute(onEffect = { effect ->
                            when (effect) {
                                RedeemEffect.NavigateBack -> appStack.pop()
                                RedeemEffect.RequireVerification -> emailNotVerifiedVisible = true
                            }
                        })
                    }
                    entry<WalletsKey> {
                        WalletsRoute(
                            onEffect = { effect ->
                                when (effect) {
                                    WalletsEffect.OpenEditor -> appStack.add(WalletEditorKey())
                                    is WalletsEffect.OpenDetail -> appStack.add(WalletDetailKey(effect.id))
                                    WalletsEffect.NavigateBack -> appStack.pop()
                                    WalletsEffect.RequireVerification -> Unit // routed to onRequireVerification
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<WalletDetailKey> { key ->
                        WalletDetailRoute(
                            walletId = key.walletId,
                            onEffect = { effect ->
                                when (effect) {
                                    is WalletDetailEffect.OpenEditor -> appStack.add(WalletEditorKey(effect.id))
                                    is WalletDetailEffect.OpenTransactions -> appStack.add(TransactionsKey(effect.walletId))
                                    is WalletDetailEffect.OpenRecurring -> appStack.add(RecurringKey(effect.walletId))
                                    is WalletDetailEffect.NavigateMembers -> appStack.add(WalletMembersKey(effect.walletId))
                                    is WalletDetailEffect.NavigateActivity -> appStack.add(WalletActivityKey(effect.walletId))
                                    is WalletDetailEffect.OpenExport -> appStack.add(ExportRequestKey(effect.walletId))
                                    is WalletDetailEffect.OpenReport -> appStack.add(ReportsKey(effect.walletId))
                                    is WalletDetailEffect.OpenAddTransaction -> appStack.add(TransactionEditorKey(walletId = effect.walletId))
                                    WalletDetailEffect.NavigateBack -> appStack.pop()
                                    is WalletDetailEffect.ShowMessage, WalletDetailEffect.RequireVerification -> Unit // shown by the route
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<WalletEditorKey> { key ->
                        WalletEditorRoute(
                            walletId = key.walletId,
                            onEffect = { effect ->
                                when (effect) {
                                    is WalletEditorEffect.Saved, WalletEditorEffect.NavigateBack, WalletEditorEffect.NotEntitled -> appStack.pop()
                                    WalletEditorEffect.RequireVerification -> Unit // routed to onRequireVerification
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<CategoriesKey> {
                        CategoriesRoute(
                            onEffect = { effect ->
                                when (effect) {
                                    is CategoriesEffect.OpenEditor -> appStack.add(CategoryEditorKey(effect.id, effect.kind.wire))
                                    CategoriesEffect.NavigateBack -> appStack.pop()
                                    CategoriesEffect.RequireVerification -> Unit // routed to onRequireVerification
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<CategoryEditorKey> { key ->
                        CategoryEditorRoute(
                            categoryId = key.categoryId,
                            kind = key.kind,
                            onEffect = { effect ->
                                when (effect) {
                                    CategoryEditorEffect.Saved, CategoryEditorEffect.NavigateBack -> appStack.pop()
                                    is CategoryEditorEffect.ShowMessage, CategoryEditorEffect.RequireVerification -> Unit // shown by the route
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<TransactionsKey> { key ->
                        TransactionsRoute(
                            walletId = key.walletId,
                            onEffect = { effect ->
                                when (effect) {
                                    is TransactionsEffect.OpenEditor -> appStack.add(TransactionEditorKey(effect.transactionId, effect.walletId))
                                    is TransactionsEffect.OpenOccurrence -> appStack.add(OccurrenceKey(effect.id))
                                    is TransactionsEffect.OpenRecurring -> appStack.add(RecurringKey(effect.walletId))
                                    TransactionsEffect.Back -> appStack.pop()
                                    TransactionsEffect.RequireVerification -> Unit // routed to onRequireVerification
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<TransactionEditorKey> { key ->
                        TransactionEditorRoute(
                            transactionId = key.transactionId,
                            walletId = key.walletId,
                            onEffect = { effect ->
                                when (effect) {
                                    TransactionEditorEffect.Close -> appStack.pop()
                                    TransactionEditorEffect.OpenWalletEditor -> appStack.add(WalletEditorKey())
                                    is TransactionEditorEffect.MakeRecurring -> {
                                        val draft = effect.draft
                                        appStack.pop()
                                        appStack.add(
                                            RuleEditorKey(
                                                walletId = draft.walletId,
                                                prefill = RulePrefill(
                                                    walletId = draft.walletId, kind = draft.kind, amountMinor = draft.amountMinor,
                                                    categoryId = draft.categoryId, counterWalletId = draft.counterWalletId,
                                                    description = draft.description, memo = draft.memo, counterparty = draft.counterparty,
                                                    startDate = draft.date,
                                                ),
                                            ),
                                        )
                                    }
                                    is TransactionEditorEffect.OpenOccurrence -> {
                                        appStack.pop()
                                        appStack.add(OccurrenceKey(effect.id))
                                    }
                                    is TransactionEditorEffect.OpenRule -> appStack.add(RuleDetailKey(effect.id))
                                    is TransactionEditorEffect.ShowMessage, TransactionEditorEffect.RequireVerification -> Unit // shown by the route
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<BudgetsKey> {
                        BudgetsRoute(
                            onEffect = { effect ->
                                when (effect) {
                                    is BudgetsEffect.OpenEditor -> appStack.add(BudgetEditorKey(effect.id))
                                    BudgetsEffect.OpenShared -> appStack.add(SharedBudgetsKey)
                                    BudgetsEffect.NavigateBack -> appStack.pop()
                                    BudgetsEffect.RequireVerification -> Unit // routed to onRequireVerification
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<SharedBudgetsKey> {
                        SharedBudgetsRoute(
                            onEffect = { effect ->
                                when (effect) {
                                    is SharedBudgetsEffect.OpenBudget -> appStack.add(SharedBudgetKey(effect.id))
                                    SharedBudgetsEffect.OpenNew -> appStack.add(SharedBudgetEditorKey(null))
                                    SharedBudgetsEffect.NavigateBack -> appStack.pop()
                                }
                            },
                        )
                    }
                    entry<SharedBudgetKey> { key ->
                        SharedBudgetDetailRoute(
                            budgetId = key.budgetId,
                            onEffect = { effect ->
                                when (effect) {
                                    SharedBudgetDetailEffect.NavigateBack -> appStack.pop()
                                    is SharedBudgetDetailEffect.OpenEditor -> appStack.add(SharedBudgetEditorKey(effect.id))
                                    else -> Unit
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<SharedBudgetEditorKey> { key ->
                        SharedBudgetEditorRoute(
                            budgetId = key.budgetId,
                            onEffect = { effect ->
                                when (effect) {
                                    is SharedBudgetEditorEffect.Close -> {
                                        appStack.pop()
                                        effect.budgetId?.let { appStack.add(SharedBudgetKey(it)) }
                                    }
                                    SharedBudgetEditorEffect.Gone -> {
                                        // Both the editor and the budget's own screen are over.
                                        appStack.pop()
                                        if (appStack.lastOrNull() is SharedBudgetKey) appStack.pop()
                                    }
                                    else -> Unit
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<ReportsKey> { key ->
                        ReportsRoute(
                            walletId = key.walletId,
                            onEffect = { effect ->
                                when (effect) {
                                    is ReportsEffect.OpenCategories -> appStack.add(
                                        ReportCategoriesKey(
                                            walletId = effect.args.walletId,
                                            from = effect.args.from.toString(),
                                            to = effect.args.to.toString(),
                                            currency = effect.args.currency,
                                            includeScheduled = effect.args.includeScheduled,
                                            kind = effect.args.kind.wire,
                                        ),
                                    )
                                    ReportsEffect.OpenWallets -> appStack.add(WalletsKey)
                                    ReportsEffect.Close -> appStack.pop()
                                }
                            },
                        )
                    }
                    entry<ReportCategoriesKey> { key ->
                        ReportCategoriesRoute(
                            walletId = key.walletId,
                            kind = key.kind,
                            onEffect = { effect ->
                                when (effect) {
                                    ReportCategoriesEffect.Close -> appStack.pop()
                                }
                            },
                        )
                    }
                    entry<BudgetEditorKey> { key ->
                        BudgetEditorRoute(
                            budgetId = key.budgetId,
                            onEffect = { effect ->
                                if (isPromptMoment(key, effect)) offerPrompt()
                                when (effect) {
                                    BudgetEditorEffect.Saved, BudgetEditorEffect.NavigateBack -> appStack.pop()
                                    is BudgetEditorEffect.ShowMessage, BudgetEditorEffect.RequireVerification -> Unit // shown by the route
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<RecurringKey> { key ->
                        RecurringRoute(
                            walletId = key.walletId,
                            onEffect = { effect ->
                                when (effect) {
                                    is RecurringEffect.OpenRule -> appStack.add(RuleDetailKey(effect.id))
                                    is RecurringEffect.OpenOccurrence -> appStack.add(OccurrenceKey(effect.id))
                                    is RecurringEffect.OpenEditor -> appStack.add(RuleEditorKey(walletId = effect.walletId))
                                    RecurringEffect.Back -> appStack.pop()
                                    RecurringEffect.RequireVerification -> Unit // routed to onRequireVerification
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<RuleDetailKey> { key ->
                        RuleDetailRoute(
                            ruleId = key.ruleId,
                            onEffect = { effect ->
                                when (effect) {
                                    RuleDetailEffect.Close -> appStack.pop()
                                    is RuleDetailEffect.OpenEditor ->
                                        appStack.add(RuleEditorKey(walletId = effect.walletId, ruleId = effect.ruleId, fromDate = effect.fromDate))
                                    is RuleDetailEffect.OpenOccurrence -> appStack.add(OccurrenceKey(effect.id))
                                    is RuleDetailEffect.ShowMessage, RuleDetailEffect.RequireVerification -> Unit // shown by the route
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<RuleEditorKey> { key ->
                        RuleEditorRoute(
                            walletId = key.walletId,
                            ruleId = key.ruleId,
                            fromDate = key.fromDate,
                            prefill = key.prefill,
                            onEffect = { effect ->
                                if (isPromptMoment(key, effect)) offerPrompt()
                                when (effect) {
                                    RuleEditorEffect.Close -> appStack.pop()
                                    RuleEditorEffect.Created -> Unit // only marks the moment above
                                    is RuleEditorEffect.OpenFromDate -> {
                                        appStack.pop()
                                        appStack.add(RuleEditorKey(walletId = effect.walletId, ruleId = effect.ruleId, fromDate = true))
                                    }
                                    RuleEditorEffect.OpenWalletEditor -> appStack.add(WalletEditorKey())
                                    is RuleEditorEffect.ShowMessage, RuleEditorEffect.RequireVerification -> Unit // shown by the route
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<OccurrenceKey> { key ->
                        OccurrenceRoute(
                            occurrenceId = key.occurrenceId,
                            confirm = key.confirm,
                            onEffect = { effect ->
                                when (effect) {
                                    OccurrenceEffect.Close -> appStack.pop()
                                    is OccurrenceEffect.OpenTransaction -> appStack.add(TransactionEditorKey(effect.transactionId, effect.walletId))
                                    is OccurrenceEffect.OpenRule -> appStack.add(RuleDetailKey(effect.ruleId))
                                    is OccurrenceEffect.ShowMessage, OccurrenceEffect.RequireVerification -> Unit // shown by the route
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<WalletMembersKey> { key ->
                        MembersRoute(
                            walletId = key.walletId,
                            onEffect = { effect ->
                                when (effect) {
                                    is MembersEffect.OpenInvite -> appStack.add(InviteKey(effect.walletId, effect.replacing))
                                    MembersEffect.OpenPlan -> appStack.add(PlanKey)
                                    // The wallet is gone from the phone: back to the wallet list, which says "You left <wallet>" (FR-019).
                                    is MembersEffect.Left -> appStack.backToWallets()
                                    MembersEffect.NavigateBack -> appStack.pop()
                                    is MembersEffect.Share, is MembersEffect.Copy, is MembersEffect.ShowMessage, MembersEffect.ShowVerifyPrompt -> Unit // handled by the route
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<InviteKey> { key ->
                        InviteRoute(
                            walletId = key.walletId,
                            replacing = key.replacing,
                            onEffect = { effect ->
                                when (effect) {
                                    is InviteEffect.Close -> appStack.pop()
                                    InviteEffect.OpenPlan -> appStack.add(PlanKey)
                                    is InviteEffect.Share, is InviteEffect.Copy, InviteEffect.Copied, InviteEffect.ShowVerifyPrompt -> Unit // handled by the route
                                }
                            },
                            onRequireVerification = { emailNotVerifiedVisible = true },
                        )
                    }
                    entry<SharedBudgetInvitationKey> { key ->
                        SharedBudgetInvitationRoute(
                            token = key.token,
                            onEffect = { effect ->
                                when (effect) {
                                    is SharedBudgetInvitationEffect.OpenBudget -> {
                                        if (appStack.lastOrNull() is SharedBudgetInvitationKey) appStack.removeAt(appStack.lastIndex)
                                        appStack.add(SharedBudgetKey(effect.budgetId))
                                    }
                                    SharedBudgetInvitationEffect.NavigateSignIn -> authStack.add(SignInKey())
                                    SharedBudgetInvitationEffect.NavigateRegister -> authStack.add(RegisterKey())
                                    SharedBudgetInvitationEffect.Close -> activeStack.pop()
                                }
                            },
                        )
                    }
                    entry<InvitationKey> { key ->
                        InvitationRoute(
                            token = key.token,
                            onEffect = { effect ->
                                if (isPromptMoment(key, effect)) offerPrompt()
                                when (effect) {
                                    is InvitationEffect.OpenWallet -> {
                                        if (appStack.lastOrNull() is InvitationKey) appStack.removeAt(appStack.lastIndex)
                                        appStack.add(WalletDetailKey(effect.walletId))
                                    }
                                    InvitationEffect.NavigateSignIn -> authStack.add(SignInKey())
                                    InvitationEffect.NavigateRegister -> authStack.add(RegisterKey())
                                    is InvitationEffect.Close -> activeStack.pop()
                                }
                            },
                        )
                    }
                    entry<WalletActivityKey> { key ->
                        ActivityRoute(
                            walletId = key.walletId,
                            onEffect = { effect ->
                                when (effect) {
                                    is ActivityEffect.OpenTransaction -> appStack.add(TransactionEditorKey(effect.transactionId, effect.walletId))
                                    ActivityEffect.NavigateBack -> appStack.pop()
                                }
                            },
                        )
                    }
                    entry<NotificationSettingsKey> {
                        NotificationSettingsRoute(onEffect = { effect ->
                            when (effect) {
                                NotificationSettingsEffect.NavigateBack -> appStack.pop()
                                else -> Unit // handled by the route
                            }
                        })
                    }
                    entry<NotificationOpenKey> { key ->
                        NotificationOpenRoute(route = key.route, nonce = key.nonce, onEffect = { effect ->
                            when (effect) {
                                NotificationOpenEffect.Close -> appStack.pop()
                                // The target takes this screen's place, so Back returns to where the person was (FR-032).
                                is NotificationOpenEffect.Replace -> {
                                    val at = appStack.indexOf(key)
                                    if (at >= 0) {
                                        appStack.removeAt(at)
                                        appStack.openPushTarget(effect.target)
                                    }
                                }
                            }
                        })
                    }
                    entry<InvitationNoticeKey> {
                        InvitationNoticeRoute(onEffect = { effect ->
                            when (effect) {
                                InvitationNoticeEffect.Close -> appStack.pop()
                            }
                        })
                    }
                    entry<SimulatePushKey> {
                        SimulatePushRoute(onEffect = { effect ->
                            when (effect) {
                                SimulatePushEffect.NavigateBack -> activeStack.pop()
                            }
                        })
                    }
                    entry<PlayBillingDevKey> {
                        PlayBillingDevRoute(onEffect = { effect ->
                            when (effect) {
                                PlayBillingDevEffect.NavigateBack -> activeStack.pop()
                            }
                        })
                    }
                    entry<UpdateRequiredKey> { key -> UpdateRequiredRoute(key.minimumVersion) }
                    entry<DevToolsKey> {
                        DevToolsRoute(onEffect = { effect ->
                            when (effect) {
                                DevToolsEffect.NavigateCatalog -> activeStack.add(CatalogKey)
                                DevToolsEffect.NavigatePasteLink -> activeStack.add(PasteLinkKey)
                                DevToolsEffect.NavigateSimulatePush -> activeStack.add(SimulatePushKey)
                                DevToolsEffect.NavigatePlayBilling -> activeStack.add(PlayBillingDevKey)
                                DevToolsEffect.NavigateBack -> activeStack.pop()
                            }
                        })
                    }
                    entry<ServerAddressKey> {
                        ServerAddressRoute(onEffect = { effect ->
                            when (effect) {
                                ServerAddressEffect.NavigateBack -> activeStack.pop()
                            }
                        })
                    }
                    entry<PasteLinkKey> {
                        PasteLinkRoute(onEffect = { effect ->
                            when (effect) {
                                PasteLinkEffect.NavigateBack -> activeStack.pop()
                                is PasteLinkEffect.SubmitLink -> Unit // handed to DeepLinkBus by the route
                            }
                        })
                    }
                    entry<VerifyEmailKey> { key ->
                        VerifyEmailRoute(token = key.token, onEffect = { effect ->
                            when (effect) {
                                VerifyEmailEffect.NavigateHome -> appStack.resetTo(HomeKey)
                                VerifyEmailEffect.NavigateSignIn -> authStack.resetTo(EntryKey, SignInKey())
                            }
                        })
                    }
                    entry<ForgotPasswordKey> { key ->
                        ForgotPasswordRoute(prefillEmail = key.prefillEmail, onEffect = { effect ->
                            when (effect) {
                                is ForgotPasswordEffect.NavigateCheckInbox -> activeStack.add(CheckInboxKey(effect.email, CheckInboxKind.Reset))
                                ForgotPasswordEffect.NavigateBack -> activeStack.pop()
                            }
                        })
                    }
                    entry<ResetPasswordKey> { key ->
                        ResetPasswordRoute(token = key.token, onEffect = { effect ->
                            when (effect) {
                                // Signed-in phones stay signed in until the server's session end reaches
                                // them as a 401 (FR-019); signed-out ones go straight to sign-in.
                                ResetPasswordEffect.NavigateSignIn ->
                                    if (sessionManager.state.value is AuthState.SignedIn) {
                                        appStack.resetTo(HomeKey)
                                    } else {
                                        authStack.resetTo(EntryKey, SignInKey(reason = SignOutReason.PasswordReset))
                                    }
                                ResetPasswordEffect.NavigateForgotPassword -> activeStack.add(ForgotPasswordKey())
                                ResetPasswordEffect.NavigateBack -> activeStack.pop()
                            }
                        })
                    }
                    entry<CatalogKey> {
                        ComponentCatalogRoute(onEffect = { effect ->
                            when (effect) {
                                ComponentCatalogEffect.NavigateBack -> activeStack.pop()
                            }
                        })
                    }
                },
            )
        }
        if (tab != null) {
            AppTabBar(
                selected = tab,
                onSelect = { target ->
                    if (target != tab) {
                        when (target) {
                            MainTab.Home -> appStack.resetTo(HomeKey)
                            MainTab.Wallets -> appStack.resetTo(HomeKey, WalletsKey)
                            MainTab.Budgets -> appStack.resetTo(HomeKey, BudgetsKey)
                            MainTab.Reports -> appStack.resetTo(HomeKey, ReportsKey())
                        }
                    }
                },
                onAdd = {
                    val walletId = when (val key = appStack.lastOrNull()) {
                        is TransactionsKey -> key.walletId
                        is WalletDetailKey -> key.walletId
                        else -> null
                    }
                    appStack.add(TransactionEditorKey(walletId = walletId))
                },
                labels = mapOf(
                    MainTab.Home to stringResource(Res.string.home_title),
                    MainTab.Wallets to stringResource(Res.string.home_wallets),
                    MainTab.Budgets to stringResource(Res.string.budgets_home_row),
                    MainTab.Reports to stringResource(Res.string.reports_home_row),
                ),
                addDescription = stringResource(Res.string.home_add_transaction),
            )
        }
    }
}

private fun SnapshotStateList<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}

/** Back to the wallet list, adding it when the wallet was reached another way (spec 013 FR-019). */
private fun SnapshotStateList<NavKey>.backToWallets() {
    val index = indexOfLast { it is WalletsKey }
    if (index >= 0) {
        while (lastIndex > index) removeAt(lastIndex)
    } else {
        while (size > 1) removeAt(lastIndex)
        add(WalletsKey)
    }
}

private fun SnapshotStateList<NavKey>.resetTo(vararg keys: NavKey) {
    if (toList() == keys.toList()) return
    clear()
    addAll(keys)
}

/** The tab a top-level screen belongs to; null for a screen without the tab bar. */
private fun tabOf(key: NavKey): MainTab? = when (key) {
    is HomeKey -> MainTab.Home
    is WalletsKey, is WalletDetailKey, is TransactionsKey -> MainTab.Wallets
    is BudgetsKey, is SharedBudgetsKey, is SharedBudgetKey -> MainTab.Budgets
    is ReportsKey, is ReportCategoriesKey -> MainTab.Reports
    else -> null
}
