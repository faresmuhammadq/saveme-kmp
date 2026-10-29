package com.fares.saveme.feature.notifications.ui.open

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fares.saveme.feature.notifications.domain.NotificationCategory
import com.fares.saveme.feature.notifications.ui.titleRes
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.push_opening

@Composable
fun NotificationOpenScreen(onIntent: (NotificationOpenIntent) -> Unit) = NotificationOpenScreen(null, onIntent)

/** [category] names the kind of notification being opened under "Opening…". */
@Composable
fun NotificationOpenScreen(category: NotificationCategory?, onIntent: (NotificationOpenIntent) -> Unit) {
    val colors = AppTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, top = 22.dp),
        ) {
            Skeleton(Modifier.size(44.dp), CircleShape)
            Skeleton(Modifier.fillMaxWidth(0.6f).heightIn(min = 30.dp), AppTheme.shapes.control)
            Skeleton(Modifier.fillMaxWidth().heightIn(min = 170.dp), RoundedCornerShape(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(3) { Skeleton(Modifier.weight(1f).heightIn(min = 64.dp), RoundedCornerShape(18.dp)) }
            }
            Skeleton(Modifier.fillMaxWidth().heightIn(min = 220.dp), RoundedCornerShape(24.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
                .fillMaxWidth()
                .background(colors.ink, RoundedCornerShape(18.dp))
                .padding(start = 18.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.push_opening),
                    style = AppTheme.type.label.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                    color = colors.background,
                    modifier = Modifier.testTag("open.progress").semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (category != null) {
                    Text(
                        stringResource(category.titleRes()),
                        style = AppTheme.type.caption.copy(fontSize = 12.5.sp),
                        color = colors.background.copy(alpha = 0.75f),
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .testTag("open.cancel")
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onIntent(NotificationOpenIntent.Cancel) }
                    .background(colors.background.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
            ) {
                Text(stringResource(Res.string.action_cancel), style = AppTheme.type.label.copy(fontWeight = FontWeight.Bold), color = colors.background)
            }
        }
    }
}

@Preview
@Composable
private fun NotificationOpenScreenPreview() {
    AppTheme { NotificationOpenScreen {} }
}
