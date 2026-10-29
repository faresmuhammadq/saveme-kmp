package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme

/*
 * "Calm tabs" chrome (redesign option B): round back button, big left-aligned title, outlined 24dp cards,
 * a pinned 56dp action bar and the five-slot tab bar with a raised add button. Screens compose these instead
 * of AppTopAppBar / AppScreen.
 */

/** Horizontal page gutter of the calm layout. */
val CalmGutter: Dp = 20.dp

/** 44dp circle with a hairline border; the back and search buttons of a calm header. */
@Composable
fun CalmCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = AppTheme.colors.ink,
    container: Color = AppTheme.colors.surface,
    bordered: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(container, CircleShape)
                .then(if (bordered) Modifier.border(1.dp, AppTheme.colors.outlineVariant, CircleShape) else Modifier),
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) tint else AppTheme.colors.outline, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun CalmBackButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) =
    CalmCircleButton(AppIcons.ArrowBack, contentDescription, onClick, modifier)

/**
 * Screen scaffold of the calm layout: canvas background, an optional round back button with [actions] on the right,
 * an optional [eyebrow] line and a 28sp [title] (a heading for screen readers), then the scrolling body.
 * [bottomBar] holds a pinned [CalmActionBar]; [titleSlot] replaces the plain title text when it needs more than a string.
 */
@Composable
fun CalmScreen(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backDescription: String = "",
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    bottomBar: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    spacing: Dp = 14.dp,
    titleSlot: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        containerColor = AppTheme.colors.background,
        modifier = modifier,
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = CalmGutter, end = CalmGutter, top = 14.dp, bottom = AppTheme.spacing.m),
        ) {
            if (onBack != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    CalmBackButton(onBack, backDescription)
                    Spacer(Modifier.weight(1f))
                    actions()
                }
            }
            if (titleSlot != null) {
                titleSlot()
            } else if (title != null || eyebrow != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        if (eyebrow != null) {
                            Text(eyebrow, style = AppTheme.type.label, color = AppTheme.colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (title != null) CalmTitle(title)
                    }
                    // Without a back button the actions sit on the title's row (tab screens).
                    if (onBack == null) actions()
                }
            }
            content()
        }
    }
}

/** The 28sp extra-bold screen title. */
@Composable
fun CalmTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.headline.copy(fontSize = 28.sp),
        color = AppTheme.colors.ink,
        modifier = modifier.semantics { heading() },
    )
}

/** White card, 1dp hairline, 24dp corners. */
@Composable
fun CalmCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    container: Color = AppTheme.colors.surface,
    bordered: Boolean = true,
    radius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container, shape)
            .then(if (bordered) Modifier.border(1.dp, AppTheme.colors.outlineVariant, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Small rounded square holding an icon on a tinted ground (wallet, category, event badges). */
@Composable
fun CalmIconTile(
    icon: ImageVector,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    radius: Dp = 12.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).background(container, RoundedCornerShape(radius)),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** 36dp pill filter: ink-filled when selected, outlined otherwise. The touch area stays 48dp. */
@Composable
fun CalmChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Button, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .height(36.dp)
                .background(if (selected) AppTheme.colors.ink else AppTheme.colors.surface, AppTheme.shapes.pill)
                .then(if (selected) Modifier else Modifier.border(1.dp, AppTheme.colors.outlineVariant, AppTheme.shapes.pill))
                .padding(horizontal = 14.dp),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, tint = if (selected) AppTheme.colors.background else AppTheme.colors.ink, modifier = Modifier.size(17.dp))
            }
            Text(
                label,
                style = AppTheme.type.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
                color = if (selected) AppTheme.colors.background else AppTheme.colors.ink,
                maxLines = 1,
            )
        }
    }
}

/** Section label in the calm lists: small, bold, secondary ink. */
@Composable
fun CalmSectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            text,
            style = AppTheme.type.label,
            color = AppTheme.colors.ink2,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** Pinned bottom area of an editor or a step: the main action(s) over the canvas, with the 20dp gutter. */
@Composable
fun CalmActionBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = CalmGutter, end = CalmGutter, top = 12.dp, bottom = 16.dp),
        content = content,
    )
}

/** The four destinations of the tab bar; the add button between the second and third is not one. */
enum class MainTab { Home, Wallets, Budgets, Reports }

/**
 * 80dp tab bar: Home, Wallets, a raised add button, Budgets, Reports. [onAdd] opens a new transaction.
 * The selected tab is filled and drawn in the primary colour; the bar pads itself for the gesture area.
 */
@Composable
fun AppTabBar(
    selected: MainTab?,
    onSelect: (MainTab) -> Unit,
    onAdd: () -> Unit,
    labels: Map<MainTab, String>,
    addDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().height(AppTheme.sizes.navBar - 1.dp).padding(horizontal = 8.dp).selectableGroup(),
        ) {
            @Composable
            fun RowScope.Tab(tab: MainTab, icon: ImageVector) {
                val on = tab == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .testTag("tab.${tab.name.lowercase()}")
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) }),
                ) {
                    Icon(icon, null, tint = if (on) colors.primary else colors.ink2, modifier = Modifier.size(24.dp))
                    Text(
                        labels[tab].orEmpty(),
                        style = AppTheme.type.overline.copy(fontSize = 11.sp, letterSpacing = TextUnit.Unspecified, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                        color = if (on) colors.primary else colors.ink2,
                        maxLines = 1,
                    )
                }
            }
            Tab(MainTab.Home, AppIcons.Home)
            Tab(MainTab.Wallets, AppIcons.Wallet)
            Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.weight(1f).fillMaxSize()) {
                val shape = RoundedCornerShape(18.dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(y = (-18).dp)
                        .size(56.dp)
                        .shadow(8.dp, shape, ambientColor = colors.primary.copy(alpha = 0.35f), spotColor = colors.primary.copy(alpha = 0.35f))
                        .background(colors.primary, shape)
                        .semantics { contentDescription = addDescription }
                        .testTag("tab.add")
                        .clickable(role = Role.Button, onClick = onAdd),
                ) {
                    Icon(AppIcons.Plus, null, tint = colors.onPrimary, modifier = Modifier.size(28.dp))
                }
            }
            Tab(MainTab.Budgets, AppIcons.Budgets)
            Tab(MainTab.Reports, AppIcons.Reports)
        }
    }
}

/** A thin vertical rule between two figures of a [CalmCard]. */
@Composable
fun CalmDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.outlineVariant))
}

@Composable
fun CalmVRule(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).background(AppTheme.colors.outlineVariant))
}
