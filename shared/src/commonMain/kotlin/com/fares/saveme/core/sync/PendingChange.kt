package com.fares.saveme.core.sync

import com.fares.saveme.core.network.AppJson
import kotlinx.serialization.Serializable
import kotlin.time.Instant

enum class ChangeState { Waiting, Sending, Clash, Refused }

/**
 * The sync scopes of the backend: the person's own, one per wallet, and from backend ADR-0027 one per Shared Budget (a third kind
 * of feed scope that a person reads only as one of its members).
 */
enum class ScopeKind(val wire: String) {
    User("user"),
    Wallet("wallet"),
    SharedBudget("shared_budget"),
    ;

    companion object {
        fun fromWire(value: String): ScopeKind = entries.first { it.wire == value }
    }
}

data class SyncScope(val kind: ScopeKind, val id: String) {
    /** Key of this scope's row in `sync_scope`. */
    val key: String get() = "${kind.wire}:$id"
}

/**
 * Why the server refused a change for good; [fields] are the feature's own field names and
 * [requestId] is the server's reference for support (P-13.2).
 */
@Serializable
data class Refusal(
    val code: String,
    val fields: Map<String, String> = emptyMap(),
    val requestId: String? = null,
    /** `Problem.feature` of a `FEATURE_NOT_ENTITLED` answer (plain string: core names no feature type, spec 013 R-19). */
    val feature: String? = null,
)

/** One record's unsent change (data-model §3). The payload is never printed (P-09.2). */
data class PendingChange(
    val recordType: String,
    val recordId: String,
    val accountId: String,
    val scope: SyncScope,
    val baseVersion: Long,
    val payloadJson: String,
    val isDelete: Boolean,
    val restore: Boolean,
    val revision: Long,
    val createdOrder: Long,
    val state: ChangeState,
    val attempts: Int,
    val nextAttemptAt: Instant?,
    val clashJson: String?,
    val refusal: Refusal?,
    val updatedAt: Instant,
) {
    override fun toString() =
        "PendingChange($recordType/$recordId, state=$state, base=$baseVersion, revision=$revision, attempts=$attempts)"
}

internal fun PendingChangeEntity.toDomain() = PendingChange(
    recordType = recordType,
    recordId = recordId,
    accountId = accountId,
    scope = SyncScope(ScopeKind.fromWire(scopeKind), scopeId),
    baseVersion = baseVersion,
    payloadJson = payloadJson,
    isDelete = isDelete,
    restore = restore,
    revision = revision,
    createdOrder = createdOrder,
    state = ChangeState.valueOf(state),
    attempts = attempts,
    nextAttemptAt = nextAttemptAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    clashJson = clashJson,
    refusal = refusalJson?.let { runCatching { AppJson.decodeFromString(Refusal.serializer(), it) }.getOrNull() },
    updatedAt = Instant.parse(updatedAt),
)
