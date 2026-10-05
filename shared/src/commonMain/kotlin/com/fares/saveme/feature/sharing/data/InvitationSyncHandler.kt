package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.RecordKey
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.sharing.data.dto.InvitationDto
import com.fares.saveme.feature.sharing.data.dto.InvitationWriteRequest
import kotlinx.serialization.json.JsonElement

/**
 * An invitation's way through the sync engine (research R-03, R-04): a `PUT` to create (version 0, the address an explicit
 * null for a link) or a `DELETE` with a JSON body to withdraw, always per record - the backend refuses invitations in a batch,
 * so [batchItem] stays null. An invitation never changes once created, so a `409` means it was closed: [acceptsClash] takes the
 * tombstone. The one-time link of a new link invitation goes to [onShareLink] (a lambda: `data` imports no domain, P-10.6) before
 * the record is returned; a `200` replay carries none. Nothing here logs a link or an address.
 *
 * @param walletVersion the stored version of a wallet, null when it is unknown; a wallet made on this phone and not yet accepted
 *   (version 0) holds its invitations back (spec 009 FR-022).
 */
class InvitationSyncHandler(
    private val repository: InvitationRepository,
    private val api: InvitationApi,
    private val onShareLink: (invitationId: String, url: String) -> Unit,
    private val walletVersion: suspend (walletId: String) -> Long?,
) : SyncHandler {

    override val recordType: String = RECORD_TYPE

    override val canRestore: Boolean = false

    override suspend fun send(change: PendingChange): ApiResult<JsonElement> {
        val walletId = change.scope.id
        if (change.isDelete) return api.delete(walletId, change.recordId, change.baseVersion).toJson()
        val payload = AppJson.decodeFromString(InvitationPayload.serializer(), change.payloadJson)
        return when (val put = api.put(walletId, change.recordId, InvitationWriteRequest(payload.role, payload.email, change.baseVersion))) {
            is ApiResult.Success -> {
                put.value.shareUrl?.let { onShareLink(change.recordId, it) }
                ApiResult.Success(AppJson.encodeToJsonElement(InvitationDto.serializer(), put.value.invitation))
            }
            is ApiResult.Failure -> put
            ApiResult.Offline -> ApiResult.Offline
            ApiResult.Timeout -> ApiResult.Timeout
        }
    }

    override suspend fun applyServerCopy(json: JsonElement) {
        repository.storeServerCopy(AppJson.decodeFromJsonElement(InvitationDto.serializer(), json))
    }

    /** A closed invitation cannot be reopened: the server's tombstone is taken without asking (FR-008). */
    override suspend fun acceptsClash(change: PendingChange, current: JsonElement): Boolean = true

    /** A wallet made on this phone and not yet accepted holds the invitation back. */
    override suspend fun prerequisites(change: PendingChange): List<RecordKey> {
        if (change.isDelete) return emptyList()
        return if (walletVersion(change.scope.id) == 0L) listOf(RecordKey(WALLET_TYPE, change.scope.id)) else emptyList()
    }

    /** An invitation that never reached the server has no copy to fall back to: it disappears. */
    override suspend fun onDiscarded(recordId: String) {
        repository.removePlaceholder(recordId)
    }

    override fun refusalFields(problem: ProblemDetails): Map<String, String> =
        problem.errors.orEmpty()
            .mapNotNull { e -> FIELD_POINTERS[e.pointer]?.let { it to e.code } }
            .toMap()

    private fun ApiResult<InvitationDto>.toJson(): ApiResult<JsonElement> = when (this) {
        is ApiResult.Success -> ApiResult.Success(AppJson.encodeToJsonElement(InvitationDto.serializer(), value))
        is ApiResult.Failure -> this
        ApiResult.Offline -> ApiResult.Offline
        ApiResult.Timeout -> ApiResult.Timeout
    }

    companion object {
        const val RECORD_TYPE = "invitation"

        /** The record type of the wallets an invitation belongs to; a local constant, not an import of that feature. */
        private const val WALLET_TYPE = "wallet"

        /** Validation pointers the invite screen can show next to a field. */
        private val FIELD_POINTERS = mapOf("/role" to "role", "/invitee_email" to "email")
    }
}
