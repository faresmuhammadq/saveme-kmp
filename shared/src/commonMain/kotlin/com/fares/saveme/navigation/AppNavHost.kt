package com.fares.saveme.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.fares.saveme.feature.account.ui.profile.ProfileEffect
import com.fares.saveme.feature.account.ui.profile.ProfileRoute
import com.fares.saveme.feature.account.ui.sessions.DeviceSessionsEffect
import com.fares.saveme.feature.account.ui.sessions.DeviceSessionsRoute
import com.fares.saveme.feature.billing.ui.plan.PlanEffect
import com.fares.saveme.feature.categories.ui.editor.CategoryEditorEffect
import com.fares.saveme.feature.categories.ui.editor.CategoryEditorRoute
import com.fares.saveme.feature.categories.ui.list.CategoriesEffect
import com.fares.saveme.feature.categories.ui.list.CategoriesRoute
import com.fares.saveme.feature.wallets.ui.detail.WalletDetailEffect
import com.fares.saveme.feature.wallets.ui.detail.WalletDetailRoute
import com.fares.saveme.feature.wallets.ui.editor.WalletEditorEffect
import com.fares.saveme.feature.wallets.ui.editor.WalletEditorRoute
import com.fares.saveme.feature.wallets.ui.list.WalletsEffect
import com.fares.saveme.feature.wallets.ui.list.WalletsRoute
import com.fares.saveme.feature.billing.ui.plan.PlanRoute
import com.fares.saveme.feature.auth.domain.AuthLink
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
            is AuthState.SignedIn -> appStack.resetTo(HomeKey)
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
            val key = when (val link = linkParser.parse(raw)) {
                is AuthLink.VerifyEmail -> VerifyEmailKey(link.token)
                is AuthLink.ResetPassword -> ResetPasswordKey(link.token)
                null -> return@collect
            }
            if (sessionManager.state.value is AuthState.SignedIn) {
                appStack.add(key)
            } else {
                // Replace a half-filled form on top, but keep the entry screen underneath.
                if (authStack.size > 1) authStack.removeAt(authStack.lastIndex)
                authStack.add(key)
            }
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

    if (authState == AuthState.Unknown) {
        // Nothing until the stored session is read, so the wrong screen never flashes (FR-011).
        Box(Modifier.fillMaxSize().background(AppTheme.colors.background))
        return
    }

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
                        // Home carries the pending-deletion notice from here on (FR-019).
                        DeleteAccountEffect.Finished -> appStack.resetTo(HomeKey)
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
            entry<UpdateRequiredKey> { key -> UpdateRequiredRoute(key.minimumVersion) }
            entry<DevToolsKey> {
                DevToolsRoute(onEffect = { effect ->
                    when (effect) {
                        DevToolsEffect.NavigateCatalog -> activeStack.add(CatalogKey)
                        DevToolsEffect.NavigatePasteLink -> activeStack.add(PasteLinkKey)
                        DevToolsEffect.NavigateBack -> activeStack.pop()
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

private fun SnapshotStateList<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}

private fun SnapshotStateList<NavKey>.resetTo(vararg keys: NavKey) {
    if (toList() == keys.toList()) return
    clear()
    addAll(keys)
}
