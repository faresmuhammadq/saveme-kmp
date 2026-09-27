package com.fares.saveme.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.money.MoneyFormatter
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.money_owed

/** Language used for number formatting; follows the layout direction like the theme does. */
@Composable
@ReadOnlyComposable
internal fun moneyLanguage(): String = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "ar" else "en"

/** Amounts always read left-to-right, even inside Arabic layouts (P-06.3). */
internal fun TextStyle.ltr(): TextStyle = copy(textDirection = TextDirection.Ltr)

@Composable
fun formatMoney(amountMinor: Long, currency: String, showPlus: Boolean = false): String =
    MoneyFormatter.format(amountMinor, currency, moneyLanguage(), showPlus)

/** Large amount that shrinks down to numeralL before it would ellipsize (board: numerals autoSize). */
@Composable
fun AmountDisplay(
    amountMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AppTheme.type.numeralHero,
    color: Color = AppTheme.colors.ink,
) {
    BasicText(
        text = formatMoney(amountMinor, currency),
        style = style.ltr().copy(color = color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = AppTheme.type.numeralL.fontSize,
            maxFontSize = style.fontSize,
            stepSize = 1.sp,
        ),
        modifier = modifier,
    )
}

enum class MoneyKind { Income, Expense, Liability, Balance }

/**
 * Income: positive colour with "+". Expense: neutral ink with "−", never red (P-03.5).
 * Liability: stored negative, shown as a positive amount "owed" (P-03.5). Balance: error colour
 * only when negative.
 */
@Composable
fun MoneyText(
    amountMinor: Long,
    currency: String,
    kind: MoneyKind,
    modifier: Modifier = Modifier,
    style: TextStyle = AppTheme.type.itemTitle.copy(fontWeight = AppTheme.type.numeralL.fontWeight),
) {
    val colors = AppTheme.colors
    when (kind) {
        MoneyKind.Income -> Text(
            formatMoney(kotlin.math.abs(amountMinor), currency, showPlus = true),
            style = style.ltr(), color = AppTheme.money.positive, modifier = modifier, maxLines = 1,
        )
        MoneyKind.Expense -> Text(
            formatMoney(-kotlin.math.abs(amountMinor), currency),
            style = style.ltr(), color = colors.ink, modifier = modifier, maxLines = 1,
        )
        MoneyKind.Liability -> Row(modifier, verticalAlignment = Alignment.Bottom) {
            Text(formatMoney(kotlin.math.abs(amountMinor), currency), style = style.ltr(), color = colors.ink, maxLines = 1)
            Text(" " + stringResource(Res.string.money_owed), style = AppTheme.type.label, color = colors.ink2, maxLines = 1)
        }
        MoneyKind.Balance -> Text(
            formatMoney(amountMinor, currency),
            style = style.ltr(),
            color = if (amountMinor < 0) colors.error else colors.ink,
            modifier = modifier,
            maxLines = 1,
        )
    }
}
