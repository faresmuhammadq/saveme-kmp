package com.fares.saveme.feature.categories.ui.editor

import com.fares.saveme.feature.categories.domain.model.Category
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategoryField
import com.fares.saveme.feature.categories.domain.model.CategoryFieldProblem
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import com.fares.saveme.feature.categories.domain.model.SyncState
import com.fares.saveme.feature.categories.ui.asProblems

/** Members a clash can differ in (research R-07). */
enum class ClashField { Name, Icon, Color, Archived }

sealed interface ClashValue {
    data class Text(val value: String?) : ClashValue
    data class Icon(val key: String?) : ClashValue
    data class Color(val hex: String?) : ClashValue
    data class Archived(val archived: Boolean) : ClashValue
}

data class ClashLine(val field: ClashField, val mine: ClashValue, val theirs: ClashValue)

/** Only the fields where this phone's change and the other device's copy differ (research R-07). */
fun clashLines(mine: CategoryDraft, theirs: Category): List<ClashLine> {
    val other = theirs.toDraft()
    return listOfNotNull(
        ClashLine(ClashField.Name, ClashValue.Text(mine.name), ClashValue.Text(other.name)).takeIf { mine.name != other.name },
        ClashLine(ClashField.Icon, ClashValue.Icon(mine.icon), ClashValue.Icon(other.icon)).takeIf { mine.icon != other.icon },
        ClashLine(ClashField.Color, ClashValue.Color(mine.color), ClashValue.Color(other.color)).takeIf { mine.color != other.color },
        ClashLine(ClashField.Archived, ClashValue.Archived(mine.archive), ClashValue.Archived(other.archive)).takeIf { mine.archive != other.archive },
    )
}

/** What the editor's status area shows (data-model §5). */
sealed interface EditorSync {
    data object None : EditorSync
    data object Waiting : EditorSync

    /** [isDelete]: the phone's change is a deletion, so the choices are "Delete anyway" / "Keep the category". */
    data class Clash(val lines: List<ClashLine>, val deletedElsewhere: Boolean, val isDelete: Boolean) : EditorSync
    data class Refused(val reason: RefusalReason) : EditorSync
}

/** Which confirmation, if any, is open. */
enum class EditorConfirm { None, Archive, Delete }

enum class EditorMessage { Gone }

data class CategoryEditorState(
    /** Null while creating; the category's id while editing. */
    val categoryId: String? = null,
    val loaded: Boolean = false,
    /** The wire value of the kind, so an unknown kind is sent back unchanged. */
    val kindWire: String = CategoryKind.Expense.wire,
    val name: String = "",
    val iconKey: String? = null,
    val colorHex: String? = null,
    /** The version shown when editing began; the save is sent on top of it (FR-007). */
    val baseVersion: Long = 0,
    val errors: Map<CategoryField, CategoryFieldProblem> = emptyMap(),
    val sync: EditorSync = EditorSync.None,
    val archived: Boolean = false,
    val canArchive: Boolean = false,
    val canDelete: Boolean = false,
    val inUseNote: Boolean = false,
    val confirm: EditorConfirm = EditorConfirm.None,
    val saving: Boolean = false,
    val busy: Boolean = false,
) {
    val isCreate: Boolean get() = categoryId == null
    val kind: CategoryKind? get() = CategoryKind.fromWire(kindWire)
    val kindEditable: Boolean get() = isCreate
    val canSave: Boolean get() = loaded && name.isNotBlank() && !saving
}

sealed interface CategoryEditorIntent {
    data class KindChosen(val kind: CategoryKind) : CategoryEditorIntent
    data class NameChanged(val value: String) : CategoryEditorIntent
    data class IconChosen(val key: String?) : CategoryEditorIntent
    data class ColorChosen(val hex: String?) : CategoryEditorIntent
    data object SaveClicked : CategoryEditorIntent
    data object ArchiveClicked : CategoryEditorIntent
    data object DeleteClicked : CategoryEditorIntent
    data object ConfirmArchive : CategoryEditorIntent
    data object ConfirmDelete : CategoryEditorIntent
    data object DismissConfirm : CategoryEditorIntent
    data object KeepMine : CategoryEditorIntent
    data object UseTheirs : CategoryEditorIntent
    data object AcceptDeletion : CategoryEditorIntent
    data object TryAgain : CategoryEditorIntent
    data object Discard : CategoryEditorIntent
    data object ArchiveInstead : CategoryEditorIntent
    data object KeepCategory : CategoryEditorIntent
    data object Back : CategoryEditorIntent
}

