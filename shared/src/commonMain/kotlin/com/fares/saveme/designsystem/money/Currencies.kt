package com.fares.saveme.designsystem.money

/** ISO 4217 codes the app can format (digits from [MoneyFormatter]); offered as default currency. */
val knownCurrencies: List<String> = listOf(
    "EGP", "SAR", "AED", "KWD", "QAR", "BHD", "OMR", "JOD", "IQD", "LYD", "TND", "MAD", "DZD", "LBP",
    "USD", "EUR", "GBP", "CHF", "CAD", "AUD", "TRY", "JPY", "KRW", "CNY", "INR",
).sorted()
