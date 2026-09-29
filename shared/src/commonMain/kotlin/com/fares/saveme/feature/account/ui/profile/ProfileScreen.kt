package com.fares.saveme.feature.account.ui.profile

import androidx.compose.foundation.background
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
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.money.knownCurrencies
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.model.Profile
import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.account.domain.model.ProfileField
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.account.ui.BackBar
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
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.profile_budget_day
import saveme.shared.generated.resources.profile_budget_day_from
import saveme.shared.generated.resources.profile_conflict_body
import saveme.shared.generated.resources.profile_conflict_mine
import saveme.shared.generated.resources.profile_conflict_theirs
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
    AppScreen(
        snackbarHostState = snackbar,
        topBar = { BackBar(stringResource(Res.string.profile_title)) { onIntent(ProfileIntent.Back) } },
    ) {
        state.banner?.let {
            InlineAlert(AlertTone.Error, it.text(), actionLabel = stringResource(Res.string.action_retry), onAction = { onIntent(ProfileIntent.Retry) })
        }
        Column {
            Text(stringResource(Res.string.profile_email), style = AppTheme.type.caption, color = AppTheme.colors.caption)
            Text(state.email, style = AppTheme.type.body, color = AppTheme.colors.ink)
            Text(
                stringResource(if (state.emailVerified) Res.string.account_email_verified else Res.string.account_email_unverified),
                style = AppTheme.type.caption,
                color = AppTheme.colors.caption,
            )
        }
        val view = state.view
        val form = state.form
        if (view == null || form == null) {
            repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
            return@AppScreen
        }
        view.conflict?.let { theirs -> ConflictCard(view, theirs, state.resolving, onIntent) }
        if (state.waitingToSync) InlineAlert(AlertTone.Info, stringResource(Res.string.profile_waiting_to_sync))
        ProfileForm(state, view, form, onIntent)
    }
    PickerSheet(state, onIntent)
}

@Composable
private fun ProfileForm(state: ProfileState, view: ProfileView, form: ProfileDraft, onIntent: (ProfileIntent) -> Unit) {
    val rejected = view.rejection
    AppTextField(
        value = form.displayName.orEmpty(),
        onValueChange = { onIntent(ProfileIntent.DisplayNameChanged(it)) },
        label = stringResource(Res.string.profile_display_name),
        modifier = Modifier.testTag("profile.displayName"),
        errorText = state.displayNameError?.let { stringResource(it.message()) }
            ?: rejected[ProfileField.DisplayName]?.let { FieldProblem.Server(it).text() },
    )
    Text(stringResource(Res.string.profile_language), style = AppTheme.type.label, color = AppTheme.colors.ink2)
    SegmentedControl(
        options = listOf("ar", "en"),
        label = { stringResource(if (it == "ar") Res.string.profile_language_ar else Res.string.profile_language_en) },
        selected = form.locale,
        onSelect = { onIntent(ProfileIntent.LanguageChanged(it)) },
    )
    AppSelectField(
        label = stringResource(Res.string.profile_timezone),
        modifier = Modifier.testTag("profile.timezone"),
        value = form.timezone,
        onClick = { onIntent(ProfileIntent.OpenPicker(ProfilePicker.Timezone)) },
        errorText = rejected[ProfileField.Timezone]?.let { FieldProblem.Server(it).text() },
    )
    AppSelectField(
        label = stringResource(Res.string.profile_currency),
        modifier = Modifier.testTag("profile.currency"),
        value = form.defaultCurrency,
        onClick = { onIntent(ProfileIntent.OpenPicker(ProfilePicker.Currency)) },
        enabled = !state.currencyLocked,
        supportingText = if (state.currencyLocked) stringResource(Res.string.profile_currency_locked) else null,
        errorText = rejected[ProfileField.DefaultCurrency]?.let { FieldProblem.Server(it).text() },
    )
    AppSelectField(
        label = stringResource(Res.string.profile_budget_day),
        modifier = Modifier.testTag("profile.budgetDay"),
        value = form.budgetStartDay.toString(),
        onClick = { onIntent(ProfileIntent.OpenPicker(ProfilePicker.BudgetDay)) },
        supportingText = view.server.budgetStartDayFrom
            ?.takeIf { form.budgetStartDay == view.server.budgetStartDay }
            ?.let { stringResource(Res.string.profile_budget_day_from, formatDate(it)) },
        errorText = rejected[ProfileField.BudgetStartDay]?.let { FieldProblem.Server(it).text() },
    )
    PrimaryButton(
        stringResource(Res.string.profile_save),
        { onIntent(ProfileIntent.Save) },
        modifier = Modifier.testTag("profile.save"),
        enabled = state.dirty,
    )
}

/** Both versions, and the person chooses; nothing is merged (FR-012). */
@Composable
private fun ConflictCard(view: ProfileView, theirs: Profile, resolving: Boolean, onIntent: (ProfileIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .padding(AppTheme.spacing.m),
    ) {
        InlineAlert(AlertTone.Warning, stringResource(Res.string.profile_conflict_title))
        Text(stringResource(Res.string.profile_conflict_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        val empty = stringResource(Res.string.profile_empty_value)
        view.conflictingFields().forEach { (field, mine, other) ->
            Text(field.label(), style = AppTheme.type.label, color = AppTheme.colors.ink)
            Text(stringResource(Res.string.profile_conflict_mine, mine.shown(field) ?: empty), style = AppTheme.type.body, color = AppTheme.colors.ink2)
            Text(stringResource(Res.string.profile_conflict_theirs, other.shown(field) ?: empty), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        }
        PrimaryButton(stringResource(Res.string.profile_keep_mine), { onIntent(ProfileIntent.KeepMine) }, loading = resolving)
        SecondaryButton(stringResource(Res.string.profile_use_theirs), { onIntent(ProfileIntent.UseTheirs) }, enabled = !resolving)
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
