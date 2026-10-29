package com.fares.saveme.feature.transactions.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AmountField
import com.fares.saveme.designsystem.component.AppAlertDialog
import com.fares.saveme.designsystem.component.AppDatePickerDialog
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.AppSnackbarHost
import com.fares.saveme.designsystem.component.CalmGutter
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.CategoryChoiceGroup
import com.fares.saveme.designsystem.component.CategoryChoiceItem
import com.fares.saveme.designsystem.component.CategoryChoiceSheet
import com.fares.saveme.designsystem.component.CategoryRow
import com.fares.saveme.designsystem.component.DateField
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.component.SettingsSwitchRow
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.icon.TxNote
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.transactions.domain.model.AddedBy
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.transactions.ui.label
import com.fares.saveme.feature.transactions.ui.labelRes
import com.fares.saveme.feature.transactions.ui.text
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_accept_deletion
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_delete_anyway
import saveme.shared.generated.resources.action_discard
import saveme.shared.generated.resources.action_keep_transaction
import saveme.shared.generated.resources.action_restore_with_mine
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.transactions_today
import saveme.shared.generated.resources.txle_save_expense
import saveme.shared.generated.resources.txle_save_income
import saveme.shared.generated.resources.txle_save_transfer
import saveme.shared.generated.resources.txle_all_categories
import saveme.shared.generated.resources.category_choice_current
import saveme.shared.generated.resources.category_choice_defaults
import saveme.shared.generated.resources.category_choice_empty
import saveme.shared.generated.resources.category_choice_own
import saveme.shared.generated.resources.category_choice_title
import saveme.shared.generated.resources.category_keep_mine
import saveme.shared.generated.resources.category_none
import saveme.shared.generated.resources.category_unavailable
import saveme.shared.generated.resources.category_use_theirs
import saveme.shared.generated.resources.filter_kind
import saveme.shared.generated.resources.tx_added_by
import saveme.shared.generated.resources.tx_added_by_former
import saveme.shared.generated.resources.tx_added_by_other
import saveme.shared.generated.resources.tx_choose_destination
import saveme.shared.generated.resources.tx_clash_body
import saveme.shared.generated.resources.tx_clash_delete_body
import saveme.shared.generated.resources.tx_cleared_help
import saveme.shared.generated.resources.tx_cleared_no
import saveme.shared.generated.resources.tx_cleared_yes
import saveme.shared.generated.resources.tx_create_wallet
import saveme.shared.generated.resources.tx_deleted_body
import saveme.shared.generated.resources.tx_delete
import saveme.shared.generated.resources.tx_delete_body
import saveme.shared.generated.resources.tx_delete_title
import saveme.shared.generated.resources.tx_destinations_empty
import saveme.shared.generated.resources.tx_field_amount
import saveme.shared.generated.resources.tx_field_category
import saveme.shared.generated.resources.tx_field_cleared
import saveme.shared.generated.resources.tx_field_date
import saveme.shared.generated.resources.tx_field_destination
import saveme.shared.generated.resources.tx_field_details
import saveme.shared.generated.resources.tx_field_note
import saveme.shared.generated.resources.tx_field_person
import saveme.shared.generated.resources.recurring_make_recurring
import saveme.shared.generated.resources.recurring_match_offer
import saveme.shared.generated.resources.recurring_match_title
import saveme.shared.generated.resources.recurring_open_rule
import saveme.shared.generated.resources.recurring_rule_title
import saveme.shared.generated.resources.tx_field_wallet
import saveme.shared.generated.resources.tx_from_recurring
import saveme.shared.generated.resources.tx_kind_locked_note
import saveme.shared.generated.resources.tx_more
import saveme.shared.generated.resources.tx_no_wallet
import saveme.shared.generated.resources.tx_other_device
import saveme.shared.generated.resources.tx_save
import saveme.shared.generated.resources.tx_status_clash
import saveme.shared.generated.resources.tx_status_deleted
import saveme.shared.generated.resources.tx_this_phone
import saveme.shared.generated.resources.tx_title_edit
import saveme.shared.generated.resources.tx_title_new
import saveme.shared.generated.resources.tx_unknown_note
import saveme.shared.generated.resources.tx_viewer_note
import saveme.shared.generated.resources.tx_wallets_locked_note
import saveme.shared.generated.resources.wallet_status_waiting

