package com.jaynak.app.design

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jaynak.app.core.PhotoApi

/** صورة من السيرفر برمزها (تُخزَّن مؤقتاً). تُرسم [fallback] حتى تصل الصورة. */
@Composable
fun RemoteImage(
    token: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit = {},
) {
    var bmp by remember(token) { mutableStateOf<Bitmap?>(token?.takeIf { it.isNotEmpty() }?.let { PhotoApi.cached(it) }) }
    LaunchedEffect(token) {
        if (!token.isNullOrEmpty() && bmp == null) bmp = PhotoApi.bitmap(token)
    }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, modifier, contentScale = contentScale) else fallback()
}

/** عدّاد رسائل غير مقروءة — دائرة حمراء صغيرة تُوضع داخل Box فوق الزر. */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(modifier.size(20.dp).background(MB.red, CircleShape), contentAlignment = Alignment.Center) {
        Text(if (count > 99) "99+" else "$count", style = MBType.num(11, FontWeight.Bold), color = Color.White)
    }
}

/** عرض الصورة بملء الشاشة — اضغط في أي مكان للإغلاق. */
@Composable
fun PhotoViewer(token: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickableNoRipple(onDismiss), contentAlignment = Alignment.Center) {
            RemoteImage(token, Modifier.fillMaxSize(), ContentScale.Fit)
        }
    }
}
