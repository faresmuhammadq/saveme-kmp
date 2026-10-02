package com.fares.saveme.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The icon keys a category can carry (spec 007, research R-13): every key the platform's default
 * set uses, so defaults and own categories share one look. An unknown or missing key falls back to
 * [AppIcons.More], the neutral icon.
 */
object CategoryIcons {
    val keys: List<String> = listOf(
        "cart", "restaurant", "bus", "fuel", "home", "bolt", "phone", "health", "school", "shirt", "sofa", "care",
        "ticket", "repeat", "plane", "child", "gift", "heart", "dots", "wallet", "store", "laptop", "chart", "undo",
    )

    fun find(key: String?): ImageVector? = when (key) {
        "cart" -> AppIcons.Cart
        "restaurant" -> AppIcons.Restaurant
        "bus" -> AppIcons.Bus
        "fuel" -> AppIcons.Fuel
        "home" -> AppIcons.Home
        "bolt" -> AppIcons.Bolt
        "phone" -> AppIcons.Phone
        "health" -> AppIcons.Health
        "school" -> AppIcons.School
        "shirt" -> AppIcons.Shirt
        "sofa" -> AppIcons.Sofa
        "care" -> AppIcons.Sparkle
        "ticket" -> AppIcons.Ticket
        "repeat" -> AppIcons.Cycle
        "plane" -> AppIcons.Plane
        "child" -> AppIcons.Smile
        "gift" -> AppIcons.Gift
        "heart" -> AppIcons.Heart
        "dots" -> AppIcons.More
        "wallet" -> AppIcons.Cash
        "store" -> AppIcons.Store
        "laptop" -> AppIcons.Laptop
        "chart" -> AppIcons.Investment
        "undo" -> AppIcons.Undo
        else -> null
    }

    /** The icon for [key], or the neutral fallback. */
    fun of(key: String?): ImageVector = find(key) ?: AppIcons.More
}