@Composable
fun TransactionEditorScreen(
    state: TransactionEditorState,
    onIntent: (TransactionEditorIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val ready = state.loaded && !state.noWallet
    val saveLabel = stringResource(
        if (!state.isCreate) {
            Res.string.tx_save
        } else {
            when (state.kind) {
                TransactionKind.Income -> Res.string.txle_save_income
                TransactionKind.Transfer -> Res.string.txle_save_transfer
                else -> Res.string.txle_save_expense
            }
        },
    )
    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        containerColor = SheetScrim,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Spacer(Modifier.height(72.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(AppTheme.colors.surface)
                    .imePadding(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = CalmGutter, end = CalmGutter, top = 10.dp, bottom = 12.dp),
                ) {
                    SheetHeader(state, onIntent)
                    EditorBody(state, onIntent)
                }
                if (ready && (state.canEdit || state.canDelete)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(start = CalmGutter, end = CalmGutter, top = 4.dp, bottom = 22.dp),
                    ) {
                        if (state.canEdit) {
                            PrimaryButton(
                                saveLabel,
                                { onIntent(TransactionEditorIntent.SaveClicked) },
                                modifier = Modifier.testTag("txEditor.save"),
                                enabled = state.canSave,
                                loading = state.saving,
                            )
                        }
                        if (state.canDelete) {
                            DestructiveButton(
                                stringResource(Res.string.tx_delete),
                                { onIntent(TransactionEditorIntent.DeleteClicked) },
                                modifier = Modifier.testTag("txEditor.delete"),
                            )
                        }
                    }
                }
            }
        }
    }
    Dialogs(state, onIntent)
}

private val SheetScrim = Color(0xFF5E6574)

@Composable
private fun ColumnScope.EditorBody(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    if (!state.loaded) {
        repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
        return
    }
    if (state.noWallet) {
        EmptyState(
            title = stringResource(Res.string.tx_no_wallet),
            body = "",
            actionLabel = stringResource(Res.string.tx_create_wallet),
            onAction = { onIntent(TransactionEditorIntent.CreateWalletClicked) },
            modifier = Modifier.testTag("txEditor.noWallet"),
            actionModifier = Modifier.testTag("txEditor.createWallet"),
        )
        return
    }
    StatusArea(state, onIntent)
    Notes(state)
    KindSection(state, onIntent)
    AmountSection(state, onIntent)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
        WalletSection(state, onIntent)
        DateChip(state, onIntent)
    }
    state.errors[TransactionField.Date]?.let {
        Text(it.text(TransactionField.Date), style = AppTheme.type.caption, color = AppTheme.colors.error, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    CategoryOrDestination(state, onIntent)
    NoteField(state, onIntent)
    MoreAndRecurring(state, onIntent)
    MoreDetails(state, onIntent)
    MatchOffers(state, onIntent)
    Captions(state, onIntent)
}

/** Sheet look: grab handle, title and a round close button. */
@Composable
private fun SheetHeader(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 5.dp)
                .background(AppTheme.colors.outlineVariant, AppTheme.shapes.pill),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(if (state.isCreate) Res.string.tx_title_new else Res.string.tx_title_edit),
                style = AppTheme.type.headline.copy(fontSize = 20.sp),
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            CalmCircleButton(
                AppIcons.Close,
                stringResource(Res.string.cd_back),
                { onIntent(TransactionEditorIntent.Back) },
                container = AppTheme.colors.sunken,
                bordered = false,
            )
        }
    }
}

