package com.fares.saveme.feature.wallets.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AmountDisplay
import com.fares.saveme.designsystem.component.WalletType as CardType
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SyncBanner
import com.fares.saveme.designsystem.component.SyncBannerState
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.component.ltr
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.WalletIcons
import com.fares.saveme.designsystem.icon.WlcArchive
import com.fares.saveme.designsystem.icon.WlcGroup
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.designsystem.tokens.WalletAccents
import com.fares.saveme.feature.wallets.domain.model.CurrencyTotal
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletType
import com.fares.saveme.feature.wallets.ui.cardType
import com.fares.saveme.feature.wallets.ui.label
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_done
import saveme.shared.generated.resources.error_feature_not_entitled
import saveme.shared.generated.resources.leave_done
import saveme.shared.generated.resources.money_owed
import saveme.shared.generated.resources.role_editor
import saveme.shared.generated.resources.role_owner
import saveme.shared.generated.resources.role_viewer
import saveme.shared.generated.resources.wallet_status_clash
import saveme.shared.generated.resources.wallet_status_not_saved
import saveme.shared.generated.resources.wallet_status_waiting
import saveme.shared.generated.resources.wallets_archived
import saveme.shared.generated.resources.wlc_archived_count
import saveme.shared.generated.resources.wlc_owed
import saveme.shared.generated.resources.wallets_empty_action
import saveme.shared.generated.resources.wallets_empty_body
import saveme.shared.generated.resources.wallets_empty_title
import saveme.shared.generated.resources.wallets_new
import saveme.shared.generated.resources.wallets_refresh_failed
import saveme.shared.generated.resources.wallets_title
import saveme.shared.generated.resources.wallets_total_caption
import saveme.shared.generated.resources.wallets_total_label
import saveme.shared.generated.resources.wallets_updated_at
import kotlin.math.abs
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsScreen(state: WalletsState, onIntent: (WalletsIntent) -> Unit) {
    CalmScreen(
        title = stringResource(Res.string.wallets_title),
        scrollable = false,
        actions = {
            CalmCircleButton(
                icon = AppIcons.Plus,
                contentDescription = stringResource(Res.string.wallets_new),
                onClick = { onIntent(WalletsIntent.CreateClicked) },
                tint = AppTheme.colors.primary,
                modifier = Modifier.testTag("wallets.create"),
            )
        },
        modifier = Modifier.testTag("wallets.list"),
    ) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { onIntent(WalletsIntent.Refresh) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            WalletsContent(state, onIntent)
        }
    }
}

@Composable
private fun WalletsContent(state: WalletsState, onIntent: (WalletsIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = AppTheme.spacing.m),
    ) {
        if (state.offline && state.waitingCount > 0) SyncBanner(SyncBannerState.Offline(state.waitingCount))
        if (state.notEntitledNotice) {
            InlineAlert(
                tone = AlertTone.Warning,
                text = stringResource(Res.string.error_feature_not_entitled),
                modifier = Modifier.testTag("wallets.notEntitled"),
                actionLabel = stringResource(Res.string.action_done),
                onAction = { onIntent(WalletsIntent.DismissNotice) },
            )
        }
        state.leftNotice?.let { name ->
            InlineAlert(
                tone = AlertTone.Info,
                text = stringResource(Res.string.leave_done, name),
                modifier = Modifier.testTag("wallets.leftNotice"),
                actionLabel = stringResource(Res.string.action_done),
                onAction = { onIntent(WalletsIntent.DismissLeftNotice) },
            )
        }
        if (state.refreshFailed) InlineAlert(AlertTone.Warning, stringResource(Res.string.wallets_refresh_failed))
        when {
            !state.loaded -> repeat(3) { Skeleton(Modifier.fillMaxWidth().height(72.dp), AppTheme.shapes.card) }
            state.isEmpty -> EmptyState(
                title = stringResource(Res.string.wallets_empty_title),
                body = stringResource(Res.string.wallets_empty_body),
                modifier = Modifier.testTag("wallets.empty"),
                actionLabel = stringResource(Res.string.wallets_empty_action),
                onAction = { onIntent(WalletsIntent.CreateClicked) },
            )
            else -> {
                if (state.totals.isNotEmpty()) TotalCard(state.totals, state.active)
                var showArchived by rememberSaveable { mutableStateOf(false) }
                TileGrid(state.active, "wallets.card", onIntent, archivedCount = state.archived.size) { showArchived = !showArchived }
                if (state.archived.isNotEmpty() && showArchived) {
                    Text(
                        stringResource(Res.string.wallets_archived),
                        style = AppTheme.type.label,
                        color = AppTheme.colors.ink2,
                        modifier = Modifier.padding(top = AppTheme.spacing.xs).semantics { heading() }.testTag("wallets.archivedHeader"),
                    )
                    TileGrid(state.archived, "wallets.archived", onIntent)
                }
            }
        }
        if (state.offline && state.updatedAt != null) {
            Text(
                stringResource(Res.string.wallets_updated_at, formatDateTime(state.updatedAt, state.timeZoneId)),
                style = AppTheme.type.caption,
                color = AppTheme.colors.caption,
                modifier = Modifier.testTag("wallets.updatedAt"),
            )
        }
    }
}

