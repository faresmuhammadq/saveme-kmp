package com.fares.saveme.feature.wallets.domain.model

/** The wallet type values of wallets.yaml; a value this app doesn't know is [Unknown] and stays as the server sent it. */
enum class WalletType(val wire: String, val isLiability: Boolean) {
    Cash("cash", false),
    Checking("checking", false),
    Savings("savings", false),
    CreditCard("credit_card", true),
    Loan("loan", true),
    Investment("investment", false),
    Unknown("", false),
    ;

    companion object {
        /** The types a person can choose when creating a wallet. */
        val selectable: List<WalletType> = entries.filter { it != Unknown }

        fun fromWire(value: String): WalletType = entries.firstOrNull { it != Unknown && it.wire == value } ?: Unknown
    }
}

/** The caller's role in a wallet. Only the owner changes the wallet itself (backend spec 010 matrix). */
enum class WalletRole(val wire: String) {
    Owner("owner"),
    Editor("editor"),
    Viewer("viewer"),
    ;

    val canEdit: Boolean get() = this == Owner

    companion object {
        /** An unknown role reads as the least powerful one. */
        fun fromWire(value: String): WalletRole = entries.firstOrNull { it.wire == value } ?: Viewer
    }
}

/** Wallet members the editor and the server's validation errors can point at. */
enum class WalletField(val pointer: String) {
    Name("/name"),
    Currency("/currency"),
    Type("/type"),
    OpeningBalance("/opening_balance_minor"),
    Icon("/icon"),
    Color("/color"),
    ;

    companion object {
        fun fromPointer(pointer: String?): WalletField? = entries.firstOrNull { it.pointer == pointer }
    }
}
