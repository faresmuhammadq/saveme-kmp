package com.fares.saveme.devtools.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.fares.saveme.designsystem.catalog.CatalogSections
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.catalog_font_scale
import saveme.shared.generated.resources.catalog_language
import saveme.shared.generated.resources.catalog_language_ar
import saveme.shared.generated.resources.catalog_language_en
import saveme.shared.generated.resources.catalog_title
import saveme.shared.generated.resources.cd_back

private val fontScales = listOf(1f, 1.5f, 2f)

/**
 * Every component in every state (FR-008). The language toggle flips layout direction and
 * typography; copy follows the device language, as the rest of the app does.
 */
@Composable
fun ComponentCatalogScreen(
    state: ComponentCatalogState,
    onIntent: (ComponentCatalogIntent) -> Unit,
) {
    AppScreen(
        topBar = {
            AppTopAppBar(
                title = stringResource(Res.string.catalog_title),
                navigationIcon = {
                    AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), { onIntent(ComponentCatalogIntent.Back) })
                },
            )
        },
    ) {
        Text(stringResource(Res.string.catalog_language), style = AppTheme.type.label, color = AppTheme.colors.ink2)
        SegmentedControl(
            options = CatalogLanguage.entries,
            label = { stringResource(if (it == CatalogLanguage.English) Res.string.catalog_language_en else Res.string.catalog_language_ar) },
            selected = state.language,
            onSelect = { onIntent(ComponentCatalogIntent.LanguageSelected(it)) },
        )
        Text(stringResource(Res.string.catalog_font_scale), style = AppTheme.type.label, color = AppTheme.colors.ink2)
        SegmentedControl(
            options = fontScales,
            label = { "${it}×" },
            selected = state.fontScale,
            onSelect = { onIntent(ComponentCatalogIntent.FontScaleSelected(it)) },
        )
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalLayoutDirection provides if (state.language == CatalogLanguage.Arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalDensity provides Density(density.density, state.fontScale),
        ) {
            AppTheme {
                Column(Modifier.fillMaxSize()) { CatalogSections() }
            }
        }
    }
}

@Preview
@Composable
private fun ComponentCatalogScreenPreview() {
    AppTheme { ComponentCatalogScreen(ComponentCatalogState(), {}) }
}
