package com.tjdoolittle.mileagelog.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tjdoolittle.mileagelog.R

/** Highway guide-sign palette: sign green, reflective amber, concrete neutrals. */
@Immutable
data class Palette(
    val bg: Color, val surface: Color, val ink: Color, val muted: Color, val line: Color,
    val sign: Color, val signInk: Color, val signSoft: Color, val signText: Color,
    val amber: Color, val amberBg: Color, val danger: Color,
    val odoBg: Color, val odoFg: Color,
)

val LightPalette = Palette(
    bg = Color(0xFFECEFEB), surface = Color(0xFFFFFFFF), ink = Color(0xFF18211D), muted = Color(0xFF5B6862), line = Color(0xFFD3D9D4),
    sign = Color(0xFF0A6A4A), signInk = Color.White, signSoft = Color(0xFFCFE7DC), signText = Color(0xFF0A6A4A),
    amber = Color(0xFFC98A07), amberBg = Color(0xFFFFF3D6), danger = Color(0xFFB3261E),
    odoBg = Color(0xFF111513), odoFg = Color(0xFFF3F3EE),
)
val DarkPalette = Palette(
    bg = Color(0xFF0E1311), surface = Color(0xFF172019), ink = Color(0xFFE5ECE8), muted = Color(0xFF94A39B), line = Color(0xFF29342F),
    sign = Color(0xFF0D7653), signInk = Color.White, signSoft = Color(0xFFB9DCCC), signText = Color(0xFF5FC59A),
    amber = Color(0xFFF0B43C), amberBg = Color(0xFF2E2611), danger = Color(0xFFF08A82),
    odoBg = Color(0xFF050706), odoFg = Color(0xFFF3F3EE),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

@OptIn(ExperimentalTextApi::class)
object Fonts {
    /** Overpass is modeled on Highway Gothic, the lettering on US road signs. */
    val sign = FontFamily(
        Font(R.font.overpass, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        Font(R.font.overpass, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    )
    val mono = FontFamily(
        Font(R.font.overpass_mono, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    )
    val body = FontFamily(
        Font(R.font.atkinson_regular, weight = FontWeight.Normal),
        Font(R.font.atkinson_bold, weight = FontWeight.Bold),
    )
}

object Type {
    val label = TextStyle(fontFamily = Fonts.sign, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.3.sp)
    val signNum = TextStyle(fontFamily = Fonts.sign, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
    val title = TextStyle(fontFamily = Fonts.sign, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
    val body = TextStyle(fontFamily = Fonts.body, fontSize = 16.sp, lineHeight = 22.sp)
    val bodyBold = body.copy(fontWeight = FontWeight.Bold)
    val small = TextStyle(fontFamily = Fonts.body, fontSize = 14.sp, lineHeight = 19.sp)
    val tiny = TextStyle(fontFamily = Fonts.body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
}

@Composable
fun MileageTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = p.sign, onPrimary = p.signInk, background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink, surfaceVariant = p.surface, onSurfaceVariant = p.muted,
        outline = p.line, error = p.danger, secondary = p.amber,
    ) else lightColorScheme(
        primary = p.sign, onPrimary = p.signInk, background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink, surfaceVariant = p.surface, onSurfaceVariant = p.muted,
        outline = p.line, error = p.danger, secondary = p.amber,
    )
    val base = Typography()
    val typography = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = Fonts.body),
        bodyMedium = base.bodyMedium.copy(fontFamily = Fonts.body),
        bodySmall = base.bodySmall.copy(fontFamily = Fonts.body),
        labelLarge = base.labelLarge.copy(fontFamily = Fonts.sign, fontWeight = FontWeight.ExtraBold),
        titleLarge = base.titleLarge.copy(fontFamily = Fonts.sign, fontWeight = FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Fonts.sign, fontWeight = FontWeight.ExtraBold),
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
