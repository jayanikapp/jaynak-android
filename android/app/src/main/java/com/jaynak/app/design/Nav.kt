package com.jaynak.app.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import com.jaynak.app.i18n.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppStore
import com.jaynak.app.core.newId

/** مقابل @Environment(AppStore.self). */
val LocalStore = staticCompositionLocalOf<AppStore> { error("AppStore غير موفَّر") }

/**
 * مقابل NavigationStack: مكدّس شاشات بالحالة. كل تبويب/ورقة له مكدّسه.
 * الدفع: nav.push { Screen() } — والرجوع: nav.pop() أو زر الرجوع في النظام.
 */
@Stable
class Nav {
    class Entry(val content: @Composable () -> Unit) { val id = newId() }

    val stack = mutableStateListOf<Entry>()
    fun push(content: @Composable () -> Unit) { stack.add(Entry(content)) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun popToRoot() { stack.clear() }
    val canPop: Boolean get() = stack.isNotEmpty()
}

val LocalNav = compositionLocalOf<Nav> { error("لا يوجد Nav") }

@Composable
fun NavStack(modifier: Modifier = Modifier, root: @Composable () -> Unit) {
    val nav = remember { Nav() }
    BackHandler(enabled = nav.canPop) { nav.pop() }
    CompositionLocalProvider(LocalNav provides nav) {
        AnimatedContent(
            targetState = nav.stack.lastOrNull(),
            modifier = modifier.fillMaxSize(),
            transitionSpec = {
                val push = (targetState != null && initialState == null) ||
                    (targetState != null && initialState != null &&
                        nav.stack.indexOf(targetState) > nav.stack.indexOf(initialState))
                // RTL: الدفع يدخل من اليسار ويخرج السابق إلى اليمين
                if (push) {
                    (slideInHorizontally(tween(320)) { -it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(320)) { -it } + fadeOut(tween(200)))
                }.using(SizeTransform(clip = false))
            },
            label = "nav",
        ) { entry ->
            if (entry == null) root() else entry.content()
        }
    }
}

/** شريط علوي بزر رجوع زجاجي وعنوان — مقابل navigationTitle(.inline) + زر الرجوع. */
@Composable
fun NavBar(
    title: String = "",
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
            .height(52.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
        } else if (onBack != null) {
            CircleGlassButton("chevron.backward", onBack)
        } else {
            Box(Modifier.size(38.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (title.isNotEmpty()) Text(title, style = MBType.headline, color = MB.ink)
        }
        if (trailing != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
        else Box(Modifier.size(38.dp))
    }
}

@Composable
fun CircleGlassButton(symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    Box(
        modifier.size(38.dp).pressable(onClick = onClick).glassShape(CircleShape, 18.dp, null, 0.10f),
        contentAlignment = Alignment.Center,
    ) { SFIcon(symbol, size = 15.dp, tint = if (tint == Color.Unspecified) MB.ink else tint) }
}

/** خلفية العلامة + محتوى يحترم الحواف. مقابل ZStack { BrandBackground; ... }. */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        BrandBackground(intensity = intensity)
        content()
    }
}

/** مقابل .sheet — ورقة سفلية بخلفية العلامة. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JSheet(
    onDismiss: () -> Unit,
    fullHeight: Boolean = true,
    gestures: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!gestures) {
        // نافذة كاملة بلا سحب: السحب بإصبع واحد يخص الخريطة فقط (لا تتنازع معه الورقة السفلية).
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Column(
                Modifier.fillMaxSize().background(MB.sunken)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.ime),
                content = content,
            )
        }
        return
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = fullHeight)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MB.sunken,
        contentColor = MB.ink,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            Modifier.fillMaxWidth().then(if (fullHeight) Modifier.fillMaxSize() else Modifier)
                .windowInsetsPadding(WindowInsets.ime),
            content = content,
        )
    }
}
