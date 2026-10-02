package com.fares.saveme.feature.categories.domain.model

import kotlin.time.Instant

/** The two kinds of category; a value this app doesn't know reads as null and is kept as the server sent it. */
enum class CategoryKind(val wire: String) {
    Expense("expense"),
    Income("income"),
    ;

    companion object {
        fun fromWire(value: String?): CategoryKind? = entries.firstOrNull { it.wire == value }
    }
}

/** Category members the editor and the server's validation errors can point at. */
enum class CategoryField(val fieldName: String) {
    Name("name"),
    Kind("kind"),
    Icon("icon"),
    Color("color"),
    ;

    companion object {
        /** The plain field name the data layer puts in a refusal. */
        fun fromName(name: String?): CategoryField? = entries.firstOrNull { it.fieldName == name }
    }
}

/** Where a displayed category came from (research R-10). */
enum class CategorySource { Own, Default, Partner, Unavailable }

/** The server's copy of an own category, as last read. */
data class Category(
    val id: String,
    /** The wire value, kept so an unknown kind is never sent back changed. */
    val kindWire: String,
    val name: String,
    val icon: String?,
    val color: String?,
    val archivedAt: Instant?,
    val inUse: Boolean,
    /** 0 = a category made on this phone that never reached the server. */
    val version: Long,
    val createdAt: Instant,
) {
    val kind: CategoryKind? get() = CategoryKind.fromWire(kindWire)

    fun toDraft() = CategoryDraft(kindWire, name, icon, color, archive = archivedAt != null)
}

/** One entry of the platform's default set. */
data class DefaultCategory(
    val id: String,
    val systemKey: String,
    val kindWire: String,
    val nameAr: String,
    val nameEn: String,
    val icon: String,
    val color: String,
    val position: Int,
    val retiredAt: Instant?,
) {
    val kind: CategoryKind? get() = CategoryKind.fromWire(kindWire)
}

/** A partner's category as published into a wallet; read-only. */
data class CategoryReference(
    val walletId: String,
    val categoryId: String,
    val ownerUserId: String,
    val kindWire: String,
    val name: String,
    val icon: String?,
    val color: String?,
)

/** The whole writable category, as the person edits it and as it waits to sync. */
data class CategoryDraft(
    val kind: String,
    val name: String,
    val icon: String?,
    val color: String?,
    /** Archive after saving. Archiving is one-way, so a draft never sets this back to false once true. */
    val archive: Boolean = false,
) {
    /** Whether every writable member equals the stored category's, leaving [archive] out. */
    fun sameFieldsAs(category: Category): Boolean =
        kind == category.kindWire && name == category.name && icon == category.icon && color == category.color
}
