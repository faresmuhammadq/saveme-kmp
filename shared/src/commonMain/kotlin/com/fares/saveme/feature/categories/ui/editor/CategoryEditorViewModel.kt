package com.fares.saveme.feature.categories.ui.editor

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.categories.domain.model.CreateResult
import com.fares.saveme.feature.categories.domain.model.SaveResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Creates or edits one category through domain lambdas only (P-16.3); in edit mode it is also the
 * category's detail: status, clash and refusal choices, archive and delete. Edit mode starts from
 * what the phone shows (a waiting change or the server copy) and remembers that version as the base.
 */
class CategoryEditorViewModel(
    private val categoryId: String?,
    kind: CategoryKind,
    private val observeCategory: (String) -> Flow<CategoryView?>,
    private val usedLocally: Flow<Set<String>> = emptyFlow(),
    private val create: suspend (CategoryDraft) -> CreateResult,
    private val save: suspend (String, CategoryDraft, Long) -> SaveResult,
    private val archive: suspend (String) -> SaveResult,
    private val delete: suspend (String) -> SaveResult,
    private val keepMine: suspend (String) -> Unit,
    private val useTheirs: suspend (String) -> Unit,
    private val tryAgain: suspend (String) -> Unit,
    private val discard: suspend (String) -> Unit,
    private val archiveInstead: suspend (String) -> Unit,
    private val keepCategory: suspend (String) -> Unit,
) : MviViewModel<CategoryEditorState, CategoryEditorIntent, CategoryEditorEffect>(
    CategoryEditorState(categoryId = categoryId, kindWire = kind.wire),
) {

    /** True from a confirmed delete or archive on, so the category vanishing is not reported as "gone". */
    private var leaving = false

    /**
     * True once the person chose a clash or refusal action ("Accept the deletion", "Delete anyway",
     * "Discard", …). If the category then leaves the phone, that is their own choice: the editor
     * closes without the "no longer available" message.
     */
    private var resolvedByPerson = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (categoryId == null) {
            setState { reduce(it, CategoryEditorChange.Loaded(it)) }
            return
        }
        val first = observeCategory(categoryId).first()
        if (first == null) {
            sendEffect(CategoryEditorEffect.ShowMessage(EditorMessage.Gone))
            sendEffect(CategoryEditorEffect.NavigateBack)
            return
        }
        val shown = first.shown
        setState {
            reduce(
                it,
                CategoryEditorChange.Loaded(
                    it.copy(
                        kindWire = shown.kind,
                        name = shown.name,
                        iconKey = shown.icon,
                        colorHex = shown.color,
                        baseVersion = first.server.version,
                        archived = shown.archive,
                    ),
                ),
            )
        }
        // Keeps the status and the offered actions current while the person types (typed fields are never overwritten).
        combine(observeCategory(categoryId), usedLocally.ifEmptyUse(emptySet())) { view, used -> view to used }
            .collect { (view, used) -> onView(view, used) }
    }

    private fun onView(view: CategoryView?, used: Set<String>) {
        if (view == null) {
            if (!leaving) {
                leaving = true
                if (!resolvedByPerson) sendEffect(CategoryEditorEffect.ShowMessage(EditorMessage.Gone))
                sendEffect(CategoryEditorEffect.NavigateBack)
            }
            return
        }
        val id = view.id
        setState { reduce(it, CategoryEditorChange.Stored(view, view.canDelete(used), id in used)) }
    }

    override fun onIntent(intent: CategoryEditorIntent) {
        when (intent) {
            is CategoryEditorIntent.KindChosen -> setState { reduce(it, CategoryEditorChange.Kind(intent.kind)) }
            is CategoryEditorIntent.NameChanged -> setState { reduce(it, CategoryEditorChange.Name(intent.value)) }
            is CategoryEditorIntent.IconChosen -> setState { reduce(it, CategoryEditorChange.Icon(intent.key)) }
            is CategoryEditorIntent.ColorChosen -> setState { reduce(it, CategoryEditorChange.Color(intent.hex)) }
            CategoryEditorIntent.SaveClicked -> submit()
            CategoryEditorIntent.ArchiveClicked -> if (currentState.canArchive) confirm(EditorConfirm.Archive)
            CategoryEditorIntent.DeleteClicked -> if (currentState.canDelete) confirm(EditorConfirm.Delete)
            CategoryEditorIntent.DismissConfirm -> if (!currentState.busy) confirm(EditorConfirm.None)
            CategoryEditorIntent.ConfirmArchive -> confirmed {
                leaving = true
                val result = archive(it)
                if (result == SaveResult.Saved) sendEffect(CategoryEditorEffect.NavigateBack) else leaving = false
                result
            }
            CategoryEditorIntent.ConfirmDelete -> confirmed {
                leaving = true
                val result = delete(it)
                if (result == SaveResult.Saved) sendEffect(CategoryEditorEffect.NavigateBack) else leaving = false
                result
            }
            CategoryEditorIntent.KeepMine -> resolve(keepMine)
            CategoryEditorIntent.UseTheirs, CategoryEditorIntent.AcceptDeletion -> resolve(useTheirs)
            CategoryEditorIntent.TryAgain -> resolve(tryAgain)
            CategoryEditorIntent.Discard -> resolve(discard)
            CategoryEditorIntent.ArchiveInstead -> resolve(archiveInstead)
            CategoryEditorIntent.KeepCategory -> resolve(keepCategory)
            CategoryEditorIntent.Back -> sendEffect(CategoryEditorEffect.NavigateBack)
        }
    }

    private fun confirm(confirm: EditorConfirm) = setState { reduce(it, CategoryEditorChange.Confirm(confirm)) }

    private fun submit() {
        val state = currentState
        if (!state.canSave) return
        val draft = CategoryDraft(
            kind = state.kindWire,
            name = state.name.trim(),
            icon = state.iconKey,
            color = state.colorHex,
            archive = state.archived,
        )
        setState { reduce(it, CategoryEditorChange.Saving(true)) }
        viewModelScope.launch {
            val id = state.categoryId
            if (id == null) {
                when (val result = create(draft)) {
                    is CreateResult.Created -> sendEffect(CategoryEditorEffect.Saved)
                    CreateResult.NotVerified -> sendEffect(CategoryEditorEffect.RequireVerification)
                    is CreateResult.Invalid -> setState { reduce(it, CategoryEditorChange.Errors(result.errors)) }
                }
            } else {
                when (val result = save(id, draft, state.baseVersion)) {
                    SaveResult.Saved -> sendEffect(CategoryEditorEffect.Saved)
                    SaveResult.NotVerified -> sendEffect(CategoryEditorEffect.RequireVerification)
                    SaveResult.NoLongerAvailable -> sendEffect(CategoryEditorEffect.NavigateBack)
                    SaveResult.InUse -> Unit
                    is SaveResult.Invalid -> setState { reduce(it, CategoryEditorChange.Errors(result.errors)) }
                }
            }
            setState { reduce(it, CategoryEditorChange.Saving(false)) }
        }
    }

    private fun confirmed(action: suspend (String) -> SaveResult) {
        val id = categoryId ?: return
        if (currentState.busy) return
        setState { reduce(it, CategoryEditorChange.Busy(true)) }
        viewModelScope.launch {
            val result = action(id)
            setState { reduce(it, CategoryEditorChange.Busy(false)) }
            confirm(EditorConfirm.None)
            if (result == SaveResult.NotVerified) sendEffect(CategoryEditorEffect.RequireVerification)
        }
    }

    private fun resolve(action: suspend (String) -> Unit) {
        val id = categoryId ?: return
        if (currentState.busy) return
        resolvedByPerson = true
        setState { reduce(it, CategoryEditorChange.Busy(true)) }
        viewModelScope.launch {
            action(id)
            setState { reduce(it, CategoryEditorChange.Busy(false)) }
        }
    }
}

/** A flow that never emits would hold `combine` back, so a default value is emitted first. */
private fun <T> Flow<T>.ifEmptyUse(default: T): Flow<T> = kotlinx.coroutines.flow.flow {
    emit(default)
    collect { emit(it) }
}
