package com.fares.saveme.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Inline 24dp stroke icons; tinted by `Icon`. Directional ones are mirrored with `autoMirror`. */
object AppIcons {
    val ArrowBack by lazy { icon("ArrowBack", "M19 12H5M11 5l-7 7 7 7", autoMirror = true) }
    val ChevronForward by lazy { icon("ChevronForward", "M9 5l7 7-7 7", autoMirror = true) }
    val Visibility by lazy { icon("Visibility", "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z") }
    val VisibilityOff by lazy { icon("VisibilityOff", "M3 3l18 18M10.6 5.1A10.6 10.6 0 0 1 12 5c6.4 0 10 7 10 7a17 17 0 0 1-3 3.9M6.6 6.6A17.4 17.4 0 0 0 2 12s3.6 7 10 7a9.7 9.7 0 0 0 5.4-1.6M9.9 9.9a3 3 0 0 0 4.2 4.2") }
    val Plus by lazy { icon("Plus", "M12 5v14M5 12h14") }
    val Check by lazy { icon("Check", "M5 12l5 5 9-10") }
    val Info by lazy { icon("Info", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 16v-5M12 8h.01") }
    val Warning by lazy { icon("Warning", "M12 3l10 18H2L12 3zM12 10v4M12 17.5h.01") }
    val Error by lazy { icon("Error", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 7v6M12 16.5h.01") }
    val CloudOff by lazy { icon("CloudOff", "M3 3l18 18M8 7.2A6 6 0 0 1 17.7 11 4 4 0 0 1 20 18M16 19H7a5 5 0 0 1-1.6-9.7") }
    val Sync by lazy { icon("Sync", "M20 7h-5M20 7V2M20 7a8 8 0 0 0-14.5 2M4 17h5M4 17v5M4 17a8 8 0 0 0 14.5-2") }
    val Home by lazy { icon("Home", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1v-9z") }
    val Activity by lazy { icon("Activity", "M4 6h16M4 12h16M4 18h10") }
    val Budgets by lazy { icon("Budgets", "M12 3a9 9 0 1 0 9 9h-9V3zM15 3.5A9 9 0 0 1 20.5 9H15V3.5z") }
    val More by lazy { icon("More", "M5 12h.01M12 12h.01M19 12h.01") }
    val Cash by lazy { icon("Cash", "M2 7h20v10H2zM12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM6 12h.01M18 12h.01") }
    val Bank by lazy { icon("Bank", "M3 10l9-6 9 6M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18") }
    val Savings by lazy { icon("Savings", "M5 11a7 6 0 0 1 12-3h2v3l2 1v3h-2.5a7 7 0 0 1-2.5 2.5V20h-3v-2h-3v2H7v-3A6 6 0 0 1 5 11zM9 7l1-3") }
    val Card by lazy { icon("Card", "M2 6h20v12H2zM2 10h20M6 15h4") }
    val Loan by lazy { icon("Loan", "M12 2v20M17 6H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6") }
    val Investment by lazy { icon("Investment", "M3 17l6-6 4 4 8-8M15 7h6v6") }
    val Person by lazy { icon("Person", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0") }
    val Devices by lazy { icon("Devices", "M4 5h12v9H4zM2 18h13M18 9h4v11h-4zM20 17h.01") }
    val Lock by lazy { icon("Lock", "M5 11h14v10H5zM8 11V7a4 4 0 0 1 8 0v4M12 15v2") }
    val Delete by lazy { icon("Delete", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3") }
    val Logout by lazy { icon("Logout", "M9 4H5v16h4M16 8l4 4-4 4M20 12H9", autoMirror = true) }
    val Language by lazy { icon("Language", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM2 12h20M12 2a15 15 0 0 1 0 20M12 2a15 15 0 0 0 0 20") }

    private fun icon(name: String, path: String, autoMirror: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror,
        ).addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
