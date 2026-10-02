package com.fares.saveme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.core.locale.AppLocaleProvider
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.AccountSyncCoordinator
import com.fares.saveme.feature.billing.domain.BillingSyncCoordinator
import com.fares.saveme.navigation.AppNavHost
import org.koin.compose.koinInject

@Composable
fun App(
    languages: AppLanguageStore = koinInject(),
    accountSync: AccountSyncCoordinator = koinInject(),
    billingSync: BillingSyncCoordinator = koinInject(),
) {
    val override by languages.language.collectAsStateWithLifecycle()
    LaunchedEffect(languages) { languages.load() }
    // Back in the foreground: pull profile and account state, push any waiting edit (spec 002 FR-011, FR-021).
    LifecycleStartEffect(accountSync) {
        accountSync.onForeground()
        billingSync.onForeground()
        onStopOrDispose { }
    }
    AppLocaleProvider(override = override, effective = override ?: languages.device()) {
        AppTheme {
            AppNavHost()
        }
    }
}
