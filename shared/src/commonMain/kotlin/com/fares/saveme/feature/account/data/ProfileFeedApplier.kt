package com.fares.saveme.feature.account.data

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.data.dto.ProfileDto
import kotlinx.serialization.json.JsonElement

/**
 * The person's profile from their change feed (spec 009 R-13): applied when its version is the same
 * or newer; the app language follows it unless a profile edit waits, whose language already applies.
 *
 * @param hasPendingEdit whether the sync queue holds a profile change; resolved on use because the
 *   engine is built from the sync handlers.
 */
class ProfileFeedApplier(
    private val repository: ProfileRepository,
    private val languages: AppLanguageStore,
    private val hasPendingEdit: suspend (profileId: String) -> Boolean,
) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.User
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) {
            val dto = AppJson.decodeFromJsonElement(ProfileDto.serializer(), record)
            if (repository.storeFeedCopy(dto) && !hasPendingEdit(dto.id)) {
                AppLanguage.fromTag(dto.locale)?.let { languages.set(it) }
            }
        }
    }

    /** There is one profile and it is never deleted. */
    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = Unit

    companion object {
        const val ROW_TYPE = "profile"
    }
}
