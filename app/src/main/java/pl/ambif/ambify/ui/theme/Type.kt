package pl.ambif.ambify.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
/** The theme's lettering for headings and buttons; body text stays in the default font, easy to read. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun headingFamily(font: ThemeFont): FontFamily = when (font) {
    ThemeFont.DEFAULT -> FontFamily.Default
    ThemeFont.ORBITRON -> FontFamily(
        androidx.compose.ui.text.font.Font(
            pl.ambif.ambify.R.font.orbitron, FontWeight.Medium,
            variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(600)),
        ),
    )
    ThemeFont.MONO -> FontFamily(androidx.compose.ui.text.font.Font(pl.ambif.ambify.R.font.share_tech_mono))
    ThemeFont.RUSSO -> FontFamily(androidx.compose.ui.text.font.Font(pl.ambif.ambify.R.font.russo_one))
}

/** Material's styles with the theme's lettering in titles and labels (buttons). */
fun typographyFor(font: ThemeFont): androidx.compose.material3.Typography {
    if (font == ThemeFont.DEFAULT) return Typography
    val heading = headingFamily(font)
    val base = Typography
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = heading), displayMedium = base.displayMedium.copy(fontFamily = heading),
        displaySmall = base.displaySmall.copy(fontFamily = heading), headlineLarge = base.headlineLarge.copy(fontFamily = heading),
        headlineMedium = base.headlineMedium.copy(fontFamily = heading), headlineSmall = base.headlineSmall.copy(fontFamily = heading),
        titleLarge = base.titleLarge.copy(fontFamily = heading), titleMedium = base.titleMedium.copy(fontFamily = heading),
        labelLarge = base.labelLarge.copy(fontFamily = heading),
    )
}

// -----------------------------------------------------------------------------
// Splash Screen Typography
// -----------------------------------------------------------------------------

val SplashTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Light,
    fontSize = 34.sp,
    letterSpacing = 0.5.sp
)

val SplashMottoStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    letterSpacing = 0.2.sp
)

val SplashPoweredByStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Light,
    fontSize = 12.sp,
    letterSpacing = 0.3.sp
)