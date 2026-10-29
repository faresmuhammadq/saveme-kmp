package com.fares.saveme.feature.account.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.CalmField
import com.fares.saveme.designsystem.component.CalmActionBar
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.calmButtonSemantics
import com.fares.saveme.designsystem.component.calmTapArea
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AccExpand
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.money.knownCurrencies
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.model.Profile
import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.account.domain.model.ProfileField
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.account.ui.text
import com.fares.saveme.feature.auth.ui.FieldProblem
import com.fares.saveme.feature.auth.ui.message
import com.fares.saveme.feature.auth.ui.text
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.account_email_unverified
import saveme.shared.generated.resources.account_email_verified
import saveme.shared.generated.resources.acct_budget_period
import saveme.shared.generated.resources.acct_conflict_other
import saveme.shared.generated.resources.acct_conflict_phone
import saveme.shared.generated.resources.acct_currency_locked
import saveme.shared.generated.resources.acct_day_value
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.profile_budget_day
import saveme.shared.generated.resources.profile_budget_day_from
import saveme.shared.generated.resources.profile_conflict_body
import saveme.shared.generated.resources.profile_conflict_title
import saveme.shared.generated.resources.profile_currency
import saveme.shared.generated.resources.profile_currency_locked
import saveme.shared.generated.resources.profile_display_name
import saveme.shared.generated.resources.profile_email
import saveme.shared.generated.resources.profile_empty_value
import saveme.shared.generated.resources.profile_keep_mine
import saveme.shared.generated.resources.profile_language
import saveme.shared.generated.resources.profile_language_ar
import saveme.shared.generated.resources.profile_language_en
import saveme.shared.generated.resources.profile_save
import saveme.shared.generated.resources.profile_timezone
import saveme.shared.generated.resources.profile_title
import saveme.shared.generated.resources.profile_use_theirs
import saveme.shared.generated.resources.profile_waiting_to_sync