private val Amber = Color(0xFFC9973A)
private val Violet = Color(0xFF6B49A8)

@Composable
private fun typeColor(type: CardType): Color = when (type) {
    CardType.Savings -> AppTheme.money.positive
    CardType.Checking -> AppTheme.colors.primary
    CardType.Cash -> Amber
    CardType.Investment -> Violet
    CardType.CreditCard, CardType.Loan -> AppTheme.colors.error
}

/** One slice of the total's split bar: what the non-liability wallets of the currency hold, by type. */
private data class Slice(val type: CardType, val minor: Long)

private val sliceOrder = listOf(
    CardType.Savings,
    CardType.Checking,
    CardType.Cash,
    CardType.Investment,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TotalCard(totals: List<CurrencyTotal>, wallets: List<WalletCardUi>) {
    var selected by rememberSaveable { mutableStateOf(totals.first().currency) }
    val total = totals.firstOrNull { it.currency == selected } ?: totals.first()
    val inCurrency = wallets.filter { it.currency == total.currency }
    val slices = sliceOrder.mapNotNull { type ->
        val minor = inCurrency.filter { it.type.cardType() == type && it.amountMinor > 0 }.sumOf { it.amountMinor }
        if (minor > 0) Slice(type, minor) else null
    }
    val owed = inCurrency.filter { it.type.cardType().isLiability && it.amountMinor < 0 }.sumOf { -it.amountMinor }
    CalmCard(modifier = Modifier.testTag("wallets.total.${total.currency}")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(Res.string.wallets_total_label) + " \u00B7 " +
                    pluralStringResource(Res.plurals.wallets_total_caption, total.walletCount, total.walletCount),
                style = AppTheme.type.label,
                color = AppTheme.colors.ink2,
                modifier = Modifier.weight(1f),
            )
            CurrencyTabs(totals.map { it.currency }, total.currency) { selected = it }
        }
        Spacer(Modifier.height(6.dp))
        AmountDisplay(total.totalMinor, total.currency, style = AppTheme.type.numeralHero.copy(fontSize = 38.sp, fontWeight = FontWeight.ExtraBold))
        if (slices.isNotEmpty()) {
            val sum = slices.sumOf { it.minor }
            val labels = slices.map { it.label() }
            val description = slices.indices.joinToString(", ") { i -> labels[i] + " " + (slices[i].minor * 100 / sum) + "%" }
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxWidth().height(10.dp).clearAndSetSemantics { contentDescription = description },
            ) {
                slices.forEachIndexed { index, slice ->
                    val first = index == 0
                    val last = index == slices.lastIndex
                    Box(
                        Modifier
                            .weight(slice.minor.toFloat())
                            .fillMaxHeight()
                            .background(
                                typeColor(slice.type),
                                RoundedCornerShape(
                                    topStart = if (first) 6.dp else 0.dp, bottomStart = if (first) 6.dp else 0.dp,
                                    topEnd = if (last) 6.dp else 0.dp, bottomEnd = if (last) 6.dp else 0.dp,
                                ),
                            ),
                    )
                }
            }
        }
        if (slices.isNotEmpty() || owed > 0) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    slices.forEach { slice ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(8.dp).background(typeColor(slice.type), CircleShape))
                            Text(slice.label(), style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink2, maxLines = 1)
                        }
                    }
                }
                if (owed > 0) {
                    Text(
                        stringResource(Res.string.wlc_owed, formatMoney(owed, total.currency)),
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold).ltr(),
                        color = AppTheme.money.over,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Slice.label(): String = when (type) {
    CardType.Savings -> WalletType.Savings.label()
    CardType.Checking -> WalletType.Checking.label()
    CardType.Cash -> WalletType.Cash.label()
    CardType.Investment -> WalletType.Investment.label()
    CardType.CreditCard -> WalletType.CreditCard.label()
    CardType.Loan -> WalletType.Loan.label()
}

/** The EGP / USD switch: a sunken pill with the chosen currency on a white chip; every chip keeps a 48dp touch height. */
@Composable
private fun CurrencyTabs(currencies: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.background(AppTheme.colors.sunken, AppTheme.shapes.pill).padding(3.dp),
        ) {
            currencies.forEach { currency ->
                val on = currency == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(28.dp)
                        .background(if (on) AppTheme.colors.surface else Color.Transparent, AppTheme.shapes.pill)
                        .testTag("wallets.currency.$currency")
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(currency) })
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        currency,
                        style = AppTheme.type.caption.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
                        color = if (on) AppTheme.colors.ink else AppTheme.colors.ink2,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TileGrid(
    cards: List<WalletCardUi>,
    tagPrefix: String,
    onIntent: (WalletsIntent) -> Unit,
    archivedCount: Int = 0,
    onArchivedClick: () -> Unit = {},
) {
    val cells = mutableListOf<@Composable (Modifier) -> Unit>()
    cards.forEachIndexed { index, card -> cells += { modifier -> WalletTile(card, "$tagPrefix.$index", onIntent, modifier) } }
    if (archivedCount > 0) cells += { modifier -> ArchivedTile(archivedCount, onArchivedClick, modifier) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                pair.forEach { cell -> cell(Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The dashed "Archived · n" tile that opens or closes the archived wallets below the grid. */
@Composable
private fun ArchivedTile(count: Int, onClick: () -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(22.dp)
    val dash = colors.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = modifier
            .testTag("wallets.archivedTile")
            .heightIn(min = 118.dp)
            .clip(shape)
            .background(colors.sunken, shape)
            .drawBehind {
                drawRoundRect(
                    dash,
                    cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(AppIcons.WlcArchive, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(24.dp))
        Text(
            stringResource(Res.string.wlc_archived_count, count),
            style = AppTheme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = colors.ink2,
        )
    }
}

@Composable
private fun WalletTile(card: WalletCardUi, tag: String, onIntent: (WalletsIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val accent = WalletAccents.find(card.accentKey)
    val cardType = card.type.cardType()
    val liability = cardType.isLiability
    val owing = liability && card.amountMinor != 0L
    val solid = accent != null && !card.archived
    val container = when {
        card.archived -> colors.sunken
        solid -> accent!!.swatch
        owing -> AppTheme.money.overContainer
        else -> colors.surface
    }
    val primaryText = when {
        solid -> colors.onPrimary
        owing -> AppTheme.money.over
        card.archived -> colors.ink2
        else -> colors.ink
    }
    val secondaryText = when {
        solid -> colors.onPrimary.copy(alpha = 0.85f)
        owing -> AppTheme.money.over
        else -> colors.ink2
    }
    val iconTint = when {
        solid -> colors.onPrimary
        owing -> AppTheme.money.over
        card.archived -> colors.ink2
        card.shared -> Violet
        else -> accent?.tint ?: typeColor(cardType)
    }
    val shape = RoundedCornerShape(22.dp)
    val status = card.status?.let { kind ->
        when (kind) {
            WalletStatusUi.Waiting -> AppIcons.Sync to stringResource(Res.string.wallet_status_waiting)
            WalletStatusUi.Changed -> AppIcons.Warning to stringResource(Res.string.wallet_status_clash)
            WalletStatusUi.NotSaved -> AppIcons.Error to stringResource(Res.string.wallet_status_not_saved)
        }
    }
    val roleLabel = card.takeIf { it.shared || it.role != null }?.let { it.role ?: it.myRole }?.let { role ->
        stringResource(
            when (role) {
                WalletRole.Owner -> Res.string.role_owner
                WalletRole.Editor -> Res.string.role_editor
                WalletRole.Viewer -> Res.string.role_viewer
            },
        )
    }
    Column(
        modifier = modifier
            .testTag(tag)
            .heightIn(min = 118.dp)
            .clip(shape)
            .background(container, shape)
            .then(if (container == colors.surface) Modifier.border(1.dp, colors.outlineVariant, shape) else Modifier)
            .clickable(role = Role.Button) { onIntent(WalletsIntent.WalletClicked(card.id)) }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Icon(if (card.shared) AppIcons.WlcGroup else WalletIcons.find(card.iconKey) ?: cardType.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.weight(1f))
            if (roleLabel != null) {
                Text(
                    roleLabel,
                    style = AppTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = if (solid) colors.onPrimary else colors.ink2,
                    maxLines = 1,
                    modifier = Modifier
                        .background(if (solid) colors.onPrimary.copy(alpha = 0.2f) else colors.sunken, AppTheme.shapes.pill)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (liability) card.name + " \u00B7 " + stringResource(Res.string.money_owed) else card.name,
            style = AppTheme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (status != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.testTag("$tag.status"),
            ) {
                Icon(status.first, contentDescription = null, tint = secondaryText, modifier = Modifier.size(14.dp))
                Text(status.second, style = AppTheme.type.caption, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatMoney(if (liability) abs(card.amountMinor) else card.amountMinor, card.currency),
                style = AppTheme.type.itemTitle.copy(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold).ltr(),
                color = if (!liability && !solid && card.amountMinor < 0) colors.error else primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private val previewCards = listOf(
    WalletCardUi("1", "Pocket", WalletType.Cash, 25_000, "EGP", "cash", "#2C7A5B", WalletStatusUi.Waiting, false, null),
    WalletCardUi("2", "Visa", WalletType.CreditCard, -100_000, "EGP", null, null, null, false, null),
)

@Preview
@Composable
private fun WalletsScreenPreview() {
    AppTheme {
        WalletsScreen(
            WalletsState(
                active = previewCards,
                totals = listOf(CurrencyTotal("EGP", -75_000, 2)),
                loaded = true,
                updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
            ),
            {},
        )
    }
}
