package com.fares.saveme.designsystem.tokens

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.hanken_grotesk_bold
import saveme.shared.generated.resources.hanken_grotesk_extrabold
import saveme.shared.generated.resources.hanken_grotesk_medium
import saveme.shared.generated.resources.hanken_grotesk_semibold
import saveme.shared.generated.resources.ibm_plex_sans_arabic_bold
import saveme.shared.generated.resources.ibm_plex_sans_arabic_medium
import saveme.shared.generated.resources.ibm_plex_sans_arabic_semibold

@Composable
fun latinFontFamily(): FontFamily = FontFamily(
    Font(Res.font.hanken_grotesk_medium, FontWeight.Medium),
    Font(Res.font.hanken_grotesk_semibold, FontWeight.SemiBold),
    Font(Res.font.hanken_grotesk_bold, FontWeight.Bold),
    Font(Res.font.hanken_grotesk_extrabold, FontWeight.ExtraBold),
)

/** No 800 weight exists; requests for ExtraBold resolve to Bold (Arabic headings drop to 700). */
@Composable
fun arabicFontFamily(): FontFamily = FontFamily(
    Font(Res.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(Res.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(Res.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
    Font(Res.font.ibm_plex_sans_arabic_bold, FontWeight.ExtraBold),
)
