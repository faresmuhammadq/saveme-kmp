package com.fares.saveme.feature.transactions.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.designsystem.tokens.CategoryColors

/** One row of the calm day card: category tile, title over note or sync status, and the amount without its currency. */
@Composable
internal fun CalmTransactionRow(
    title: String,
    subtitle: String,
    amountText: String,
    amountColor: Color,
    iconKey: String?,
    colorHex: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
    waiting: Boolean = false,
    directionLabel: String? = null,
    amountModifier: Modifier = Modifier,
    statusModifier: Modifier = Modifier,
) {
    val swatch = CategoryColors.of(colorHex)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        CalmIconTile(CategoryIcons.of(iconKey), swatch.tint, swatch.container, size = 38.dp, iconSize = 20.dp, radius = 12.dp)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppTheme.type.itemTitle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                color = AppTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (status != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(if (waiting) AppIcons.Sync else AppIcons.Warning, contentDescription = null, tint = AppTheme.money.pending, modifier = Modifier.size(14.dp))
                    Text(
                        status,
                        style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = AppTheme.money.pending,
                        modifier = statusModifier,
                    )
                }
            } else {
                Text(subtitle, style = AppTheme.type.caption.copy(fontSize = 12.5.sp), color = AppTheme.colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            amountText,
            style = AppTheme.type.itemTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr),
            color = amountColor,
            maxLines = 1,
            modifier = amountModifier.then(
                if (directionLabel != null) Modifier.semantics { contentDescription = "$directionLabel $amountText" } else Modifier,
            ),
        )
    }
}
