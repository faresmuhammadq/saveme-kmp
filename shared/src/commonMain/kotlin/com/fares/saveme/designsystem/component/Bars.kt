package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme

/** 64dp top bar below the status bar. The back arrow mirrors in RTL through the icon's autoMirror. */
@Composable
fun AppTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(AppTheme.sizes.topBar)
            .padding(horizontal = AppTheme.spacing.xxs),
    ) {
        if (navigationIcon != null) navigationIcon() else Spacer(Modifier.width(AppTheme.spacing.s))
        Text(
            text = title,
            style = AppTheme.type.title,
            color = AppTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = AppTheme.spacing.xxs),
        )
        actions()
    }
}

data class NavItem(val label: String, val icon: ImageVector)

/** 80dp bottom navigation with a 56×30 pill indicator on primaryContainer. */
@Composable
fun AppNavigationBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(AppTheme.sizes.navBar)
            .background(AppTheme.colors.surface)
            .selectableGroup(),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(AppTheme.sizes.navBar)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(AppTheme.sizes.navIndicatorWidth, AppTheme.sizes.navIndicatorHeight)
                        .background(if (selected) AppTheme.colors.primaryContainer else AppTheme.colors.surface, AppTheme.shapes.pill),
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = if (selected) AppTheme.colors.onPrimaryContainer else AppTheme.colors.ink2,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(AppTheme.spacing.xxs))
                Text(
                    item.label,
                    style = AppTheme.type.caption,
                    color = if (selected) AppTheme.colors.ink else AppTheme.colors.ink2,
                    maxLines = 1,
                )
            }
        }
    }
}
