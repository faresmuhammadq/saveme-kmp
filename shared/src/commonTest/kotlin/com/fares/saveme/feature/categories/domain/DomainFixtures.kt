package com.fares.saveme.feature.categories.domain

import com.fares.saveme.feature.categories.domain.model.Category
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategoryReference
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.categories.domain.model.DefaultCategory
import com.fares.saveme.feature.categories.domain.model.SyncState
import kotlin.time.Instant

fun ownView(
    id: String,
    name: String = id,
    kind: String = "expense",
    archived: Boolean = false,
    created: String = "2026-09-01T00:00:00Z",
    pending: CategoryDraft? = null,
    pendingDelete: Boolean = false,
    state: SyncState = SyncState.None,
    inUse: Boolean = false,
    version: Long = 1,
    icon: String? = "restaurant",
    color: String? = "#1F7F8C",
) = CategoryView(
    Category(id, kind, name, icon, color, if (archived) Instant.parse("2026-09-05T00:00:00Z") else null, inUse, version, Instant.parse(created)),
    pending, pendingDelete, state,
)

fun defaultCategory(
    id: String,
    en: String = id,
    ar: String = "ع-$id",
    kind: String = "expense",
    position: Int = 1,
    retired: Boolean = false,
) = DefaultCategory(id, "key_$id", kind, ar, en, "cart", "#2E7D32", position, if (retired) Instant.parse("2026-09-30T00:00:00Z") else null)

fun reference(categoryId: String, walletId: String = "w1", name: String = "Partner $categoryId", kind: String = "expense") =
    CategoryReference(walletId, categoryId, "u2", kind, name, "restaurant", "#1F7F8C")
