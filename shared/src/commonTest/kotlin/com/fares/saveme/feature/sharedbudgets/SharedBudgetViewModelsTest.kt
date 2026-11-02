package com.fares.saveme.feature.sharedbudgets

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.sharedbudgets.domain.ActionResult
import com.fares.saveme.feature.sharedbudgets.domain.BudgetWriteResult
import com.fares.saveme.feature.sharedbudgets.domain.InviteResult
import com.fares.saveme.feature.sharedbudgets.domain.JoinResult
import com.fares.saveme.feature.sharedbudgets.domain.PreviewResult
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetField
import com.fares.saveme.feature.sharedbudgets.domain.model.EditorBudget
import com.fares.saveme.feature.sharedbudgets.domain.model.EditorLine
import com.fares.saveme.feature.sharedbudgets.domain.model.MemberTotal
import com.fares.saveme.feature.sharedbudgets.domain.model.SharedBudgetProgress
import com.fares.saveme.feature.sharedbudgets.ui.detail.DetailConfirm
import com.fares.saveme.feature.sharedbudgets.ui.detail.SharedBudgetDetailEffect
import com.fares.saveme.feature.sharedbudgets.ui.detail.SharedBudgetDetailIntent
import com.fares.saveme.feature.sharedbudgets.ui.detail.SharedBudgetDetailViewModel
import com.fares.saveme.feature.sharedbudgets.ui.editor.EditorMessage
import com.fares.saveme.feature.sharedbudgets.ui.editor.SharedBudgetEditorEffect
import com.fares.saveme.feature.sharedbudgets.ui.editor.SharedBudgetEditorIntent
import com.fares.saveme.feature.sharedbudgets.ui.editor.SharedBudgetEditorViewModel
import com.fares.saveme.feature.sharedbudgets.ui.invitation.InvitationStage
import com.fares.saveme.feature.sharedbudgets.ui.invitation.SharedBudgetInvitationEffect
import com.fares.saveme.feature.sharedbudgets.ui.invitation.SharedBudgetInvitationIntent
import com.fares.saveme.feature.sharedbudgets.ui.invitation.SharedBudgetInvitationViewModel
import com.fares.saveme.feature.sharedbudgets.ui.list.SharedBudgetsEffect
import com.fares.saveme.feature.sharedbudgets.ui.list.SharedBudgetsIntent
import com.fares.saveme.feature.sharedbudgets.ui.list.SharedBudgetsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun progress(
    id: String,
    archived: Boolean = false,
    owner: Boolean = true,
    members: List<MemberTotal> = emptyList(),
) = SharedBudgetProgress(
    id = id, name = "Home", currency = "EGP", period = BudgetPeriod(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), 31, 10),
    limitMinor = 100, spentMinor = 10, projectedMinor = null, lines = emptyList(), unlistedMinor = 0, members = members,
    isOwner = owner, archived = archived, refreshedAt = null,
)

private val me = MemberTotal("me", "Fares", isMe = true, spentMinor = 5, isMember = true, isOwner = true)
private val sara = MemberTotal("sara", "Sara", isMe = false, spentMinor = 5, isMember = true, isOwner = false)
private val gone = MemberTotal("gone", null, isMe = false, spentMinor = 1, isMember = false, isOwner = false)

class SharedBudgetViewModelsTest {
    private val manager = FakeSharedBudgetsManager()

    // ---- list ----

    @Test
    fun theListSplitsArchivedFromActiveAndOpensOnlyBudgetsItHolds() = runMviTest {
        val store = SharedBudgetsViewModel(flowOf(listOf(progress("b1"), progress("b2", archived = true))))
        assertTrue(store.state.value.loaded)
        assertEquals(listOf("b1"), store.state.value.active.map { it.id })
        assertEquals(listOf("b2"), store.state.value.archived.map { it.id })
        store.effects.test {
            store.onIntent(SharedBudgetsIntent.BudgetClicked("nope"))
            store.onIntent(SharedBudgetsIntent.BudgetClicked("b1"))
            assertEquals(SharedBudgetsEffect.OpenBudget("b1"), awaitItem())
            store.onIntent(SharedBudgetsIntent.AddClicked)
            assertEquals(SharedBudgetsEffect.OpenNew, awaitItem())
        }
    }

    @Test
    fun anEmptyListIsEmptyOnlyOnceLoaded() = runMviTest {
        assertTrue(SharedBudgetsViewModel(flowOf(emptyList())).state.value.isEmpty)
        assertFalse(SharedBudgetsViewModel(emptyFlow()).state.value.isEmpty)
    }

    // ---- detail ----

