package com.fares.saveme.feature.categories.domain.model

/**
 * What reports need to group and name categories (spec 014 research R-08): [keyOf] is the grouping key the server uses (a
 * platform category's `system_key`, any other category's id) and [display] resolves the name, icon and colour: own
 * categories, platform defaults in the app language, the category references of every covered wallet, else "unavailable".
 * The machine key is never shown.
 */
class ReportLabels(
    private val key: (String) -> String,
    private val displays: CategoryDisplays,
) {
    fun keyOf(categoryId: String): String = key(categoryId)

    fun display(categoryId: String): CategoryDisplay = displays.resolve(categoryId)

    companion object {
        /** Every id is its own key; nothing resolves. For tests and for a service that does not know better. */
        fun identity(displays: CategoryDisplays = CategoryDisplays({ unavailable(it) })) = ReportLabels({ it }, displays)

        private fun unavailable(id: String) = CategoryDisplay(
            id = id, name = "", iconKey = null, colorHex = null, kindWire = null, source = CategorySource.Unavailable, archived = false,
        )
    }
}
