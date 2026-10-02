package com.fares.saveme.feature.categories.ui.editor

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategoryField
import com.fares.saveme.feature.categories.domain.model.CategoryFieldProblem
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.categories.domain.model.CreateResult
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import com.fares.saveme.feature.categories.domain.model.SaveResult
import com.fares.saveme.feature.categories.domain.model.SyncState
import com.fares.saveme.feature.categories.domain.ownView
import com.fares.saveme.feature.categories.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CategoryEditorReducerTest {
    @Test
    fun typingChecksTheNameAndClearsItsOwnError() {
        val blank = reduce(CategoryEditorState(loaded = true), CategoryEditorChange.Name("  "))
        assertEquals(CategoryFieldProblem.NameBlank, blank.errors[CategoryField.Name])
        assertFalse(blank.canSave)
        val long = reduce(blank, CategoryEditorChange.Name("x".repeat(51)))
        assertEquals(CategoryFieldProblem.NameTooLong, long.errors[CategoryField.Name])
        val ok = reduce(long, CategoryEditorChange.Name("Coffee"))
        assertNull(ok.errors[CategoryField.Name])
        assertTrue(ok.canSave)
    }

    @Test
    fun theNameIsCountedInCodePoints() {
        val fifty = "x".repeat(49) + "😀"
        assertNull(reduce(CategoryEditorState(), CategoryEditorChange.Name(fifty)).errors[CategoryField.Name])
        assertEquals(50, codePoints(fifty))
    }

    @Test
    fun theKindIsChosenWhileCreatingAndReadOnlyWhileEditing() {
        val create = reduce(CategoryEditorState(), CategoryEditorChange.Kind(CategoryKind.Income))
        assertEquals("income", create.kindWire)
        assertTrue(create.kindEditable)
        val edit = reduce(CategoryEditorState(categoryId = "c1"), CategoryEditorChange.Kind(CategoryKind.Income))
        assertEquals("expense", edit.kindWire)
        assertFalse(edit.kindEditable)
    }

    @Test
    fun iconAndColourChoicesClearTheirErrors() {
        val state = CategoryEditorState(errors = mapOf(CategoryField.Icon to CategoryFieldProblem.Server("X"), CategoryField.Color to CategoryFieldProblem.Server("X")))
        val next = reduce(reduce(state, CategoryEditorChange.Icon("bus")), CategoryEditorChange.Color(null))
        assertEquals(emptyMap(), next.errors)
        assertEquals("bus", next.iconKey)
        assertNull(next.colorHex)
    }

    @Test
    fun theStatusFollowsTheViewAndActionsFollowTheRule() {
        val waiting = reduce(CategoryEditorState(categoryId = "c1"), CategoryEditorChange.Stored(ownView("c1", state = SyncState.Waiting), canDelete = true, usedLocally = false))
        assertEquals(EditorSync.Waiting, waiting.sync)
        assertTrue(waiting.canArchive && waiting.canDelete && !waiting.inUseNote)
        val used = reduce(waiting, CategoryEditorChange.Stored(ownView("c1", inUse = true), canDelete = false, usedLocally = false))
        assertTrue(!used.canDelete && used.inUseNote)
        val archived = reduce(waiting, CategoryEditorChange.Stored(ownView("c1", archived = true), canDelete = true, usedLocally = false))
        assertFalse(archived.canArchive)
        assertTrue(archived.archived)
    }

    @Test
    fun aClashListsOnlyTheDifferingFieldsAndATombstoneListsNone() {
        val theirs = Category("c1", "expense", "Café", "restaurant", "#000000", null, false, 6, Instant.parse("2026-09-01T00:00:00Z"))
        val view = ownView("c1", "Coffee", pending = CategoryDraft("expense", "Coffee", "restaurant", "#1F7F8C"), state = SyncState.Clash(theirs, false))
        val clash = assertIs<EditorSync.Clash>(reduce(CategoryEditorState(), CategoryEditorChange.Stored(view, true, false)).sync)
        assertEquals(listOf(ClashField.Name, ClashField.Color), clash.lines.map { it.field })
        assertFalse(clash.deletedElsewhere || clash.isDelete)
        val deleted = assertIs<EditorSync.Clash>(
            reduce(CategoryEditorState(), CategoryEditorChange.Stored(ownView("c1", state = SyncState.Clash(null, true)), true, false)).sync,
        )
        assertTrue(deleted.deletedElsewhere && deleted.lines.isEmpty())
        val deleteClash = assertIs<EditorSync.Clash>(
            reduce(CategoryEditorState(), CategoryEditorChange.Stored(ownView("c1", pendingDelete = true, state = SyncState.Clash(theirs, false)), true, false)).sync,
        )
        assertTrue(deleteClash.isDelete && deleteClash.lines.isEmpty())
    }

    @Test
    fun aRefusalShowsItsReasonAndItsFieldErrors() {
        val view = ownView("c1", state = SyncState.Refused(RefusalReason.Invalid, mapOf(CategoryField.Name to "OUT_OF_RANGE")))
        val state = reduce(CategoryEditorState(), CategoryEditorChange.Stored(view, true, false))
        assertEquals(EditorSync.Refused(RefusalReason.Invalid), state.sync)
        assertEquals(CategoryFieldProblem.Server("OUT_OF_RANGE"), state.errors[CategoryField.Name])
    }
}