@Composable
private fun AmountSection(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    val focus = remember { FocusRequester() }
    if (state.isCreate) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
    val error = state.errors[TransactionField.Amount]?.text(TransactionField.Amount)
    val amountStyle = AppTheme.type.numeralL.copy(
        fontSize = 56.sp,
        fontWeight = FontWeight.ExtraBold,
        color = AppTheme.colors.ink,
        textAlign = TextAlign.Center,
        textDirection = TextDirection.Ltr,
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fieldWidth = with(density) { measurer.measure(state.amountText.ifEmpty { "0" }, amountStyle).size.width.toDp() + 6.dp }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(stringResource(Res.string.tx_field_amount), style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink2)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                state.currency,
                style = AppTheme.type.title.copy(fontSize = 22.sp, textDirection = TextDirection.Ltr),
                color = AppTheme.colors.ink2,
                modifier = Modifier.padding(top = 12.dp),
            )
            BasicTextField(
                value = state.amountText,
                onValueChange = { onIntent(TransactionEditorIntent.AmountChanged(it)) },
                enabled = state.canEdit,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = amountStyle,
                cursorBrush = SolidColor(AppTheme.colors.primary),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (state.amountText.isEmpty()) {
                            Text("0", style = AppTheme.type.numeralL.copy(fontSize = 56.sp, fontWeight = FontWeight.ExtraBold), color = AppTheme.colors.outline)
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .width(maxOf(fieldWidth, 150.dp).coerceAtMost(260.dp))
                    .focusRequester(focus)
                    .testTag("txEditor.amount"),
            )
        }
        if (error != null) Text(error, style = AppTheme.type.caption, color = AppTheme.colors.error)
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(36.dp)
                .border(1.dp, AppTheme.colors.outlineVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(18.dp))
            Text(text, style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DateChip(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    InfoChip(
        AppIcons.Calendar,
        state.date?.let { if (it == today) stringResource(Res.string.transactions_today) else formatDate(it) }.orEmpty(),
        { onIntent(TransactionEditorIntent.DateClicked) },
        enabled = state.canEdit,
        modifier = Modifier.testTag("txEditor.date"),
    )
}

@Composable
private fun NoteField(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    val label = stringResource(Res.string.tx_field_note)
    val error = state.errors[TransactionField.Note]?.text(TransactionField.Note)
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(AppTheme.colors.sunken, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
        ) {
            Icon(AppIcons.TxNote, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(22.dp))
            BasicTextField(
                value = state.note,
                onValueChange = { onIntent(TransactionEditorIntent.NoteChanged(it)) },
                enabled = state.canEdit,
                textStyle = AppTheme.type.body.copy(color = AppTheme.colors.ink),
                cursorBrush = SolidColor(AppTheme.colors.primary),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.note.isEmpty()) Text(label, style = AppTheme.type.body, color = AppTheme.colors.caption)
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).testTag("txEditor.note"),
            )
        }
        if (error != null) Text(error, style = AppTheme.type.caption, color = AppTheme.colors.error, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MoreAndRecurring(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        LinkButton(
            stringResource(Res.string.tx_more),
            { onIntent(TransactionEditorIntent.MoreToggled) },
            Modifier.testTag("txEditor.more"),
        )
        if (state.canMakeRecurring) {
            LinkButton(
                stringResource(Res.string.recurring_make_recurring),
                { onIntent(TransactionEditorIntent.MakeRecurringClicked) },
                Modifier.testTag("txEditor.makeRecurring"),
                enabled = !state.saving && !state.busy,
            )
        }
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text,
            style = AppTheme.type.caption.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
            color = if (enabled) AppTheme.colors.primary else AppTheme.colors.outline,
        )
    }
}

@Composable
private fun Notes(state: TransactionEditorState) {
    if (state.isCreate) return
    when {
        state.kind == TransactionKind.Unknown -> Note(stringResource(Res.string.tx_unknown_note))
        !state.canEdit -> Note(stringResource(Res.string.tx_viewer_note))
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("txEditor.readOnly"))
}

@Composable
private fun WalletSection(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    if (!state.walletEditable) return
    InfoChip(
        AppIcons.Wallet,
        state.walletChoices.firstOrNull { it.id == state.walletId }?.name.orEmpty(),
        { onIntent(TransactionEditorIntent.WalletClicked) },
        enabled = state.walletChoices.size > 1,
        modifier = Modifier.testTag("txEditor.wallet"),
    )
}