    @Test
    fun theDetailClosesWhenTheBudgetLeavesThePhone() = runMviTest {
        val source = MutableStateFlow<SharedBudgetProgress?>(progress("b1"))
        val store = SharedBudgetDetailViewModel("b1", { source }, manager)
        assertNotNull(store.state.value.progress)
        store.effects.test {
            source.value = null
            assertEquals(SharedBudgetDetailEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun theOwnerInvitesAndTheLinkGoesStraightToTheShareSheet() = runMviTest {
        val store = SharedBudgetDetailViewModel("b1", { flowOf(progress("b1", members = listOf(me))) }, manager)
        store.effects.test {
            store.onIntent(SharedBudgetDetailIntent.InviteClicked)
            assertIs<SharedBudgetDetailEffect.ShareLink>(awaitItem())
        }
        assertEquals(listOf("invite:b1"), manager.calls)
        manager.invite = InviteResult.Created(null)
        store.effects.test {
            store.onIntent(SharedBudgetDetailIntent.InviteClicked)
            assertEquals(SharedBudgetDetailEffect.LinkUnavailable, awaitItem())
        }
    }

    @Test
    fun aMemberCannotInviteButCanLeaveAfterConfirming() = runMviTest {
        val store = SharedBudgetDetailViewModel("b1", { flowOf(progress("b1", owner = false, members = listOf(me, sara))) }, manager)
        store.onIntent(SharedBudgetDetailIntent.InviteClicked)
        assertTrue(manager.calls.isEmpty())
        store.onIntent(SharedBudgetDetailIntent.LeaveClicked)
        assertEquals(DetailConfirm.Leave, store.state.value.confirm)
        store.effects.test {
            store.onIntent(SharedBudgetDetailIntent.ConfirmYes)
            assertEquals(SharedBudgetDetailEffect.NavigateBack, awaitItem())
        }
        assertEquals(listOf("leave:b1"), manager.calls)
    }

    @Test
    fun theOwnerRemovesOnlyCurrentMembersOtherThanThemselves() = runMviTest {
        val store = SharedBudgetDetailViewModel("b1", { flowOf(progress("b1", members = listOf(me, sara, gone))) }, manager)
        store.onIntent(SharedBudgetDetailIntent.RemoveClicked("me"))
        store.onIntent(SharedBudgetDetailIntent.RemoveClicked("gone"))
        assertEquals(null, store.state.value.confirm)
        store.onIntent(SharedBudgetDetailIntent.RemoveClicked("sara"))
        assertEquals(DetailConfirm.Remove("sara", "Sara"), store.state.value.confirm)
        store.onIntent(SharedBudgetDetailIntent.ConfirmYes)
        assertEquals(listOf("remove:sara"), manager.calls)
        assertEquals(null, store.state.value.confirm)
    }

    @Test
    fun anOfflineActionIsReportedNotSilentlyDropped() = runMviTest {
        manager.action = ActionResult.Offline
        val store = SharedBudgetDetailViewModel("b1", { flowOf(progress("b1", members = listOf(me, sara))) }, manager)
        store.onIntent(SharedBudgetDetailIntent.TransferClicked("sara"))
        store.effects.test {
            store.onIntent(SharedBudgetDetailIntent.ConfirmYes)
            assertEquals(SharedBudgetDetailEffect.ShowMessage(EditorMessage.Offline), awaitItem())
        }
    }

    // ---- editor ----

    private val currencies = flowOf(listOf("EGP", "USD"))
    private val choices = flowOf(CategoryChoices(null, emptyList(), emptyList()))

    private fun editor(id: String? = null, source: EditorBudget? = null) =
        SharedBudgetEditorViewModel(id, manager, { flowOf(source) }, currencies, choices)

    @Test
    fun creatingSendsTheTypedDraftAndOpensTheNewBudget() = runMviTest {
        val store = editor()
        store.onIntent(SharedBudgetEditorIntent.NameChanged("Home"))
        store.onIntent(SharedBudgetEditorIntent.LimitChanged("2000"))
        store.onIntent(SharedBudgetEditorIntent.StartDayChosen(5))
        store.effects.test {
            store.onIntent(SharedBudgetEditorIntent.SaveClicked)
            assertEquals(SharedBudgetEditorEffect.Close("new"), awaitItem())
        }
        val draft = manager.created.single()
        assertEquals("EGP", draft.currency)
        assertEquals(5, draft.startDay)
        assertEquals(200_000L, draft.limitMinor)
    }

    @Test
    fun anEmptyLimitMeansNoLimitAndGarbageIsRefusedWithoutACall() = runMviTest {
        val store = editor()
        store.onIntent(SharedBudgetEditorIntent.NameChanged("Home"))
        store.onIntent(SharedBudgetEditorIntent.LimitChanged("abc"))
        store.onIntent(SharedBudgetEditorIntent.SaveClicked)
        assertTrue(SharedBudgetField.Limit in store.state.value.errors)
        assertTrue(manager.created.isEmpty())
        store.onIntent(SharedBudgetEditorIntent.LimitChanged(""))
        store.effects.test {
            store.onIntent(SharedBudgetEditorIntent.SaveClicked)
            awaitItem()
        }
        assertEquals(null, manager.created.single().limitMinor)
    }

    @Test
    fun aMemberWhoIsNotTheOwnerCannotSaveOrChangeTheList() = runMviTest {
        val store = editor("b1", EditorBudget("b1", "Home", "EGP", 1, null, 3, isOwner = false, archived = false, lines = listOf(EditorLine("l1", "c1", "Rent", null, null))))
        assertFalse(store.state.value.canEdit)
        store.onIntent(SharedBudgetEditorIntent.SaveClicked)
        store.onIntent(SharedBudgetEditorIntent.LineRemoved("l1"))
        store.onIntent(SharedBudgetEditorIntent.AddCategoryClicked)
        assertTrue(manager.saved.isEmpty() && manager.calls.isEmpty())
    }

    @Test
    fun theOwnerEditsOnTopOfTheShownVersionAndTheLineCallsGoToTheServer() = runMviTest {
        val store = editor("b1", EditorBudget("b1", "Home", "EGP", 1, 500_000, 7, isOwner = true, archived = false, lines = listOf(EditorLine("l1", "c1", "Rent", null, null))))
        assertEquals("5000", store.state.value.limitText.substringBefore('.'))
        store.onIntent(SharedBudgetEditorIntent.NameChanged("Our home"))
        store.effects.test {
            store.onIntent(SharedBudgetEditorIntent.SaveClicked)
            assertEquals(SharedBudgetEditorEffect.Close(null), awaitItem())
        }
        assertEquals(7L, manager.saved.single().third)
        store.onIntent(SharedBudgetEditorIntent.LineRemoved("l1"))
        store.onIntent(SharedBudgetEditorIntent.CategoryChosen("c2"))
        assertEquals(listOf("removeLine:l1", "addLine:c2"), manager.calls)
    }

    @Test
    fun anOfflineLineChangeSaysItNeedsAConnection() = runMviTest {
        manager.action = ActionResult.Offline
        val store = editor("b1", EditorBudget("b1", "Home", "EGP", 1, null, 7, isOwner = true, archived = false, lines = listOf(EditorLine("l1", "c1", "Rent", null, null))))
        store.effects.test {
            store.onIntent(SharedBudgetEditorIntent.LineRemoved("l1"))
            assertEquals(SharedBudgetEditorEffect.ShowMessage(EditorMessage.Offline), awaitItem())
        }
    }

    @Test
    fun aDeletedBudgetClosesBothScreens() = runMviTest {
        val store = editor("b1", EditorBudget("b1", "Home", "EGP", 1, null, 7, isOwner = true, archived = false, lines = emptyList()))
        manager.write = BudgetWriteResult.Saved("b1")
        store.onIntent(SharedBudgetEditorIntent.DeleteClicked)
        assertTrue(store.state.value.confirmDelete)
        store.effects.test {
            store.onIntent(SharedBudgetEditorIntent.DeleteConfirmed)
            assertEquals(SharedBudgetEditorEffect.Gone, awaitItem())
        }
    }

    // ---- invitation ----

    private fun invitation(token: String = "T".repeat(43), signedIn: Boolean = true, onAnswered: () -> Unit = {}) =
        SharedBudgetInvitationViewModel(token, { signedIn }, manager, onAnswered)

    @Test
    fun aLinkThatWasNotValidMakesNoCall() = runMviTest {
        assertEquals(InvitationStage.InvalidLink, invitation(token = "").state.value.stage)
        assertTrue(manager.calls.isEmpty())
    }

    @Test
    fun aSignedOutPersonIsAskedToSignInFirstAndNothingIsSent() = runMviTest {
        val store = invitation(signedIn = false)
        assertEquals(InvitationStage.SignedOut, store.state.value.stage)
        assertTrue(manager.calls.isEmpty())
        store.effects.test {
            store.onIntent(SharedBudgetInvitationIntent.SignInClicked)
            assertEquals(SharedBudgetInvitationEffect.NavigateSignIn, awaitItem())
        }
    }

    @Test
    fun thePersonSeesThePreviewBeforeJoiningAndJoiningOpensTheBudget() = runMviTest {
        var answered = false
        val store = invitation(onAnswered = { answered = true })
        assertEquals("Home", assertIs<InvitationStage.Preview>(store.state.value.stage).preview.name)
        assertEquals(listOf("preview"), manager.calls)
        store.effects.test {
            store.onIntent(SharedBudgetInvitationIntent.JoinClicked)
            assertEquals(SharedBudgetInvitationEffect.OpenBudget("b1"), awaitItem())
        }
        assertTrue(answered)
    }

    @Test
    fun anOwnerPlanThatIsFullAndAnExpiredInvitationAreTold() = runMviTest {
        manager.join = JoinResult.PlanFull
        val full = invitation()
        full.onIntent(SharedBudgetInvitationIntent.JoinClicked)
        assertEquals(InvitationStage.PlanFull, full.state.value.stage)
        manager.preview = PreviewResult.Unavailable
        assertEquals(InvitationStage.Unavailable, invitation().state.value.stage)
    }

    @Test
    fun decliningClosesTheScreenAndOfflineKeepsItOpenToRetry() = runMviTest {
        val store = invitation()
        manager.action = ActionResult.Offline
        store.onIntent(SharedBudgetInvitationIntent.DeclineClicked)
        assertEquals(InvitationStage.Offline, store.state.value.stage)
        manager.action = ActionResult.Done
        val again = invitation()
        again.effects.test {
            again.onIntent(SharedBudgetInvitationIntent.DeclineClicked)
            assertEquals(SharedBudgetInvitationEffect.Close, awaitItem())
        }
    }
}
