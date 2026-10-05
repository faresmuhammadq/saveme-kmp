package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.recurring.domain.RecurringService
import com.fares.saveme.feature.sharing.data.dto.ActivityEntryDto
import com.fares.saveme.feature.sharing.domain.model.SubjectKind
import com.fares.saveme.feature.transactions.domain.TransactionsService
import com.fares.saveme.feature.wallets.domain.WalletsService
import kotlinx.coroutines.flow.first

/** Names what an activity page is about from the phone's own copy (spec 013 R-18). */
fun interface ActivityNameSource {
    suspend fun namesFor(walletId: String, entries: List<ActivityEntryDto>): ActivityNames
}

/**
 * One pass per page through the services that own the records (P-10.4): wallets (members, wallet name), transactions, recurring
 * rules and occurrences, with category names from categories. A record the phone does not hold stays unnamed.
 */
class DefaultActivityNameSource(
    private val wallets: WalletsService,
    private val transactions: TransactionsService,
    private val recurring: RecurringService,
    private val categories: CategoriesService,
) : ActivityNameSource {

    override suspend fun namesFor(walletId: String, entries: List<ActivityEntryDto>): ActivityNames {
        val members = wallets.observeMembers(walletId).first().associate { it.userId to it.displayName?.takeIf(String::isNotBlank) }
        val walletName = wallets.observeWallet(walletId).first()?.shown?.name
        val categoryNames = if (entries.any { it.subjectType in RECORD_KINDS }) categories.observeDisplays(walletId).first() else null
        fun category(id: String?): String? = id?.let { categoryNames?.resolve(it)?.name?.takeIf(String::isNotBlank) }

        fun ids(kind: SubjectKind) = entries.filter { it.subjectType == kind.wire }.map { it.subjectId }.distinct()
        val tx = buildMap {
            for (id in ids(SubjectKind.Transaction)) {
                val view = transactions.observeTransaction(id, walletId).first() ?: continue
                put(id, view.shown.description?.takeIf(String::isNotBlank) ?: category(view.shown.categoryId))
            }
        }
        val rules = buildMap {
            for (id in ids(SubjectKind.RecurringRule)) {
                val view = recurring.observeRule(id).first() ?: continue
                put(id, view.shown.description?.takeIf(String::isNotBlank) ?: category(view.shown.categoryId))
            }
        }
        val occurrences = buildMap {
            for (id in ids(SubjectKind.Occurrence)) {
                val view = recurring.observeOccurrence(id).first() ?: continue
                put(id, view.shownDescription?.takeIf(String::isNotBlank) ?: view.ruleDescription?.takeIf(String::isNotBlank) ?: category(view.shownCategoryId))
            }
        }
        return ActivityNames(members = members, transactions = tx, rules = rules, occurrences = occurrences, walletName = walletName)
    }

    private companion object {
        val RECORD_KINDS = setOf(SubjectKind.Transaction.wire, SubjectKind.RecurringRule.wire, SubjectKind.Occurrence.wire)
    }
}
