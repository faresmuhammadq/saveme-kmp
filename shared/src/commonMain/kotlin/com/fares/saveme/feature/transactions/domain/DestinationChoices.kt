package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.wallets.domain.model.WalletList
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView

/** Whether the person may add to, change or delete transactions of this wallet: owner or editor (backend spec 012). */
internal fun WalletView.canWrite(): Boolean = server.myRole == WalletRole.Owner || server.myRole == WalletRole.Editor

internal fun WalletView.toChoice() = WalletChoice(id, shown.name, shown.currency)

/**
 * The wallets a transfer from [sourceWalletId] can go to (FR-010): active, visible, same currency,
 * owner or editor, other than the source. A transfer's source is never a choice for itself.
 */
object DestinationChoices {
    fun of(list: WalletList, sourceWalletId: String): List<WalletChoice> {
        val source = (list.active + list.archived).firstOrNull { it.id == sourceWalletId } ?: return emptyList()
        return list.active
            .filter { it.id != sourceWalletId && it.shown.currency == source.shown.currency && it.canWrite() }
            .map { it.toChoice() }
    }
}
