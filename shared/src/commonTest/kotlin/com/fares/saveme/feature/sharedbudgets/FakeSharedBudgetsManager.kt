package com.fares.saveme.feature.sharedbudgets

import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetInvitationPreviewDto
import com.fares.saveme.feature.sharedbudgets.domain.ActionResult
import com.fares.saveme.feature.sharedbudgets.domain.BudgetWriteResult
import com.fares.saveme.feature.sharedbudgets.domain.InviteResult
import com.fares.saveme.feature.sharedbudgets.domain.JoinResult
import com.fares.saveme.feature.sharedbudgets.domain.PreviewResult
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetDraft
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetsManager

/** Records what the screens ask and answers what a test sets; nothing leaves the process. */
class FakeSharedBudgetsManager : SharedBudgetsManager {
    val calls = mutableListOf<String>()
    val created = mutableListOf<SharedBudgetDraft>()
    val saved = mutableListOf<Triple<String, SharedBudgetDraft, Long>>()

    var write: BudgetWriteResult? = null
    var action: ActionResult = ActionResult.Done
    var invite: InviteResult = InviteResult.Created("https://x.example/invite/shared-budget?token=T")
    var preview: PreviewResult = PreviewResult.Ready(SharedBudgetInvitationPreviewDto("i1", "b1", "Home", "EGP", "Sara", 2, "2026-11-10T10:00:00Z", false))
    var join: JoinResult = JoinResult.Joined("b1")

    override suspend fun create(draft: SharedBudgetDraft): BudgetWriteResult {
        created += draft
        return write ?: BudgetWriteResult.Saved("new")
    }

    override suspend fun save(id: String, draft: SharedBudgetDraft, baseVersion: Long): BudgetWriteResult {
        saved += Triple(id, draft, baseVersion)
        return write ?: BudgetWriteResult.Saved(id)
    }

    override suspend fun delete(id: String): BudgetWriteResult {
        calls += "delete:$id"
        return write ?: BudgetWriteResult.Saved(id)
    }

    override suspend fun archive(id: String): ActionResult = action.also { calls += "archive:$id" }
    override suspend fun addLine(budgetId: String, categoryId: String, limitMinor: Long?): ActionResult = action.also { calls += "addLine:$categoryId" }
    override suspend fun setLineLimit(budgetId: String, lineId: String, limitMinor: Long?): ActionResult = action.also { calls += "lineLimit:$lineId:$limitMinor" }
    override suspend fun removeLine(budgetId: String, lineId: String): ActionResult = action.also { calls += "removeLine:$lineId" }
    override suspend fun invite(budgetId: String): InviteResult = invite.also { calls += "invite:$budgetId" }
    override suspend fun leave(budgetId: String): ActionResult = action.also { calls += "leave:$budgetId" }
    override suspend fun removeMember(budgetId: String, userId: String): ActionResult = action.also { calls += "remove:$userId" }
    override suspend fun transferOwnership(budgetId: String, userId: String): ActionResult = action.also { calls += "transfer:$userId" }
    override suspend fun preview(token: String): PreviewResult = preview.also { calls += "preview" }
    override suspend fun join(token: String): JoinResult = join.also { calls += "join" }
    override suspend fun decline(token: String): ActionResult = action.also { calls += "decline" }
    override suspend fun refresh(budgetId: String, force: Boolean) {
        calls += "refresh:$budgetId:$force"
    }
}