@Composable
private fun KindSection(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    if (state.kindEditable) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            TransactionKind.selectable.forEach { kind ->
                val on = kind == state.kind
                val (container, tint) = when (kind) {
                    TransactionKind.Expense -> AppTheme.money.overContainer to AppTheme.money.over
                    TransactionKind.Income -> AppTheme.money.positiveContainer to AppTheme.money.positive
                    else -> AppTheme.colors.primaryContainer to AppTheme.colors.primary
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .selectable(selected = on, role = Role.RadioButton) { onIntent(TransactionEditorIntent.KindChosen(kind)) }
                        .testTag("txEditor.kind.${kind.wire}"),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(36.dp)
                            .background(if (on) container else AppTheme.colors.sunken, AppTheme.shapes.pill)
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            stringResource(kind.labelRes()),
                            style = AppTheme.type.caption.copy(fontSize = 13.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
                            color = if (on) tint else AppTheme.colors.ink2,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        return
    }
    AppTextField(
        value = stringResource(state.kind.labelRes()),
        onValueChange = {},
        label = stringResource(Res.string.filter_kind),
        modifier = Modifier.testTag("txEditor.kind"),
        enabled = false,
        supportingText = stringResource(Res.string.tx_kind_locked_note),
        errorText = state.errors[TransactionField.Kind]?.text(TransactionField.Kind),
    )
}

@Composable
private fun CategoryOrDestination(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    if (state.kind == TransactionKind.Transfer) {
        AppSelectField(
            label = stringResource(Res.string.tx_field_destination),
            value = state.destinationName.orEmpty(),
            onClick = { onIntent(TransactionEditorIntent.DestinationClicked) },
            modifier = Modifier.testTag("txEditor.destination"),
            supportingText = if (state.isCreate) null else stringResource(Res.string.tx_wallets_locked_note),
            errorText = state.errors[TransactionField.Destination]?.text(TransactionField.Destination),
            enabled = state.isCreate && state.canEdit && state.destinationChoices.isNotEmpty(),
        )
        if (state.destinationsEmpty) {
            Text(
                stringResource(Res.string.tx_destinations_empty, state.currency),
                style = AppTheme.type.body,
                color = AppTheme.colors.ink2,
                modifier = Modifier.testTag("txEditor.destinationsEmpty"),
            )
        }
        return
    }
    if (state.kind == TransactionKind.Unknown) return
    Text(stringResource(Res.string.tx_field_category), style = AppTheme.type.label, color = AppTheme.colors.ink2, modifier = Modifier.padding(top = 8.dp))
    val choices = state.categoryChoices
    val options = (listOfNotNull(state.category) + listOfNotNull(choices?.current) + (choices?.own.orEmpty()) + (choices?.defaults.orEmpty()))
        .distinctBy { it.id }
        .let { all -> all.sortedByDescending { it.id == state.categoryId } }
        .take(7)
    val tiles = options.map<CategoryDisplay, @Composable (Modifier) -> Unit> { option ->
        { modifier ->
            CategoryTileButton(
                label = option.label(),
                icon = CategoryIcons.of(option.iconKey),
                selected = option.id == state.categoryId,
                enabled = state.canEdit,
                onClick = { onIntent(TransactionEditorIntent.CategoryChosen(option.id)) },
                modifier = modifier.testTag("txEditor.categoryOption.${option.id}"),
            )
        }
    } + listOf<@Composable (Modifier) -> Unit>({ modifier ->
        CategoryTileButton(
            label = stringResource(Res.string.txle_all_categories),
            icon = AppIcons.More,
            selected = false,
            enabled = state.canEdit,
            onClick = { onIntent(TransactionEditorIntent.CategoryClicked) },
            modifier = modifier.testTag("txEditor.category"),
        )
    })
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
        tiles.chunked(4).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                rowTiles.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                repeat(4 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (state.categoriesEmpty) {
        Text(stringResource(Res.string.category_choice_empty), style = AppTheme.type.body, color = AppTheme.colors.ink2)
    }
    state.errors[TransactionField.Category]?.let {
        Text(it.text(TransactionField.Category), style = AppTheme.type.caption, color = AppTheme.colors.error)
    }
}

@Composable
private fun CategoryTileButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) AppTheme.colors.primaryContainer else AppTheme.colors.sunken, shape)
            .border(2.dp, if (selected) AppTheme.colors.primary else Color.Transparent, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) AppTheme.colors.primary else AppTheme.colors.ink2, modifier = Modifier.size(24.dp))
        Text(
            label,
            style = AppTheme.type.caption.copy(fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = if (selected) AppTheme.colors.primary else AppTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoreDetails(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    if (!state.moreOpen) return
    AppTextField(
        value = state.details,
        onValueChange = { onIntent(TransactionEditorIntent.DetailsChanged(it)) },
        label = stringResource(Res.string.tx_field_details),
        modifier = Modifier.testTag("txEditor.details"),
        singleLine = false,
        minLines = 3,
        errorText = state.errors[TransactionField.Details]?.text(TransactionField.Details),
        enabled = state.canEdit,
    )
    AppTextField(
        value = state.person,
        onValueChange = { onIntent(TransactionEditorIntent.PersonChanged(it)) },
        label = stringResource(Res.string.tx_field_person),
        modifier = Modifier.testTag("txEditor.person"),
        errorText = state.errors[TransactionField.Person]?.text(TransactionField.Person),
        enabled = state.canEdit,
    )
    SettingsSwitchRow(
        title = stringResource(Res.string.tx_field_cleared),
        checked = state.cleared,
        onCheckedChange = { if (state.canEdit) onIntent(TransactionEditorIntent.ClearedToggled(it)) },
        modifier = Modifier.testTag("txEditor.cleared"),
        supportingText = stringResource(Res.string.tx_cleared_help),
    )
}

/** "Confirm … instead": a received occurrence this entry may duplicate, so one payment is not recorded twice (spec 010 FR-031). */
@Composable
private fun MatchOffers(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    if (!state.isCreate || state.matches.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        InlineAlert(AlertTone.Info, stringResource(Res.string.recurring_match_title), Modifier.testTag("txEditor.matches"))
        state.matches.forEachIndexed { index, item ->
            val title = item.description?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.recurring_rule_title)
            SecondaryButton(
                stringResource(Res.string.recurring_match_offer, title, formatMoney(item.amountMinor, item.currency)),
                { onIntent(TransactionEditorIntent.MatchClicked(item.id)) },
                modifier = Modifier.testTag("txEditor.match.$index"),
                enabled = state.canSave,
            )
        }
    }
}

@Composable
private fun Captions(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    when (val by = state.addedBy) {
        is AddedBy.Member -> Caption(stringResource(Res.string.tx_added_by, by.name), "txEditor.addedBy")
        AddedBy.AnotherMember -> Caption(stringResource(Res.string.tx_added_by_other), "txEditor.addedBy")
        AddedBy.FormerMember -> Caption(stringResource(Res.string.tx_added_by_former), "txEditor.addedBy")
        AddedBy.You, null -> Unit
    }
    if (state.fromRecurringRule) Caption(stringResource(Res.string.tx_from_recurring), "txEditor.recurring")
    if (state.canOpenRule) {
        AppTextButton(
            stringResource(Res.string.recurring_open_rule),
            { onIntent(TransactionEditorIntent.OpenRuleClicked) },
            Modifier.testTag("txEditor.openRule"),
        )
    }
}

@Composable
private fun Caption(text: String, tag: String) {
    Text(text, style = AppTheme.type.caption, color = AppTheme.colors.caption, modifier = Modifier.testTag(tag))
}

@Composable
private fun StatusArea(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    when (val sync = state.sync) {
        EditorSync.None -> Unit
        EditorSync.Waiting -> InlineAlert(AlertTone.Info, stringResource(Res.string.wallet_status_waiting), Modifier.testTag("txEditor.status"))
        is EditorSync.Clash -> ClashCard(sync, state, onIntent)
        is EditorSync.Refused -> RefusedCard(sync, state, onIntent)
    }
}

@Composable
private fun RefusedCard(refused: EditorSync.Refused, state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        InlineAlert(AlertTone.Error, refused.reason.text(state.currency), Modifier.testTag("txEditor.status"))
        if (refused.reason.canRetryUnchanged) {
            PrimaryButton(
                stringResource(Res.string.action_retry), { onIntent(TransactionEditorIntent.TryAgain) },
                Modifier.testTag("txEditor.tryAgain"), enabled = !state.busy,
            )
        }
        SecondaryButton(
            stringResource(Res.string.action_discard), { onIntent(TransactionEditorIntent.Discard) },
            Modifier.testTag("txEditor.discard"), enabled = !state.busy,
        )
    }
}

