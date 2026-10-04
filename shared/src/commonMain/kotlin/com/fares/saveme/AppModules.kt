package com.fares.saveme

import com.fares.saveme.devtools.devToolsModule
import com.fares.saveme.feature.account.di.accountModule
import com.fares.saveme.feature.auth.di.authModule
import com.fares.saveme.feature.billing.di.billingModule
import com.fares.saveme.feature.budgets.di.budgetsModule
import com.fares.saveme.feature.categories.di.categoriesModule
import com.fares.saveme.feature.recurring.di.recurringModule
import com.fares.saveme.feature.transactions.di.transactionsModule
import com.fares.saveme.feature.wallets.di.walletsModule
import com.fares.saveme.navigation.navigationModule
import org.koin.core.module.Module

/** Every feature module the app starts with; `core` never lists these itself (P-10.7). */
val appFeatureModules: List<Module> = listOf(navigationModule, devToolsModule, authModule, accountModule, billingModule, walletsModule, categoriesModule, transactionsModule, recurringModule, budgetsModule)
