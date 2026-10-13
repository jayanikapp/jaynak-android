package com.jaynak.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** مقابل Theme.swift — ألوان الهوية من Assets.xcassets (فاتح/داكن). */
data class MBPalette(
    val dark: Boolean,
    val brandGreen: Color,
    val brandTeal: Color,
    val brandRed: Color,
    val brandGold: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val hairline: Color,
    val rimLight: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
)

private fun w(v: Float) = Color(v, v, v)

val LightPalette = MBPalette(
    dark = false,
    brandGreen = Color(0xFF0FBFA4),
    brandTeal = Color(0xFF1AA5C9),
    brandRed = Color(red = 0.933f, green = 0.180f, blue = 0.310f),
    brandGold = Color(red = 0.902f, green = 0.639f, blue = 0.180f),
    inkPrimary = w(0.08f),
    inkSecondary = w(0.36f),
    inkTertiary = w(0.52f),
    hairline = Color.Black.copy(alpha = 0.09f),
    rimLight = Color.White.copy(alpha = 0.60f),
    surfaceRaised = w(1f),
    surfaceSunken = w(0.945f),
)

val DarkPalette = MBPalette(
    dark = true,
    brandGreen = Color(0xFF4FE5CE),
    brandTeal = Color(0xFF62DFFC),
    brandRed = Color(red = 1.000f, green = 0.318f, blue = 0.435f),
    brandGold = Color(red = 1.000f, green = 0.784f, blue = 0.361f),
    inkPrimary = w(0.97f),
    inkSecondary = w(0.74f),
    inkTertiary = w(0.56f),
    hairline = Color.White.copy(alpha = 0.11f),
    rimLight = Color.White.copy(alpha = 0.14f),
    surfaceRaised = Color(red = 0.106f, green = 0.106f, blue = 0.118f),
    surfaceSunken = Color(red = 0.071f, green = 0.071f, blue = 0.082f),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** مقابل roleTone في البيئة. */
enum class RoleTone { customer, driver, admin }

val LocalRoleTone = compositionLocalOf { RoleTone.customer }

/** وصول مختصر للألوان: MB.green … */
object MB {
    val c: MBPalette @Composable get() = LocalPalette.current
    val green: Color @Composable get() = LocalPalette.current.brandGreen
    val teal: Color @Composable get() = LocalPalette.current.brandTeal
    val red: Color @Composable get() = LocalPalette.current.brandRed
    val gold: Color @Composable get() = LocalPalette.current.brandGold
    val ink: Color @Composable get() = LocalPalette.current.inkPrimary
    val ink2: Color @Composable get() = LocalPalette.current.inkSecondary
    val ink3: Color @Composable get() = LocalPalette.current.inkTertiary
    val hairline: Color @Composable get() = LocalPalette.current.hairline
    val rim: Color @Composable get() = LocalPalette.current.rimLight
    val raised: Color @Composable get() = LocalPalette.current.surfaceRaised
    val sunken: Color @Composable get() = LocalPalette.current.surfaceSunken
    val dark: Boolean @Composable get() = LocalPalette.current.dark

    val accent: Color
        @Composable get() = when (LocalRoleTone.current) {
            RoleTone.customer -> green
            RoleTone.driver -> red
            RoleTone.admin -> gold
        }

    val greenGradient: Brush
        @Composable get() = diag(listOf(green, teal))

    val redGradient: Brush
        @Composable get() = diag(listOf(red, mix(red, gold, 0.40f)))

    val goldGradient: Brush
        @Composable get() = Brush.verticalGradient(listOf(gold, mix(gold, red, 0.28f)))

    val toneGradient: Brush
        @Composable get() = when (LocalRoleTone.current) {
            RoleTone.customer -> greenGradient
            RoleTone.driver -> redGradient
            RoleTone.admin -> goldGradient
        }

    /** topTrailing → bottomLeading كما في SwiftUI. */
    fun diag(colors: List<Color>): Brush = Brush.linearGradient(
        colors,
        start = Offset(Float.POSITIVE_INFINITY, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY),
    )

    fun mix(a: Color, b: Color, t: Float): Color = lerp(a, b, t)
}

fun Color.soft(): Color = copy(alpha = 0.14f)

/** مقابل Elevation. */
enum class Elevation(val shadow: Float, val radius: Float) {
    flush(0f, 0f), inset(0.04f, 16f), card(0.14f, 24f), floating(0.30f, 32f)
}

/** مقابل Font.ar / Font.num. */
object MBType {
    private fun ar(size: Float, weight: FontWeight) =
        TextStyle(fontSize = size.sp, fontWeight = weight, textDirection = TextDirection.Content)

    fun ar(size: Int, weight: FontWeight = FontWeight.Normal) = ar(size.toFloat(), weight)

    fun num(size: Int, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
        fontSize = size.sp, fontWeight = weight, fontFamily = FontFamily.SansSerif,
        fontFeatureSettings = "tnum",
    )

    val hero = ar(46f, FontWeight.Black)
    val display = ar(34f, FontWeight.ExtraBold)
    val title = ar(27f, FontWeight.Bold)
    val section = ar(20f, FontWeight.Bold)
    val headline = ar(17f, FontWeight.Bold)
    val body = ar(15.5f, FontWeight.Normal)
    val callout = ar(15f, FontWeight.Medium)
    val caption = ar(13f, FontWeight.Normal)
    val micro = ar(11f, FontWeight.Medium)
}

object Metrics {
    val cardRadius = 26.dp
    val tileRadius = 20.dp
    val chipRadius = 14.dp
    val gutter = 18.dp
    val stack = 14.dp
    val rowGap = 2.dp
    val tabScrollInset = 150.dp
}

/** مقابل BrandBackground: تدرّج أساسي + إضاءة علوية + توهّج زاوية بلون الدور. */
@Composable
fun BrandBackground(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val dark = MB.dark
    val accent = MB.accent
    val base = if (dark) Brush.verticalGradient(
        0.00f to Color(0.094f, 0.094f, 0.106f),
        0.52f to Color(0.063f, 0.063f, 0.075f),
        1.00f to Color(0.039f, 0.039f, 0.047f),
    ) else Brush.verticalGradient(
        0.00f to w(0.995f),
        1.00f to w(0.957f),
    )
    BoxWithConstraints(modifier.fillMaxSize().background(base)) {
        val wd = maxWidth
        val ht = maxHeight
        // الإضاءة العلوية
        Glow(
            Modifier.offset(x = wd * 0.5f - wd * 0.8f, y = -wd * 0.16f - wd * 0.31f)
                .size(width = wd * 1.6f, height = wd * 0.62f),
            color = Color.White.copy(alpha = (if (dark) 0.055f else 0.85f) * intensity),
            blurRadius = wd * 0.22f,
        )
        // توهّج الزاوية السفلية
        Glow(
            Modifier.offset(x = wd * 0.08f - wd * 0.39f, y = ht * 1.02f - wd * 0.39f)
                .size(wd * 0.78f),
            color = accent.copy(alpha = (if (dark) 0.20f else 0.11f) * intensity),
            blurRadius = wd * 0.38f,
        )
    }
}

/** لفّ التطبيق: ألوان + تدرّج الخط + اتجاه RTL إجباري يضبطه MainActivity. */
@Composable
fun JaynakTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = p.brandGreen, secondary = p.brandTeal, error = p.brandRed,
        background = p.surfaceSunken, surface = p.surfaceRaised,
        onBackground = p.inkPrimary, onSurface = p.inkPrimary,
    ) else lightColorScheme(
        primary = p.brandGreen, secondary = p.brandTeal, error = p.brandRed,
        background = p.surfaceSunken, surface = p.surfaceRaised,
        onBackground = p.inkPrimary, onSurface = p.inkPrimary,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** مقابل .roleTone(_:) */
@Composable
fun RoleToneScope(tone: RoleTone, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRoleTone provides tone, content = content)
}

/** شكل ضبابي: blur حقيقي من أندرويد ١٢، وقبله تدرّج شعاعي يعطي نفس الأثر. */
@Composable
private fun Glow(modifier: Modifier, color: Color, blurRadius: androidx.compose.ui.unit.Dp) {
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        Box(modifier.blur(blurRadius).background(color, CircleShape))
    } else {
        Box(modifier.background(
            Brush.radialGradient(listOf(color, Color.Transparent)), CircleShape))
    }
}