class CategoryEditorViewModelTest {
    private class Calls {
        val created = mutableListOf<CategoryDraft>()
        val saved = mutableListOf<Triple<String, CategoryDraft, Long>>()
        val log = mutableListOf<String>()
        var createResult: CreateResult = CreateResult.Created("new")
        var saveResult: SaveResult = SaveResult.Saved
        var archiveResult: SaveResult = SaveResult.Saved
        var deleteResult: SaveResult = SaveResult.Saved
    }

    private fun vm(
        id: String? = null,
        kind: CategoryKind = CategoryKind.Expense,
        view: Flow<CategoryView?> = flowOf(ownView("c1", "Coffee", version = 4)),
        used: Flow<Set<String>> = flowOf(emptySet()),
        calls: Calls = Calls(),
    ) = CategoryEditorViewModel(
        categoryId = id,
        kind = kind,
        observeCategory = { view },
        usedLocally = used,
        create = { calls.created += it; calls.createResult },
        save = { i, d, b -> calls.saved += Triple(i, d, b); calls.saveResult },
        archive = { calls.log += "archive"; calls.archiveResult },
        delete = { calls.log += "delete"; calls.deleteResult },
        keepMine = { calls.log += "keepMine" },
        useTheirs = { calls.log += "useTheirs" },
        tryAgain = { calls.log += "tryAgain" },
        discard = { calls.log += "discard" },
        archiveInstead = { calls.log += "archiveInstead" },
        keepCategory = { calls.log += "keepCategory" },
    )

    @Test
    fun createModePreselectsTheKindAndSavesTheDraft() = runMviTest {
        val calls = Calls()
        val vm = vm(kind = CategoryKind.Income, calls = calls)
        assertTrue(vm.state.value.loaded && vm.state.value.isCreate)
        assertEquals("income", vm.state.value.kindWire)
        vm.onIntent(CategoryEditorIntent.NameChanged("  Tips "))
        vm.onIntent(CategoryEditorIntent.IconChosen("gift"))
        vm.onIntent(CategoryEditorIntent.ColorChosen("#B04375"))
        vm.effects.test {
            vm.onIntent(CategoryEditorIntent.SaveClicked)
            assertEquals(CategoryEditorEffect.Saved, awaitItem())
        }
        assertEquals(CategoryDraft("income", "Tips", "gift", "#B04375", false), calls.created.single())
    }

