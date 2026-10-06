package com.fares.saveme.feature.reports.di

import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.feature.account.domain.ProfileSyncer
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.reports.data.ReportsApi
import com.fares.saveme.feature.reports.domain.DefaultReportsService
import com.fares.saveme.feature.reports.domain.DevReportParityProbe
import com.fares.saveme.feature.reports.domain.LearnedPeriods
import com.fares.saveme.feature.reports.domain.NoParityProbe
import com.fares.saveme.feature.reports.domain.ReportChoicesStore
import com.fares.saveme.feature.reports.domain.ReportParityProbe
import com.fares.saveme.feature.reports.domain.ReportsCleaner
import com.fares.saveme.feature.reports.domain.ReportsService
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.ui.categories.ReportCategoriesViewModel
import com.fares.saveme.feature.reports.ui.reports.CategoryGroupKind
import com.fares.saveme.feature.reports.ui.reports.ReportsViewModel
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Reports (spec 014). Listed after the transactions, recurring, categories, wallets, budgets and sharing modules. Reports
 * keep nothing on the phone: the holders are in memory, the cleaner empties them at sign-out, and the hooks they read
 * (`ReportLedger`, `ScheduledMoney`, category labels) are bound by the features that own them. The sync engine is never
 * resolved here, so the graph keeps no cycle (spec 006 lesson).
 */
val reportsModule = module {
    single { ReportsApi(get()) }
    single { ReportChoicesStore() }
    single { LearnedPeriods() }
    single { ReportsCleaner(get(), get()) } bind UserDataCleaner::class
    // The probe reads the figure endpoints and exists only in the development build (FR-028).
    single<ReportParityProbe> { if (get<AppConfig>().environment == Environment.Dev) DevReportParityProbe(get()) else NoParityProbe }
    single<ReportsService> {
        DefaultReportsService(
            wallets = get(),
            transactions = get(),
            ledger = get(),
            scheduled = get(),
            categories = get(),
            profile = get<ProfileSyncer>().view,
            deviceZoneId = { get<ClientInfo>().timezoneId() },
            authState = get<SessionManager>().state,
            choices = get(),
            learned = get(),
            api = get(),
            runner = get(),
            probe = get(),
        )
    }
    // The screens (P-16): one store per scope, domain lambdas only. The key is the wallet's id, or empty for every wallet.
    viewModel { params ->
        val wallet: String = params[0]
        val scope = if (wallet.isEmpty()) ReportScope.AllWallets else ReportScope.Wallet(wallet)
        val service = get<ReportsService>()
        val profile = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        ReportsViewModel(
            walletId = wallet.ifEmpty { null },
            report = service.observe(scope),
            labels = service.observeLabels(scope),
            refresh = { force -> service.refresh(scope, force) },
            setRange = { service.setRange(scope, it) },
            setTrendLength = { service.setTrendLength(scope, it) },
            setCurrency = { service.setCurrency(it) },
            setIncludeScheduled = { service.setIncludeScheduled(scope, it) },
            validateCustom = { from, to -> service.validateCustom(from, to) },
            // Times show in the profile timezone (P-05); the device's until the profile is known.
            timeZoneId = profile.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() },
        )
    }
    viewModel { params ->
        val wallet: String = params[0]
        val kind: String = params[1]
        val scope = if (wallet.isEmpty()) ReportScope.AllWallets else ReportScope.Wallet(wallet)
        val service = get<ReportsService>()
        ReportCategoriesViewModel(
            initialKind = CategoryGroupKind.fromWire(kind),
            report = service.observe(scope),
            labels = service.observeLabels(scope),
        )
    }
}