@Composable
fun ProfileScreen(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val view = state.view
    val form = state.form
    CalmScreen(
        title = stringResource(Res.string.profile_title),
        onBack = { onIntent(ProfileIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        snackbarHostState = snackbar,
        bottomBar = {
            if (view != null && form != null) {
                CalmActionBar {
                    PrimaryButton(
                        stringResource(Res.string.profile_save),
                        { onIntent(ProfileIntent.Save) },
                        modifier = Modifier.testTag("profile.save"),
                        enabled = state.dirty,
                    )
                }
            }
        },
    ) {
        state.banner?.let {
            InlineAlert(AlertTone.Error, it.text(), actionLabel = stringResource(Res.string.action_retry), onAction = { onIntent(ProfileIntent.Retry) })
        }
        if (view == null || form == null) {
            repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
        } else {
            view.conflict?.let { theirs -> ConflictCard(view, theirs, state.resolving, onIntent) }
            if (state.waitingToSync) InlineAlert(AlertTone.Info, stringResource(Res.string.profile_waiting_to_sync))
            ProfileForm(state, view, form, onIntent)
        }
    }
    PickerSheet(state, onIntent)
}

@Composable
private fun ProfileForm(state: ProfileState, view: ProfileView, form: ProfileDraft, onIntent: (ProfileIntent) -> Unit) {
    val rejected = view.rejection
    CalmField(
        value = form.displayName.orEmpty(),
        onValueChange = { onIntent(ProfileIntent.DisplayNameChanged(it)) },
        label = stringResource(Res.string.profile_display_name),
        modifier = Modifier.testTag("profile.displayName"),
        errorText = state.displayNameError?.let { stringResource(it.message()) }
            ?: rejected[ProfileField.DisplayName]?.let { FieldProblem.Server(it).text() },
    )
    CalmCard(radius = 22.dp, padding = PaddingValues(horizontal = 16.dp)) {
        SettingRow(label = stringResource(Res.string.profile_language)) {
            listOf("ar", "en").forEach { code ->
                LanguagePill(
                    stringResource(if (code == "ar") Res.string.profile_language_ar else Res.string.profile_language_en),
                    selected = form.locale == code,
                    onClick = { onIntent(ProfileIntent.LanguageChanged(code)) },
                )
            }
        }
        CalmDivider()
        SettingRow(
            label = stringResource(Res.string.profile_timezone),
            value = form.timezone,
            onClick = { onIntent(ProfileIntent.OpenPicker(ProfilePicker.Timezone)) },
            modifier = Modifier.testTag("profile.timezone"),
            errorText = rejected[ProfileField.Timezone]?.let { FieldProblem.Server(it).text() },
        )
        CalmDivider()
        SettingRow(
            label = stringResource(Res.string.profile_currency),
            value = form.defaultCurrency,
            onClick = { onIntent(ProfileIntent.OpenPicker(ProfilePicker.Currency)) },
            modifier = Modifier.testTag("profile.currency"),
            enabled = !state.currencyLocked,
            supporting = if (state.currencyLocked) stringResource(Res.string.acct_currency_locked) else null,
            errorText = rejected[ProfileField.DefaultCurrency]?.let { FieldProblem.Server(it).text() },
        )
        CalmDivider()
        SettingRow(
            label = stringResource(Res.string.acct_budget_period),
            value = stringResource(Res.string.acct_day_value, form.budgetStartDay),
            onClick = { onIntent(ProfileIntent.OpenPicker(ProfilePicker.BudgetDay)) },
            modifier = Modifier.testTag("profile.budgetDay"),
            supporting = view.server.budgetStartDayFrom
                ?.takeIf { form.budgetStartDay == view.server.budgetStartDay }
                ?.let { stringResource(Res.string.profile_budget_day_from, formatDate(it)) },
            errorText = rejected[ProfileField.BudgetStartDay]?.let { FieldProblem.Server(it).text() },
        )
    }
}

/** A label with its hint on the start and the value (or [trailing] pills) on the end; a [value] with an [onClick] opens a picker. */
@Composable
private fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    supporting: String? = null,
    errorText: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null && enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(vertical = 8.dp)
                .heightIn(min = 48.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink)
                supporting?.let { Text(it, style = AppTheme.type.caption.copy(fontSize = 12.sp), color = AppTheme.colors.ink2) }
            }
            if (value != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(36.dp).background(AppTheme.colors.sunken, AppTheme.shapes.pill).padding(horizontal = 12.dp),
                ) {
                    Text(
                        value,
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = if (enabled) AppTheme.colors.ink else AppTheme.colors.ink2,
                    )
                    Icon(
                        if (enabled) AppIcons.AccExpand else AppIcons.Lock,
                        contentDescription = null,
                        tint = AppTheme.colors.ink2,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (value == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
        }
        errorText?.let { Text(it, style = AppTheme.type.caption, color = AppTheme.colors.error, modifier = Modifier.padding(bottom = 8.dp)) }
    }
}

@Composable
private fun LanguagePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Text(
            label,
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = if (selected) AppTheme.colors.onPrimary else AppTheme.colors.ink,
            modifier = Modifier
                .background(if (selected) AppTheme.colors.primary else AppTheme.colors.sunken, AppTheme.shapes.pill)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Both versions, and the person chooses; nothing is merged (FR-012). */
@Composable
private fun ConflictCard(view: ProfileView, theirs: Profile, resolving: Boolean, onIntent: (ProfileIntent) -> Unit) {
    CalmCard(
        container = AppTheme.money.pendingContainer,
        bordered = false,
        radius = 22.dp,
        padding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Sync, contentDescription = null, tint = AppTheme.money.pending, modifier = Modifier.size(22.dp))
            Text(
                stringResource(Res.string.profile_conflict_title),
                style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = AppTheme.colors.ink,
            )
        }
        Text(
            stringResource(Res.string.profile_conflict_body),
            style = AppTheme.type.caption.copy(fontSize = 13.sp),
            color = AppTheme.colors.ink2,
            modifier = Modifier.padding(top = 4.dp),
        )
        val empty = stringResource(Res.string.profile_empty_value)
        val fields = view.conflictingFields()
        val named = fields.size > 1
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp).height(IntrinsicSize.Min)) {
            ConflictChoice(
                header = stringResource(Res.string.acct_conflict_phone),
                values = fields.map { (field, mine, _) -> (if (named) field.label() + ": " else "") + (mine.shown(field) ?: empty) },
                action = stringResource(Res.string.profile_keep_mine),
                highlighted = true,
                enabled = !resolving,
                onClick = { onIntent(ProfileIntent.KeepMine) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            ConflictChoice(
                header = stringResource(Res.string.acct_conflict_other),
                values = fields.map { (field, _, other) -> (if (named) field.label() + ": " else "") + (other.shown(field) ?: empty) },
                action = stringResource(Res.string.profile_use_theirs),
                highlighted = false,
                enabled = !resolving,
                onClick = { onIntent(ProfileIntent.UseTheirs) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ConflictChoice(
    header: String,
    values: List<String>,
    action: String,
    highlighted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .background(AppTheme.colors.surface, shape)
            .border(1.5.dp, if (highlighted) AppTheme.colors.primary else AppTheme.colors.outlineVariant, shape)
            .then(if (enabled) Modifier.calmTapArea(onClick) else Modifier)
            .padding(12.dp),
    ) {
        Text(header, style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink2)
        values.forEach { Text(it, style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = AppTheme.colors.ink) }
        Text(
            action,
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = AppTheme.colors.ink2,
            modifier = if (enabled) Modifier.calmButtonSemantics(onClick) else Modifier,
        )
    }
}

@Composable
private fun ProfileField.label(): String = stringResource(
    when (this) {
        ProfileField.DisplayName -> Res.string.profile_display_name
        ProfileField.Locale -> Res.string.profile_language
        ProfileField.Timezone -> Res.string.profile_timezone
        ProfileField.DefaultCurrency -> Res.string.profile_currency
        ProfileField.BudgetStartDay -> Res.string.profile_budget_day
    },
)

@Composable
private fun String?.shown(field: ProfileField): String? = when {
    this == null -> null
    field == ProfileField.Locale -> stringResource(if (this == "ar") Res.string.profile_language_ar else Res.string.profile_language_en)
    else -> this
}

@Composable
private fun PickerSheet(state: ProfileState, onIntent: (ProfileIntent) -> Unit) {
    val form = state.form ?: return
    val close = { onIntent(ProfileIntent.ClosePicker) }
    when (state.picker) {
        ProfilePicker.Timezone -> SelectionSheet(
            title = stringResource(Res.string.profile_timezone),
            options = remember { TimeZone.availableZoneIds.sorted() },
            label = { it },
            selected = form.timezone,
            onSelect = { onIntent(ProfileIntent.Picked(it)) },
            onDismiss = close,
        )
        ProfilePicker.Currency -> SelectionSheet(
            title = stringResource(Res.string.profile_currency),
            options = knownCurrencies,
            label = { it },
            selected = form.defaultCurrency,
            onSelect = { onIntent(ProfileIntent.Picked(it)) },
            onDismiss = close,
        )
        ProfilePicker.BudgetDay -> SelectionSheet(
            title = stringResource(Res.string.profile_budget_day),
            options = (1..28).map { it.toString() },
            label = { it },
            selected = form.budgetStartDay.toString(),
            onSelect = { onIntent(ProfileIntent.Picked(it)) },
            onDismiss = close,
            searchable = false,
        )
        null -> Unit
    }
}

internal val previewProfile = Profile("Sara", "en", "Africa/Cairo", "EGP", false, 1, LocalDate(2026, 10, 1), 3)

@Preview
@Composable
private fun ProfileScreenPreview() {
    val view = ProfileView(previewProfile, pending = null, conflict = null, rejection = emptyMap(), refreshedAt = null)
    AppTheme { ProfileScreen(ProfileState("sara@example.com", true, view, view.effective), {}) }
}
