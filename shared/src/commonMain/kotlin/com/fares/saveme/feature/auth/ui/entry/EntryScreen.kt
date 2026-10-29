package com.fares.saveme.feature.auth.ui.entry

import com.fares.saveme.feature.auth.ui.AuthLogo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.fares.saveme.designsystem.icon.AppIcons
import saveme.shared.generated.resources.entryc_emergency_fund
import saveme.shared.generated.resources.entryc_have_account
import saveme.shared.generated.resources.entryc_left_to_spend
import saveme.shared.generated.resources.entryc_shared_wallet
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_create_account
import saveme.shared.generated.resources.action_dev_tools
import saveme.shared.generated.resources.app_name
import saveme.shared.generated.resources.entry_tagline
import saveme.shared.generated.resources.server_entry_link

@Composable
fun EntryScreen(state: EntryState, onIntent: (EntryIntent) -> Unit) {
    Column(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
        EntryIllustration(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars))
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), ambientColor = Color(0x14141E37), spotColor = Color(0x14141E37))
                .background(AppTheme.colors.surface, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 26.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthLogo(34.dp)
                Text(
                    stringResource(Res.string.app_name),
                    style = AppTheme.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
                    color = AppTheme.colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(
                stringResource(Res.string.entry_tagline),
                style = AppTheme.type.headline.copy(lineHeight = 31.sp),
                color = AppTheme.colors.ink,
            )
            PrimaryButton(
                stringResource(Res.string.action_create_account),
                { onIntent(EntryIntent.CreateAccountClicked) },
                modifier = Modifier.padding(top = 8.dp).testTag("entry.createAccount"),
            )
            AppTextButton(
                stringResource(Res.string.entryc_have_account),
                { onIntent(EntryIntent.SignInClicked) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("entry.signIn"),
            )
            state.serverAddress?.let { address ->
                AppTextButton(
                    stringResource(Res.string.server_entry_link, address),
                    { onIntent(EntryIntent.ServerClicked) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("entry.server"),
                )
            }
            if (state.isDebugTools) {
                AppTextButton(stringResource(Res.string.action_dev_tools), { onIntent(EntryIntent.DevToolsClicked) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Three tilted sample cards (budget ring, savings wallet, shared wallet): decoration only, hidden from screen readers. */
@Composable
private fun EntryIllustration(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val cardShadow = Color(0x1A141E37)
    Box(modifier.clearAndSetSemantics {}) {
        Column(
            modifier = Modifier
                .offset(28.dp, 70.dp)
                .width(220.dp)
                .graphicsLayer { rotationZ = -4f }
                .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = cardShadow, spotColor = cardShadow)
                .background(colors.surface, RoundedCornerShape(24.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(24.dp))
                .padding(16.dp),
        ) {
            Text(stringResource(Res.string.entryc_left_to_spend), style = AppTheme.type.caption, color = colors.ink2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                Canvas(Modifier.size(64.dp)) {
                    val width = 14f / 120f * size.width
                    val arc = Size(size.width - width, size.height - width)
                    val topLeft = Offset(width / 2, width / 2)
                    drawArc(colors.sunken, 0f, 360f, false, topLeft, arc, style = Stroke(width))
                    drawArc(colors.primary, -90f, 360f * 168f / 302f, false, topLeft, arc, style = Stroke(width, cap = StrokeCap.Round))
                }
                Text("9,700", style = AppTheme.type.numeralL.copy(fontSize = 26.sp), color = colors.ink)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-24).dp, y = 210.dp)
                .width(190.dp)
                .height(118.dp)
                .graphicsLayer { rotationZ = 3f }
                .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = cardShadow, spotColor = cardShadow)
                .background(AppTheme.money.positive, RoundedCornerShape(24.dp))
                .padding(16.dp),
        ) {
            Icon(AppIcons.Savings, contentDescription = null, tint = Color.White)
            Spacer(Modifier.weight(1f))
            Text(stringResource(Res.string.entryc_emergency_fund), style = AppTheme.type.caption, color = Color.White.copy(alpha = 0.85f))
            Text("120,000", style = AppTheme.type.numeralL.copy(fontSize = 20.sp), color = Color.White)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .offset(44.dp, 350.dp)
                .width(250.dp)
                .graphicsLayer { rotationZ = -2f }
                .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = cardShadow, spotColor = cardShadow)
                .background(colors.surface, RoundedCornerShape(20.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row {
                Avatar("L", colors.primary, colors.onPrimary)
                Avatar("K", AppTheme.money.positive, Color.White, Modifier.offset(x = (-8).dp))
            }
            Text(stringResource(Res.string.entryc_shared_wallet), style = AppTheme.type.label.copy(fontWeight = FontWeight.Bold), color = colors.ink)
        }
    }
}

@Composable
private fun Avatar(letter: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(28.dp).background(container, CircleShape)) {
        Text(letter, style = AppTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold), color = content)
    }
}

@Preview
@Composable
private fun EntryScreenPreview() {
    AppTheme { EntryScreen(EntryState(isDebugTools = true)) {} }
}
