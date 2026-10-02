package com.fares.saveme.feature.account.data

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.sync.BatchItem
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.account.data.dto.ProfileDto
import com.fares.saveme.feature.account.data.dto.ProfileWriteRequest
import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.account.domain.model.ProfileField
import kotlinx.serialization.json.JsonElement

/** The profile's way through the sync engine (spec 004 research R-08): `PUT /me/profile`. */
class ProfileSyncHandler(
    private val repository: ProfileRepository,
    private val languages: AppLanguageStore,
) : SyncHandler {

    override val recordType: String = RECORD_TYPE

    private fun writeRequest(change: PendingChange): ProfileWriteRequest {
        val draft = AppJson.decodeFromString(ProfileDraft.serializer(), change.payloadJson)
        return ProfileWriteRequest(
            displayName = draft.displayName,
            locale = draft.locale,
            timezone = draft.timezone,
            defaultCurrency = draft.defaultCurrency,
            budgetPeriodStartDay = draft.budgetStartDay,
            version = change.baseVersion,
        )
    }

    /** The same body `PUT /me/profile` sends; the item's id is the person's profile id (spec 009 FR-024). */
    override suspend fun batchItem(change: PendingChange): BatchItem =
        BatchItem(RECORD_TYPE, BatchOp.Upsert, change.recordId, walletId = null, data = writeRequest(change).toJson())

    override suspend fun send(change: PendingChange): ApiResult<JsonElement> {
        val request = writeRequest(change)
        return when (val result = repository.push(request)) {
            is ApiResult.Success -> ApiResult.Success(AppJson.encodeToJsonElement(ProfileDto.serializer(), result.value))
            is ApiResult.Failure -> result
            ApiResult.Offline -> ApiResult.Offline
            ApiResult.Timeout -> ApiResult.Timeout
        }
    }

    override suspend fun applyServerCopy(json: JsonElement) {
        repository.storeServerCopy(AppJson.decodeFromJsonElement(ProfileDto.serializer(), json))
    }

    /** The edit is gone: the app language follows the server copy again. */
    override suspend fun onDiscarded(recordId: String) {
        repository.read()?.let { row -> AppLanguage.fromTag(row.locale)?.let { languages.set(it) } }
    }

    override fun refusalFields(problem: ProblemDetails): Map<String, String> =
        problem.errors.orEmpty()
            .mapNotNull { e -> ProfileField.fromPointer(e.pointer)?.let { it.name to e.code } }
            .toMap()

    companion object {
        const val RECORD_TYPE = "profile"
    }
}
