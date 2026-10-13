package com.jaynak.app.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppStore
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.core.OrderStatus
import java.util.Locale

// ───────── أنماط الضغط ─────────

/** مقابل PressableStyle: تصغير خفيف وتعتيم عند الضغط، بلا موجة Material. */
fun Modifier.pressable(
    scale: Float = 0.975f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) scale else 1f, MBAnim.snap(), label = "press")
    this
        .graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (pressed) 0.94f else 1f }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** مقابل HeroButtonStyle: كبسولة بتدرّج وحافة ضوء وظل ملوّن يخفّ عند الضغط. */
@Composable
fun HeroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MB.green,
    gradient: Brush = MB.greenGradient,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.975f else 1f, MBAnim.snap(), label = "hero")
    val shadowC = tint.copy(alpha = if (pressed) 0.28f else 0.45f)
    val shape = CircleShape
    Row(
        modifier
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .shadow(if (pressed) 4.dp else 9.dp, shape, ambientColor = shadowC, spotColor = shadowC)
            .clip(shape)
            .background(gradient)
            .border(1.dp, MB.rim, shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// ───────── بطاقات وصفوف ─────────

/** مقابل GlassCard — المحتوى يحاذى للـtrailing كما في iOS. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = Metrics.gutter,
    radius: Dp = Metrics.cardRadius,
    tint: Color? = null,
    elevation: Elevation = Elevation.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (elevation == Elevation.flush) {
        Column(
            modifier.fillMaxWidth().padding(horizontal = padding, vertical = padding * 0.65f),
            horizontalAlignment = Alignment.End, content = content,
        )
    } else {
        Column(
            modifier.fillMaxWidth()
                .glassSurface(radius, tint, elevation.shadow)
                .padding(padding),
            horizontalAlignment = Alignment.End, content = content,
        )
    }
}

@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    showsDivider: Boolean = true,
    inset: Dp = Metrics.gutter,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Column(
            modifier.fillMaxWidth().padding(horizontal = inset, vertical = 13.dp),
            horizontalAlignment = Alignment.End, content = content,
        )
        if (showsDivider) {
            // padding(.leading) → بداية الاتجاه (يمين في RTL)
            Box(Modifier.padding(start = inset).fillMaxWidth().height(0.7.dp).background(MB.hairline))
        }
    }
}

@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    radius: Dp = Metrics.cardRadius,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().glassSurface(radius, null, Elevation.card.shadow),
        content = content,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    symbol: String? = null,
    actionTitle: String = "الكل",
    action: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (symbol != null) {
            SFIcon(symbol, size = 15.dp, tint = MB.green, modifier = Modifier.padding(top = 4.dp))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MBType.section, color = MB.ink)
            if (subtitle != null) Text(subtitle, style = MBType.caption, color = MB.ink2)
        }
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(actionTitle, style = MBType.callout, color = MB.green,
                modifier = Modifier.clickable(onClick = action))
        }
    }
}

@Composable
fun StatTile(
    title: String,
    value: String,
    symbol: String,
    modifier: Modifier = Modifier,
    tint: Color = MB.green,
    delta: String? = null,
    deltaUp: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth()
            .glassSurface(Metrics.tileRadius, null, Elevation.card.shadow)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MBType.caption, color = MB.ink2, maxLines = 1)
            Text(value, style = MBType.num(24, FontWeight.ExtraBold), color = MB.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (delta != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SFIcon(if (deltaUp) "arrow.up.right" else "arrow.down.right", size = 11.dp,
                        tint = if (deltaUp) MB.green else MB.red)
                    Text(delta, style = MBType.micro, color = if (deltaUp) MB.green else MB.red)
                }
            }
        }
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.soft()),
            contentAlignment = Alignment.Center,
        ) { SFIcon(symbol, size = 14.dp, tint = tint) }
    }
}

// ───────── شارات واختيارات ─────────

@Composable
fun MBChip(
    text: String,
    modifier: Modifier = Modifier,
    symbol: String? = null,
    tint: Color = MB.green,
    filled: Boolean = false,
) {
    val fg = if (filled) Color.White else tint
    Row(
        modifier
            .clip(CircleShape)
            .background(if (filled) tint else tint.copy(alpha = 0.14f))
            .border(0.8.dp, if (filled) MB.rim else tint.copy(alpha = 0.22f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (symbol != null) SFIcon(symbol, size = 11.dp, tint = fg)
        Text(text, style = MBType.micro, color = fg)
    }
}

@Composable
fun SelectChip(
    title: String,
    symbol: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color = MB.green,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .pressable(0.97f) { Feedback.selection(); onClick() }
            .glassSurface(20.dp, if (isSelected) tint else null, if (isSelected) 0.22f else 0.10f)
            .let { if (isSelected) it.background(tint, shape) else it }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            SFIcon(symbol, size = 20.dp, tint = if (isSelected) Color.White else tint)
        }
        Text(title, style = MBType.callout, color = if (isSelected) Color.White else MB.ink)
        if (subtitle != null) {
            Text(subtitle, style = MBType.micro, maxLines = 1,
                color = if (isSelected) Color.White.copy(alpha = 0.85f) else MB.ink3)
        }
    }
}

// ───────── أزرار الإجراء ─────────

@Composable
fun PrimaryCTA(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    symbol: String? = null,
    tint: Color = MB.green,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val gradient = when (tint) {
        MB.red -> MB.redGradient
        MB.gold -> MB.goldGradient
        else -> MB.greenGradient
    }
    HeroButton(
        onClick = {
            if (enabled && !loading) {
                Feedback.impact(ImpactStyle.medium)
                onClick()
            }
        },
        modifier = modifier.fillMaxWidth().height(54.dp).alpha(if (enabled) 1f else 0.40f).sheen(enabled && !loading),
        tint = tint, gradient = gradient, enabled = enabled && !loading,
    ) {
        if (loading) {
            BrandLoader(26.dp)
        } else if (symbol != null) {
            SFIcon(symbol, size = 16.dp, tint = Color.White)
        }
        if (loading || symbol != null) Spacer(Modifier.width(8.dp))
        Text(title, style = MBType.ar(17, FontWeight.SemiBold), color = Color.White)
    }
}

@Composable
fun SecondaryCTA(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    symbol: String? = null,
    tint: Color = Color.Unspecified,
) {
    val c = if (tint == Color.Unspecified) MB.ink else tint
    Row(
        modifier.fillMaxWidth().height(50.dp)
            .pressable { Feedback.impact(ImpactStyle.light); onClick() }
            .glassChip(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (symbol != null) {
            SFIcon(symbol, size = 15.dp, tint = c)
            Spacer(Modifier.width(7.dp))
        }
        Text(title, style = MBType.ar(16, FontWeight.Medium), color = c)
    }
}

// ───────── تقييم وتقدّم ─────────

@Composable
fun StarRating(
    value: Double,
    modifier: Modifier = Modifier,
    count: Int? = null,
    size: Dp = 13.dp,
    editable: Boolean = false,
    onChange: ((Int) -> Unit)? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (editable) 10.dp else 2.dp)) {
        for (i in 1..5) {
            val filled = i.toDouble() <= Math.round(value).toDouble()
            SFIcon(
                if (filled) "star.fill" else "star",
                size = size,
                tint = if (filled) MB.gold else MB.ink3,
                modifier = Modifier
                    .graphicsLayer { val s = if (editable && filled) 1.06f else 1f; scaleX = s; scaleY = s }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, enabled = editable,
                    ) {
                        Feedback.impact(ImpactStyle.light)
                        onChange?.invoke(i)
                    },
            )
        }
        if (!editable) {
            Text(
                if (value == 0.0) "جديد" else String.format(Locale.US, "%.1f", value),
                style = MBType.num(size.value.toInt(), FontWeight.SemiBold), color = MB.ink2,
            )
            if (count != null) Text("($count)", style = MBType.micro, color = MB.ink3)
        }
    }
}

@Composable
fun OrderProgressTrack(step: Int, modifier: Modifier = Modifier) {
    val labels = listOf("بحث", "تعيين", "استلام", "توصيل", "تسليم")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.indices.forEach { i ->
                Box(
                    Modifier.weight(1f).height(5.dp).clip(CircleShape)
                        .background(if (i <= step) MB.greenGradient else SolidColor(MB.ink.copy(alpha = 0.12f)))
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { i, l ->
                Text(l, Modifier.weight(1f), textAlign = TextAlign.Center, style = MBType.micro,
                    color = if (i <= step) MB.green else MB.ink3)
            }
        }
    }
}

// ───────── حقول ─────────

@Composable
fun MBField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    symbol: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    isRTLText: Boolean = true,
) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MBType.caption, color = MB.ink2)
        Row(
            Modifier.fillMaxWidth().height(50.dp)
                .glassSurface(16.dp, null, 0.08f)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (symbol != null) {
                Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                    SFIcon(symbol, size = 14.dp, tint = MB.ink3)
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                val style = (if (isRTLText) MBType.body else MBType.num(16, FontWeight.Medium))
                    .copy(color = MB.ink, textAlign = TextAlign.End)
                if (text.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = style.copy(color = MB.ink3), maxLines = 1)
                }
                BasicTextField(
                    value = text, onValueChange = onTextChange, singleLine = true,
                    textStyle = style, cursorBrush = SolidColor(MB.green),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun KeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    bold: Boolean = false,
    isNumber: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = MBType.callout, color = MB.ink2)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = if (isNumber) MBType.num(if (bold) 17 else 15, if (bold) FontWeight.Bold else FontWeight.SemiBold)
            else MBType.ar(if (bold) 17 else 15, if (bold) FontWeight.Bold else FontWeight.Medium),
            color = if (valueColor == Color.Unspecified) MB.ink else valueColor,
        )
    }
}

@Composable
fun EmptyStateView(symbol: String, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SFIcon(symbol, size = 34.dp, tint = MB.ink3)
        Text(title, style = MBType.headline, color = MB.ink)
        Text(message, style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center)
    }
}

@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = MB.green) {
    val initials = name.split(" ").filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().toString() }
    Box(
        modifier.size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.55f))))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = MBType.ar((size.value * 0.38f).toInt(), FontWeight.Bold), color = Color.White)
    }
}

@Composable
fun LoadingDots() = BrandLoader(28.dp)

@Composable
fun ToastBanner(toast: AppStore.Toast, modifier: Modifier = Modifier) {
    val tint = when (toast.kind) {
        AppStore.Toast.Kind.info, AppStore.Toast.Kind.success -> MB.green
        AppStore.Toast.Kind.error -> MB.red
    }
    val symbol = when (toast.kind) {
        AppStore.Toast.Kind.info -> "info.circle.fill"
        AppStore.Toast.Kind.success -> "checkmark.circle.fill"
        AppStore.Toast.Kind.error -> "exclamationmark.triangle.fill"
    }
    Row(
        modifier.padding(horizontal = 24.dp)
            .glassChip(tint.copy(alpha = 0.25f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        SFIcon(symbol, size = 15.dp, tint = tint)
        Text(toast.text, style = MBType.ar(14, FontWeight.Medium), color = MB.ink, maxLines = 2)
    }
}

@Composable
fun StatusBadge(status: OrderStatus, modifier: Modifier = Modifier) {
    val tint = when (status) {
        OrderStatus.searching -> Color(0xFFFF9500)
        OrderStatus.assigned, OrderStatus.pickingUp -> Color(0xFF007AFF)
        OrderStatus.pickedUp, OrderStatus.delivering, OrderStatus.delivered -> MB.green
        OrderStatus.cancelled -> MB.red
    }
    MBChip(
        text = status.title,
        symbol = when (status) {
            OrderStatus.delivered -> "checkmark.circle.fill"
            OrderStatus.cancelled -> "xmark.circle.fill"
            else -> "circle.fill"
        },
        tint = tint,
        filled = status.isLive && status != OrderStatus.searching,
        modifier = modifier,
    )
}

/** نقر بلا موجة — مقابل Button(.plain) / onTapGesture. */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null, onClick = onClick,
    )
}
