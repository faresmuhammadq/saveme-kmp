package com.fares.saveme.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

    // Any call can answer 426; the blocking screen goes on whichever stack is showing (FR-018).
    LaunchedEffect(apiEvents) {
        apiEvents.events.filterIsInstance<ApiEvent.UpdateRequired>().collect { event ->
            val stack = if (sessionManager.state.value is AuthState.SignedIn) appStack else authStack
            if (stack.lastOrNull() !is UpdateRequiredKey) stack.add(UpdateRequiredKey(event.minimumVersion))
        }
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
                        EntryEffect.NavigateRegister -> Unit // US3
                        EntryEffect.NavigateDevTools -> authStack.add(DevToolsKey)
                    }
                })
            }
            entry<SignInKey> { key ->
                SignInRoute(args = SignInArgs(key.prefillEmail, key.reason), onEffect = { effect ->
                    when (effect) {
                        is SignInEffect.NavigateForgotPassword -> Unit // US4
                        SignInEffect.NavigateRegister -> Unit // US3
                        SignInEffect.NavigateBack -> authStack.pop()
                    }
                })
            }
            entry<HomeKey> {
                HomeRoute(onEffect = { effect ->
                    when (effect) {
                        HomeEffect.NavigateAccount -> appStack.add(AccountKey)
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
            entry<UpdateRequiredKey> { key -> UpdateRequiredRoute(key.minimumVersion) }
            entry<DevToolsKey> {
                DevToolsRoute(onEffect = { effect ->
                    when (effect) {
                        DevToolsEffect.NavigateCatalog -> activeStack.add(CatalogKey)
                        DevToolsEffect.NavigatePasteLink -> Unit // US3
                        DevToolsEffect.NavigateBack -> activeStack.pop()
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
