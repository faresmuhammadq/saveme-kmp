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
    val Calendar by lazy { icon("Calendar", "M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM4 10h16M8 3v4M16 3v4") }
    val Close by lazy { icon("Close", "M6 6l12 12M18 6L6 18") }
    val Plus by lazy { icon("Plus", "M12 5v14M5 12h14") }
    val Check by lazy { icon("Check", "M5 12l5 5 9-10") }
    val Minus by lazy { icon("Minus", "M6 12h12") }
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

    // Category icons (spec 007, research R-13). Named for what they draw, never for what they mean.
    val Cart by lazy { icon("Cart", "M3 4h2l2.4 11h10.2l2-8H6.2M9 20h.01M17 20h.01") }
    val Restaurant by lazy { icon("Restaurant", "M6 3v6a3 3 0 0 0 6 0V3M9 3v18M17 21V3c-2.5 1.5-3.5 4.5-3.5 8H17") }
    val Bus by lazy { icon("Bus", "M5 4h14v13H5zM5 11h14M8.5 20v-3M15.5 20v-3M8.5 14h.01M15.5 14h.01") }
    val Fuel by lazy { icon("Fuel", "M4 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16M3 21h12M4 10h10M14 8h2.5a1.5 1.5 0 0 1 1.5 1.5V16a1.5 1.5 0 0 0 3 0V8l-3-3") }
    val Bolt by lazy { icon("Bolt", "M13 2L4 14h7l-1 8 9-12h-7l1-8z") }
    val Phone by lazy { icon("Phone", "M8 2h8a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1zM11 18h2") }
    val Health by lazy { icon("Health", "M9 3h6v6h6v6h-6v6H9v-6H3V9h6V3z") }
    val School by lazy { icon("School", "M2 9l10-5 10 5-10 5L2 9zM6 11.5V16c0 1.5 2.7 3 6 3s6-1.5 6-3v-4.5M22 9v6") }
    val Shirt by lazy { icon("Shirt", "M8 3L3 6l2.5 4L8 9v12h8V9l2.5 1L21 6l-5-3a4 4 0 0 1-8 0z") }
    val Sofa by lazy { icon("Sofa", "M5 11V8a3 3 0 0 1 3-3h8a3 3 0 0 1 3 3v3M3 13a2 2 0 0 1 4 0v1h10v-1a2 2 0 0 1 4 0v5H3v-5zM6 18v2M18 18v2") }
    val Sparkle by lazy { icon("Sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3zM19 16l.7 2.3L22 19l-2.3.7L19 22l-.7-2.3L16 19l2.3-.7L19 16z") }
    val Ticket by lazy { icon("Ticket", "M3 7h18v3a2 2 0 0 0 0 4v3H3v-3a2 2 0 0 0 0-4V7zM14 7v10") }
    val Cycle by lazy { icon("Cycle", "M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3") }
    val Plane by lazy { icon("Plane", "M21 3L3 10.5l6.5 2.5L12 19.5 21 3zM9.5 13L21 3") }
    val Smile by lazy { icon("Smile", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM8 14s1.5 2 4 2 4-2 4-2M9 9.5h.01M15 9.5h.01") }
    val Gift by lazy { icon("Gift", "M3 8h18v4H3zM5 12v9h14v-9M12 8v13M12 8c-2 0-4-1-4-3a2 2 0 0 1 4 0v3zM12 8c2 0 4-1 4-3a2 2 0 0 0-4 0v3z") }
    val Heart by lazy { icon("Heart", "M12 21s-8-5.2-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 10c0 5.8-8 11-8 11z") }
    val Store by lazy { icon("Store", "M4 9l1.5-5h13L20 9M4 9a2.5 2.5 0 0 0 5 0 2.5 2.5 0 0 0 6 0 2.5 2.5 0 0 0 5 0M5 11.5V20h14v-8.5M10 20v-5h4v5") }
    val Laptop by lazy { icon("Laptop", "M5 5h14v10H5zM3 19h18") }
    val Undo by lazy { icon("Undo", "M9 14L4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3") }

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