    @Test
    fun aBlankNameCannotBeSaved() = runMviTest {
        val calls = Calls()
        val vm = vm(calls = calls)
        vm.onIntent(CategoryEditorIntent.SaveClicked)
        assertTrue(calls.created.isEmpty())
    }

    @Test
    fun serverFieldErrorsAndTheVerifyPromptComeBackFromCreate() = runMviTest {
        val calls = Calls()
        calls.createResult = CreateResult.Invalid(mapOf(CategoryField.Name to CategoryFieldProblem.NameTooLong))
        val vm = vm(calls = calls)
        vm.onIntent(CategoryEditorIntent.NameChanged("x"))
        vm.onIntent(CategoryEditorIntent.SaveClicked)
        assertEquals(CategoryFieldProblem.NameTooLong, vm.state.value.errors[CategoryField.Name])
        calls.createResult = CreateResult.NotVerified
        vm.effects.test {
            vm.onIntent(CategoryEditorIntent.SaveClicked)
            assertEquals(CategoryEditorEffect.RequireVerification, awaitItem())
        }
    }

    @Test
    fun editModeStartsFromWhatIsShownAndSavesOnTheFirstSeenVersion() = runMviTest {
        val calls = Calls()
        val flow = MutableStateFlow<CategoryView?>(ownView("c1", "Coffee", version = 4))
        val vm = vm(id = "c1", view = flow, calls = calls)
        assertEquals("Coffee", vm.state.value.name)
        assertEquals(4L, vm.state.value.baseVersion)
        // A refresh lands while typing: the base stays, so the server answers with a clash.
        flow.value = ownView("c1", "Other", version = 9)
        vm.onIntent(CategoryEditorIntent.NameChanged("Beans"))
        vm.onIntent(CategoryEditorIntent.SaveClicked)
        assertEquals(Triple("c1", CategoryDraft("expense", "Beans", "restaurant", "#1F7F8C", false), 4L), calls.saved.single())
        assertEquals("Beans", vm.state.value.name)
    }