/** Both versions side by side, and the person chooses; nothing is merged (FR-023, FR-024). */
@Composable
private fun ClashCard(clash: EditorSync.Clash, state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m),
    ) {
        if (clash.deletedElsewhere) {
            InlineAlert(AlertTone.Warning, stringResource(Res.string.tx_status_deleted), Modifier.testTag("txEditor.status"))
            if (clash.isDelete) {
                // Both sides deleted it: nothing to restore, only the person's own deletion to settle.
                PrimaryButton(
                    stringResource(Res.string.action_accept_deletion), { onIntent(TransactionEditorIntent.AcceptDeletion) },
                    Modifier.testTag("txEditor.acceptDeletion"), loading = state.busy,
                )
                return@Column
            }
            Text(stringResource(Res.string.tx_deleted_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
            PrimaryButton(
                stringResource(Res.string.action_restore_with_mine), { onIntent(TransactionEditorIntent.RestoreWithMine) },
                Modifier.testTag("txEditor.restoreWithMine"), loading = state.busy,
            )
            SecondaryButton(
                stringResource(Res.string.action_accept_deletion), { onIntent(TransactionEditorIntent.AcceptDeletion) },
                Modifier.testTag("txEditor.acceptDeletion"), enabled = !state.busy,
            )
            return@Column
        }
        InlineAlert(AlertTone.Warning, stringResource(Res.string.tx_status_clash), Modifier.testTag("txEditor.status"))
        Text(
            stringResource(if (clash.isDelete) Res.string.tx_clash_delete_body else Res.string.tx_clash_body),
            style = AppTheme.type.body,
            color = AppTheme.colors.ink2,
        )
        clash.lines.forEach { line -> ClashLineView(line) }
        if (clash.isDelete) {
            PrimaryButton(
                stringResource(Res.string.action_delete_anyway), { onIntent(TransactionEditorIntent.DeleteAnyway) },
                Modifier.testTag("txEditor.deleteAnyway"), loading = state.busy,
            )
            SecondaryButton(
                stringResource(Res.string.action_keep_transaction), { onIntent(TransactionEditorIntent.KeepTransaction) },
                Modifier.testTag("txEditor.keepTransaction"), enabled = !state.busy,
            )
        } else {
            PrimaryButton(
                stringResource(Res.string.category_keep_mine), { onIntent(TransactionEditorIntent.KeepMine) },
                Modifier.testTag("txEditor.keepMine"), loading = state.busy,
            )
            SecondaryButton(
                stringResource(Res.string.category_use_theirs), { onIntent(TransactionEditorIntent.UseTheirs) },
                Modifier.testTag("txEditor.useTheirs"), enabled = !state.busy,
            )
        }
    }
}

@Composable
private fun ClashLineView(line: ClashLine) {
    val label = when (line.field) {
        ClashField.Amount -> Res.string.tx_field_amount
        ClashField.Date -> Res.string.tx_field_date
        ClashField.Category -> Res.string.tx_field_category
        ClashField.Note -> Res.string.tx_field_note
        ClashField.Details -> Res.string.tx_field_details
        ClashField.Person -> Res.string.tx_field_person
        ClashField.Cleared -> Res.string.tx_field_cleared
    }
    Text(stringResource(label), style = AppTheme.type.label, color = AppTheme.colors.ink)
    Text(stringResource(Res.string.tx_this_phone, line.mine.text()), style = AppTheme.type.body, color = AppTheme.colors.ink2)
    Text(stringResource(Res.string.tx_other_device, line.theirs.text()), style = AppTheme.type.body, color = AppTheme.colors.ink2)
}

@Composable
private fun ClashValue.text(): String = when (this) {
    is ClashValue.Text -> value?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.category_none)
    is ClashValue.Money -> formatMoney(minor, currency)
    is ClashValue.Day -> formatDate(date)
    is ClashValue.Category -> display?.label() ?: stringResource(Res.string.category_none)
    is ClashValue.Cleared -> stringResource(if (cleared) Res.string.tx_cleared_yes else Res.string.tx_cleared_no)
}

