package com.jaynak.app.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.Feedback

data class TabItem(val title: String, val symbol: String)

/**
 * مقابل TabView (iOS 26): شريط تبويب زجاجي عائم + «ملحق سفلي» فوقه.
 * التبويبات التي زُيرت تبقى مركّبة بحالتها (مثل TabView) لكن غير مرئية.
 */
@Composable
fun TabScaffold(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accessory: (@Composable () -> Unit)? = null,
    content: @Composable (Int) -> Unit,
) {
    val visited = remember { mutableStateListOf<Int>() }
    if (selected !in visited) visited.add(selected)

    Box(modifier.fillMaxSize()) {
        BrandBackground()
        for (i in visited.toList()) {
            val shown = i == selected
            Box(
                Modifier.fillMaxSize().then(
                    if (shown) Modifier else Modifier.layout { m, _ ->
                        m.measure(androidx.compose.ui.unit.Constraints.fixed(0, 0))
                        layout(0, 0) {}
                    }
                )
            ) { androidx.compose.runtime.key(i) { content(i) } }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (accessory != null) {
                Box(
                    Modifier.fillMaxWidth().height(52.dp)
                        .glassChip(),
                    contentAlignment = Alignment.CenterStart,
                ) { accessory() }
            }
            Row(
                Modifier.fillMaxWidth().height(64.dp)
                    .glassShape(CircleShape, 24.dp, null, 0.18f)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { i, t ->
                    val on = i == selected
                    val accent = MB.accent
                    Column(
                        Modifier.weight(1f).fillMaxSize()
                            .clip(CircleShape)
                            .background(if (on) accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                            .pressable(0.96f) { if (!on) { Feedback.selection(); onSelect(i) } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        SFIcon(t.symbol, size = 22.dp, tint = if (on) accent else MB.ink2)
                        Text(t.title, style = MBType.micro, color = if (on) accent else MB.ink2, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** مقابل fullScreenCover — طبقة كاملة تنزلق من الأسفل مع دعم زر الرجوع. */
@Composable
fun FullScreenCover(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = visible) { onDismiss() }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(380)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(200)),
    ) {
        Box(Modifier.fillMaxSize().background(MB.sunken)) { content() }
    }
}
