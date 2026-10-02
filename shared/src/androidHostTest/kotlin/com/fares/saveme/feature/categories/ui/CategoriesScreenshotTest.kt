package com.fares.saveme.feature.categories.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.categories.domain.model.Category
import com.fares.saveme.feature.categories.domain.model.CategoryField
import com.fares.saveme.feature.categories.domain.model.CategoryFieldProblem
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import com.fares.saveme.feature.categories.ui.editor.CategoryEditorScreen
import com.fares.saveme.feature.categories.ui.editor.CategoryEditorState
import com.fares.saveme.feature.categories.ui.editor.ClashField
import com.fares.saveme.feature.categories.ui.editor.ClashLine
import com.fares.saveme.feature.categories.ui.editor.ClashValue
import com.fares.saveme.feature.categories.ui.editor.ConfirmCard
import com.fares.saveme.feature.categories.ui.editor.EditorConfirm
import com.fares.saveme.feature.categories.ui.editor.EditorSync
import com.fares.saveme.feature.categories.ui.list.CategoriesScreen
import com.fares.saveme.feature.categories.ui.list.CategoriesState
import com.fares.saveme.feature.categories.ui.list.CategoryCaptionUi
import com.fares.saveme.feature.categories.ui.list.CategoryRowUi
import com.fares.saveme.feature.categories.ui.list.CategoryStatusUi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/** The category screens' states in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, FR-025). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1600dp")
class CategoriesScreenshotTest(
    private val name: String,
    private val screen: @Composable () -> Unit,
) {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(config: String, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AppTheme { screen() }
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_categories_${name}_$config.png")
    }

    @Test
    fun englishLtr() = capture("en")

    @Test
    @Config(qualifiers = "+ar-rEG")
    fun arabicRtl() = capture("ar", rtl = true)

    @Test
    fun englishFontScale2() {
        RuntimeEnvironment.setFontScale(2f)
        capture("en_fs2")
    }

    companion object {
        private val updated = Instant.parse("2026-10-02T10:00:00Z")

        private fun own(
            name: String, icon: String? = "restaurant", color: String? = "#1F7F8C", status: CategoryStatusUi? = null,
            caption: CategoryCaptionUi = CategoryCaptionUi.None,
        ) = CategoryRowUi("o-$name", name, icon, color, caption, status, true)

        private fun default(name: String, icon: String, color: String) =
            CategoryRowUi("d-$name", name, icon, color, CategoryCaptionUi.Default, null, false)

        private val defaults = listOf(
            default("Groceries", "cart", "#2E7D32"), default("Dining out", "restaurant", "#EF6C00"), default("Utilities", "bolt", "#F9A825"),
            default("Kids", "child", "#F06292"),
        )

        private val populated = CategoriesState(
            own = listOf(
                own("Coffee", status = CategoryStatusUi.Waiting),
                own("Gym", "health", "#C62828", CategoryStatusUi.Changed),
                own("Books", "school", "#283593", CategoryStatusUi.NotSaved),
                own("Typo", "unknown-key", "#123456", CategoryStatusUi.NotDeleted),
                own("Plain", null, null),
            ),
            defaults = defaults,
            archived = listOf(own("Old hobby", "ticket", "#7B1FA2", caption = CategoryCaptionUi.Archived)),
            loaded = true,
            updatedAt = updated,
        )

        private val coffee = CategoryEditorState(
            categoryId = "c1", loaded = true, name = "Coffee", iconKey = "restaurant", colorHex = "#1F7F8C", baseVersion = 3,
            canArchive = true, canDelete = true,
        )

        private val theirs = Category("c1", "expense", "Café", "restaurant", "#2C7A5B", null, false, 6, Instant.parse("2026-09-01T00:00:00Z"))

        private fun dialog(state: CategoryEditorState): @Composable () -> Unit = {
            Column(Modifier.background(AppTheme.colors.background).padding(16.dp)) { ConfirmCard(state, {}) }
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            arrayOf("list_loaded", @Composable { CategoriesScreen(populated, {}) }),
            arrayOf("list_no_own", @Composable { CategoriesScreen(populated.copy(own = emptyList(), archived = emptyList()), {}) }),
            arrayOf("list_defaults_missing", @Composable {
                CategoriesScreen(populated.copy(defaults = emptyList(), defaultsMissing = true, archived = emptyList()), {})
            }),
            arrayOf("list_offline", @Composable {
                CategoriesScreen(populated.copy(offline = true, waitingCount = 1, refreshFailed = false), {})
            }),
            arrayOf("list_refresh_failed", @Composable { CategoriesScreen(populated.copy(refreshFailed = true), {}) }),
            arrayOf("list_loading", @Composable { CategoriesScreen(CategoriesState(), {}) }),
            arrayOf("editor_create", @Composable {
                CategoryEditorScreen(CategoryEditorState(loaded = true, name = "", kindWire = "income"), {})
            }),
            arrayOf("editor_edit", @Composable { CategoryEditorScreen(coffee, {}) }),
            arrayOf("editor_waiting", @Composable { CategoryEditorScreen(coffee.copy(sync = EditorSync.Waiting), {}) }),
            arrayOf("editor_clash", @Composable {
                CategoryEditorScreen(
                    coffee.copy(
                        sync = EditorSync.Clash(
                            listOf(
                                ClashLine(ClashField.Name, ClashValue.Text("Coffee"), ClashValue.Text(theirs.name)),
                                ClashLine(ClashField.Color, ClashValue.Color("#1F7F8C"), ClashValue.Color(theirs.color)),
                            ),
                            deletedElsewhere = false, isDelete = false,
                        ),
                    ),
                    {},
                )
            }),
            arrayOf("editor_delete_clash", @Composable {
                CategoryEditorScreen(
                    coffee.copy(
                        sync = EditorSync.Clash(
                            listOf(), deletedElsewhere = false, isDelete = true,
                        ),
                    ),
                    {},
                )
            }),
            arrayOf("editor_deleted_elsewhere", @Composable {
                CategoryEditorScreen(coffee.copy(sync = EditorSync.Clash(emptyList(), deletedElsewhere = true, isDelete = false)), {})
            }),
            arrayOf("editor_not_saved_field_error", @Composable {
                CategoryEditorScreen(
                    coffee.copy(
                        name = "", sync = EditorSync.Refused(RefusalReason.Invalid),
                        errors = mapOf(CategoryField.Name to CategoryFieldProblem.Server("OUT_OF_RANGE")),
                    ),
                    {},
                )
            }),
            arrayOf("editor_not_saved_other", @Composable {
                CategoryEditorScreen(coffee.copy(sync = EditorSync.Refused(RefusalReason.Other("req-42"))), {})
            }),
            arrayOf("editor_in_use_refusal", @Composable {
                CategoryEditorScreen(coffee.copy(sync = EditorSync.Refused(RefusalReason.InUse), canDelete = false), {})
            }),
            arrayOf("editor_in_use_note", @Composable { CategoryEditorScreen(coffee.copy(canDelete = false, inUseNote = true), {}) }),
            arrayOf("editor_archived", @Composable { CategoryEditorScreen(coffee.copy(archived = true, canArchive = false), {}) }),
            arrayOf("dialog_archive", dialog(coffee.copy(confirm = EditorConfirm.Archive))),
            arrayOf("dialog_delete", dialog(coffee.copy(confirm = EditorConfirm.Delete))),
        )
    }
}
