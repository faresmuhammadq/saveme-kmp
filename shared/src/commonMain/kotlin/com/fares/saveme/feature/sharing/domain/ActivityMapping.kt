package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.feature.sharing.data.dto.ActivityEntryDto
import com.fares.saveme.feature.sharing.domain.model.ActivityAction
import com.fares.saveme.feature.sharing.domain.model.ActivityItem
import com.fares.saveme.feature.sharing.domain.model.ActivitySubject
import com.fares.saveme.feature.sharing.domain.model.Actor
import com.fares.saveme.feature.sharing.domain.model.SubjectKind
import kotlin.time.Instant

/**
 * What the phone can name for the entries of one page: a key present means the phone holds that record, its value is the name
 * to show (null when the record has none, so the kind's words are used instead). Built by [ActivityNameSource].
 */
data class ActivityNames(
    val members: Map<String, String?> = emptyMap(),
    val transactions: Map<String, String?> = emptyMap(),
    val rules: Map<String, String?> = emptyMap(),
    val occurrences: Map<String, String?> = emptyMap(),
    val walletName: String? = null,
)

/**
 * One activity entry as words (spec 013 R-18, FR-025). Pure. An action or subject kind this app does not know is skipped
 * (null). No amount is read: neither the entry nor the result has one.
 */
object ActivityMapping {

    fun map(entry: ActivityEntryDto, names: ActivityNames): ActivityItem? {
        val action = ActivityAction.fromWire(entry.action) ?: return null
        val kind = SubjectKind.fromWire(entry.subjectType) ?: return null
        val at = runCatching { Instant.parse(entry.occurredAt) }.getOrNull() ?: return null
        return ActivityItem(
            id = entry.id,
            at = at,
            actor = actorOf(entry, names),
            action = action,
            subject = subjectOf(kind, entry.subjectId, names),
            roleWire = entry.role,
        )
    }

    private fun actorOf(entry: ActivityEntryDto, names: ActivityNames): Actor {
        val userId = entry.actorUserId ?: return Actor.Automatic
        entry.actorDisplayName?.takeIf { it.isNotBlank() }?.let { return Actor.Named(it) }
        if (userId !in names.members) return Actor.FormerMember
        return names.members[userId]?.takeIf { it.isNotBlank() }?.let { Actor.Named(it) } ?: Actor.UnnamedMember
    }

    private fun subjectOf(kind: SubjectKind, id: String, names: ActivityNames): ActivitySubject = when (kind) {
        SubjectKind.Wallet -> ActivitySubject(kind, id, names.walletName, onPhone = true)
        SubjectKind.Transaction -> ActivitySubject(kind, id, names.transactions[id], onPhone = id in names.transactions)
        SubjectKind.RecurringRule -> ActivitySubject(kind, id, names.rules[id], onPhone = id in names.rules)
        SubjectKind.Occurrence -> ActivitySubject(kind, id, names.occurrences[id], onPhone = id in names.occurrences)
        SubjectKind.Member -> ActivitySubject(kind, id, names.members[id], onPhone = id in names.members)
        SubjectKind.Invitation -> ActivitySubject(kind, id, null)
    }

    /**
     * Whether the latest role change about [userId] in [entries] (newest first) was the owner's plan lowering them; null when
     * the entries hold no role change about them (spec 013 FR-027).
     */
    fun limitedByPlan(entries: List<ActivityEntryDto>, userId: String): Boolean? {
        val change = entries.firstOrNull {
            it.subjectType == SubjectKind.Member.wire && it.subjectId == userId &&
                (it.action == ActivityAction.RoleChanged.wire || it.action == ActivityAction.RoleLimitedByPlan.wire)
        } ?: return null
        return change.action == ActivityAction.RoleLimitedByPlan.wire
    }
}