sealed interface CategoryEditorEffect {
    data object Saved : CategoryEditorEffect
    data object NavigateBack : CategoryEditorEffect
    data class ShowMessage(val message: EditorMessage) : CategoryEditorEffect
    data object RequireVerification : CategoryEditorEffect
}

sealed interface CategoryEditorChange {
    /** The initial values: the preselected kind when creating, the category as shown when editing. */
    data class Loaded(val state: CategoryEditorState) : CategoryEditorChange

    /** The category's current view: status, archived and the offered actions follow it; typed fields do not. */
    data class Stored(val view: CategoryView, val canDelete: Boolean, val usedLocally: Boolean) : CategoryEditorChange
    data class Kind(val kind: CategoryKind) : CategoryEditorChange
    data class Name(val value: String) : CategoryEditorChange
    data class Icon(val key: String?) : CategoryEditorChange
    data class Color(val hex: String?) : CategoryEditorChange
    data class Errors(val errors: Map<CategoryField, CategoryFieldProblem>) : CategoryEditorChange
    data class Confirm(val confirm: EditorConfirm) : CategoryEditorChange
    data class Saving(val busy: Boolean) : CategoryEditorChange
    data class Busy(val busy: Boolean) : CategoryEditorChange
}

const val MAX_NAME_CODE_POINTS = 50

/** Name length in Unicode code points, as the server counts it (FR-006). */
fun codePoints(text: String): Int = text.count { !it.isLowSurrogate() }

fun reduce(state: CategoryEditorState, change: CategoryEditorChange): CategoryEditorState = when (change) {
    is CategoryEditorChange.Loaded -> change.state.copy(loaded = true)
    is CategoryEditorChange.Stored -> {
        val refused = (change.view.syncState as? SyncState.Refused)?.fields.orEmpty()
        state.copy(
            sync = change.view.syncState.toEditorSync(change.view),
            archived = change.view.archived,
            canArchive = !change.view.archived && !change.view.pendingDelete,
            canDelete = change.canDelete,
            inUseNote = !change.canDelete && !change.view.pendingDelete && (change.view.server.inUse || change.usedLocally),
            errors = if (refused.isNotEmpty()) refused.asProblems() else state.errors,
        )
    }
    is CategoryEditorChange.Kind -> if (state.kindEditable) state.copy(kindWire = change.kind.wire) else state
    is CategoryEditorChange.Name -> state.copy(name = change.value, errors = state.errors.withNameProblem(change.value))
    is CategoryEditorChange.Icon -> state.copy(iconKey = change.key, errors = state.errors - CategoryField.Icon)
    is CategoryEditorChange.Color -> state.copy(colorHex = change.hex, errors = state.errors - CategoryField.Color)
    is CategoryEditorChange.Errors -> state.copy(errors = change.errors)
    is CategoryEditorChange.Confirm -> state.copy(confirm = change.confirm)
    is CategoryEditorChange.Saving -> state.copy(saving = change.busy)
    is CategoryEditorChange.Busy -> state.copy(busy = change.busy)
}

internal fun SyncState.toEditorSync(view: CategoryView): EditorSync = when (this) {
    SyncState.None -> EditorSync.None
    SyncState.Waiting -> EditorSync.Waiting
    is SyncState.Clash -> EditorSync.Clash(
        lines = theirs?.takeIf { !view.pendingDelete }?.let { clashLines(view.shown, it) }.orEmpty(),
        deletedElsewhere = deletedElsewhere,
        isDelete = view.pendingDelete,
    )
    is SyncState.Refused -> EditorSync.Refused(reason)
}

private fun Map<CategoryField, CategoryFieldProblem>.withNameProblem(name: String): Map<CategoryField, CategoryFieldProblem> = when {
    name.isBlank() -> this + (CategoryField.Name to CategoryFieldProblem.NameBlank)
    codePoints(name.trim()) > MAX_NAME_CODE_POINTS -> this + (CategoryField.Name to CategoryFieldProblem.NameTooLong)
    else -> this - CategoryField.Name
}