@Composable
private fun Dialogs(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    if (state.datePickerOpen) {
        AppDatePickerDialog(
            initial = state.date ?: LocalDate(2026, 1, 1),
            onPick = { onIntent(TransactionEditorIntent.DateChosen(it)) },
            onDismiss = { onIntent(TransactionEditorIntent.DatePickerDismissed) },
        )
    }
    if (state.categorySheetOpen) {
        val choices = state.categoryChoices ?: CategoryChoices(null, emptyList(), emptyList())
        val current = stringResource(Res.string.category_choice_current)
        val own = stringResource(Res.string.category_choice_own)
        val defaults = stringResource(Res.string.category_choice_defaults)
        val unavailable = stringResource(Res.string.category_unavailable)
        fun CategoryDisplay.item() = CategoryChoiceItem(id, name.ifEmpty { unavailable }, iconKey, colorHex)
        CategoryChoiceSheet(
            title = stringResource(Res.string.category_choice_title),
            groups = listOf(
                CategoryChoiceGroup(current, listOfNotNull(choices.current?.item()), "categoryChoice.current", indexed = false),
                CategoryChoiceGroup(own, choices.own.map { it.item() }, "categoryChoice.own"),
                CategoryChoiceGroup(defaults, choices.defaults.map { it.item() }, "categoryChoice.default"),
            ),
            selectedId = state.categoryId,
            onSelect = { onIntent(TransactionEditorIntent.CategoryChosen(it)) },
            onDismiss = { onIntent(TransactionEditorIntent.SheetDismissed) },
            emptyMessage = stringResource(Res.string.category_choice_empty),
        )
    }
    if (state.destinationSheetOpen) {
        SelectionSheet(
            title = stringResource(Res.string.tx_choose_destination),
            options = state.destinationChoices,
            label = { it.name },
            selected = state.destinationChoices.firstOrNull { it.id == state.destinationId },
            onSelect = { onIntent(TransactionEditorIntent.DestinationChosen(it.id)) },
            onDismiss = { onIntent(TransactionEditorIntent.SheetDismissed) },
            searchable = false,
        )
    }
    if (state.walletSheetOpen) {
        SelectionSheet(
            title = stringResource(Res.string.tx_choose_destination),
            options = state.walletChoices,
            label = { it.name },
            selected = state.walletChoices.firstOrNull { it.id == state.walletId },
            onSelect = { onIntent(TransactionEditorIntent.WalletChosen(it.id)) },
            onDismiss = { onIntent(TransactionEditorIntent.WalletSheetDismissed) },
            searchable = false,
        )
    }
    if (state.confirmDelete) {
        val date = state.shownDate ?: state.date
        AppAlertDialog(
            title = stringResource(Res.string.tx_delete_title),
            text = stringResource(
                Res.string.tx_delete_body,
                formatMoney(state.shownAmountMinor, state.currency),
                date?.let { formatDate(it) }.orEmpty(),
            ),
            confirmLabel = stringResource(Res.string.tx_delete),
            onConfirm = { onIntent(TransactionEditorIntent.DeleteConfirmed) },
            dismissLabel = stringResource(Res.string.action_cancel),
            onDismiss = { onIntent(TransactionEditorIntent.DeleteCancelled) },
            destructive = true,
            confirmLoading = state.busy,
        )
    }
}

@Preview
@Composable
private fun TransactionEditorScreenPreview() {
    AppTheme {
        TransactionEditorScreen(
            TransactionEditorState(
                loaded = true, walletId = "w1", currency = "EGP", amountText = "45.50", date = LocalDate(2026, 10, 2),
                walletChoices = listOf(WalletChoice("w1", "Cash", "EGP")),
            ),
            {},
        )
    }
}
