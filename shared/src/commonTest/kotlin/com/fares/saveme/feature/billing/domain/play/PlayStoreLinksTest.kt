package com.fares.saveme.feature.billing.domain.play

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayStoreLinksTest {

    @Test
    fun theLinkCarriesThePackageAndTheProduct() = assertEquals(
        "https://play.google.com/store/account/subscriptions?package=com.fares.saveme&sku=plus_monthly",
        PlayStoreLinks.subscriptions("com.fares.saveme", "plus_monthly"),
    )

    @Test
    fun withoutAKnownProductThereIsNoSku() = assertEquals(
        "https://play.google.com/store/account/subscriptions?package=com.fares.saveme.dev",
        PlayStoreLinks.subscriptions("com.fares.saveme.dev", null),
    )

    @Test
    fun aBlankProductHasNoSkuEither() = assertEquals(
        "https://play.google.com/store/account/subscriptions?package=com.fares.saveme",
        PlayStoreLinks.subscriptions("com.fares.saveme", " "),
    )

    @Test
    fun valuesAreEncoded() = assertEquals(
        "https://play.google.com/store/account/subscriptions?package=a%20b%26c&sku=x%2Fy%3Dz",
        PlayStoreLinks.subscriptions("a b&c", "x/y=z"),
    )

    @Test
    fun withNoApplicationIdOnlyTheSkuRemains() = assertEquals(
        "https://play.google.com/store/account/subscriptions?sku=plus_monthly",
        PlayStoreLinks.subscriptions("", "plus_monthly"),
    )
}