    @Test
    fun aMissingCategoryClosesBackWithAMessage() = runMviTest {
        val vm = vm(id = "c1", view = flowOf(null))
        vm.effects.test {
            assertEquals(CategoryEditorEffect.ShowMessage(EditorMessage.Gone), awaitItem())
            assertEquals(CategoryEditorEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun archiveIsOfferedForActiveOnlyAndConfirmedBeforeItRuns() = runMviTest {
        val calls = Calls()
        val vm = vm(id = "c1", calls = calls)
        assertTrue(vm.state.value.canArchive)
        vm.onIntent(CategoryEditorIntent.ArchiveClicked)
        assertEquals(EditorConfirm.Archive, vm.state.value.confirm)
        vm.onIntent(CategoryEditorIntent.DismissConfirm)
        assertEquals(EditorConfirm.None, vm.state.value.confirm)
        assertTrue(calls.log.isEmpty())
        val archived = vm(id = "c1", view = flowOf(ownView("c1", archived = true)))
        assertFalse(archived.state.value.canArchive)
        archived.onIntent(CategoryEditorIntent.ArchiveClicked)
        assertEquals(EditorConfirm.None, archived.state.value.confirm)
    }

    @Test
    fun confirmingArchiveAndDeleteRunThemAndGoBack() = runMviTest {
        val calls = Calls()
        val vm = vm(id = "c1", calls = calls)
        vm.effects.test {
            vm.onIntent(CategoryEditorIntent.ArchiveClicked)
            vm.onIntent(CategoryEditorIntent.ConfirmArchive)
            assertEquals(CategoryEditorEffect.NavigateBack, awaitItem())
            vm.onIntent(CategoryEditorIntent.DeleteClicked)
            vm.onIntent(CategoryEditorIntent.ConfirmDelete)
            assertEquals(CategoryEditorEffect.NavigateBack, awaitItem())
        }
        assertEquals(listOf("archive", "delete"), calls.log)
    }

    @Test
    fun deleteIsOfferedPerTheRuleElseTheInUseNoteShows() = runMviTest {
        val free = vm(id = "c1")
        assertTrue(free.state.value.canDelete && !free.state.value.inUseNote)
        free.onIntent(CategoryEditorIntent.DeleteClicked)
        assertEquals(EditorConfirm.Delete, free.state.value.confirm)

        val serverUse = vm(id = "c1", view = flowOf(ownView("c1", inUse = true)))
        assertTrue(!serverUse.state.value.canDelete && serverUse.state.value.inUseNote)
        serverUse.onIntent(CategoryEditorIntent.DeleteClicked)
        assertEquals(EditorConfirm.None, serverUse.state.value.confirm)

        val localUse = vm(id = "c1", used = flowOf(setOf("c1")))
        assertTrue(!localUse.state.value.canDelete && localUse.state.value.inUseNote)
    }

    @Test
    fun anInUseRefusalOffersArchiveInsteadAndKeep() = runMviTest {
        val calls = Calls()
        val refused = ownView("c1", pendingDelete = true, state = SyncState.Refused(RefusalReason.InUse, emptyMap()))
        val vm = vm(id = "c1", view = flowOf(refused), calls = calls)
        assertEquals(EditorSync.Refused(RefusalReason.InUse), vm.state.value.sync)
        assertFalse(vm.state.value.canDelete)
        vm.onIntent(CategoryEditorIntent.ArchiveInstead)
        vm.onIntent(CategoryEditorIntent.KeepCategory)
        assertEquals(listOf("archiveInstead", "keepCategory"), calls.log)
    }

    @Test
    fun clashAndRefusalChoicesCallTheirDomainActions() = runMviTest {
        val calls = Calls()
        val vm = vm(id = "c1", calls = calls)
        vm.onIntent(CategoryEditorIntent.KeepMine)
        vm.onIntent(CategoryEditorIntent.UseTheirs)
        vm.onIntent(CategoryEditorIntent.AcceptDeletion)
        vm.onIntent(CategoryEditorIntent.TryAgain)
        vm.onIntent(CategoryEditorIntent.Discard)
        assertEquals(listOf("keepMine", "useTheirs", "useTheirs", "tryAgain", "discard"), calls.log)
    }

    @Test
    fun notVerifiedRefusalOffersTryAgainAndRequireVerificationFromSave() = runMviTest {
        val calls = Calls()
        calls.saveResult = SaveResult.NotVerified
        val vm = vm(id = "c1", calls = calls)
        vm.effects.test {
            vm.onIntent(CategoryEditorIntent.SaveClicked)
            assertEquals(CategoryEditorEffect.RequireVerification, awaitItem())
        }
    }

    @Test
    fun goneWhileEditingClosesWithAMessageOnceAndBack() = runMviTest {
        val flow = MutableStateFlow<CategoryView?>(ownView("c1"))
        val vm = vm(id = "c1", view = flow)
        vm.effects.test {
            flow.value = null
            assertEquals(CategoryEditorEffect.ShowMessage(EditorMessage.Gone), awaitItem())
            assertEquals(CategoryEditorEffect.NavigateBack, awaitItem())
        }
    }

    /** "Accept the deletion", "Delete anyway" or "Discard" remove the category by the person's choice: close, no "gone" message. */
    @Test
    fun aRemovalThePersonChoseClosesWithoutTheGoneMessage() = runMviTest {
        val flow = MutableStateFlow<CategoryView?>(ownView("c1"))
        val vm = vm(id = "c1", view = flow)
        vm.effects.test {
            vm.onIntent(CategoryEditorIntent.AcceptDeletion)
            flow.value = null
            assertEquals(CategoryEditorEffect.NavigateBack, awaitItem())
            expectNoEvents()
        }
    }
}
