package com.jaynak.app.features.shared

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.jaynak.app.core.DocumentStore
import com.jaynak.app.core.PhotoApi
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassButton
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.ProminentButton
import com.jaynak.app.design.RemoteImage
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.features.driver.decodeOriented
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PhotoCapture(val camera: () -> Unit, val gallery: () -> Unit)

/** كاميرا + استوديو؛ تعيد الصورة مصغّرة JPEG (أطول ضلع ١٢٨٠) جاهزة للرفع. */
@Composable
fun rememberPhotoCapture(onBytes: (ByteArray) -> Unit): PhotoCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onBytes)

    suspend fun process(raw: ByteArray) {
        val bmp = withContext(Dispatchers.IO) { runCatching { decodeOriented(raw) }.getOrNull() }
        val jpg = bmp?.let { withContext(Dispatchers.IO) { DocumentStore.jpegData(it, 1280) } }
        if (jpg != null) latest.value(jpg)
    }

    var cameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { runCatching { f.readBytes() }.getOrNull() }
                if (bytes != null) process(bytes)
                f.delete()
            }
        } else f?.delete()
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (raw != null) process(raw)
        }
    }
    return remember {
        PhotoCapture(
            camera = {
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val f = File.createTempFile("shot", ".jpg", dir)
                cameraFile = f
                cameraLauncher.launch(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f))
            },
            gallery = { galleryLauncher.launch("image/*") },
        )
    }
}

/**
 * ورقة التقاط صورة: تصوير أو (اختيارياً) من الاستوديو، معاينة، ثم تأكيد.
 * [onConfirm] ترفع الصورة وتعيد نص الخطأ، أو null عند النجاح فتُغلق الورقة.
 */
@Composable
fun PhotoPickSheet(
    title: String,
    hint: String,
    confirmText: String,
    onConfirm: suspend (ByteArray) -> String?,
    onDismiss: () -> Unit,
    cameraOnly: Boolean = false,
    currentToken: String? = null,
) {
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cap = rememberPhotoCapture { b ->
        bytes = b; error = null
        scope.launch { bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(b, 0, b.size) } }
    }

    JSheet(onDismiss = { if (!busy) onDismiss() }) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.ink2,
                        modifier = Modifier.clickableNoRipple { if (!busy) onDismiss() })
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(title, style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 18.dp))
                }
                Text(hint, style = MBType.caption, color = MB.ink2, textAlign = TextAlign.Center)

                val shape = RoundedCornerShape(24.dp)
                Box(Modifier.fillMaxWidth().height(300.dp).clip(shape).background(MB.ink.copy(alpha = 0.07f)),
                    contentAlignment = Alignment.Center) {
                    val b = bmp
                    if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Crop)
                    else if (!currentToken.isNullOrEmpty())
                        RemoteImage(currentToken, Modifier.fillMaxWidth().height(300.dp))
                    else SFIcon("camera.fill", size = 42.dp, tint = MB.ink3)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProminentButton(if (bytes == null) "تصوير" else "إعادة التصوير", { cap.camera() },
                        Modifier.weight(1f), symbol = "camera.fill", enabled = !busy)
                    if (!cameraOnly)
                        GlassButton("من الاستوديو", { cap.gallery() }, Modifier.weight(1f), tint = MB.ink,
                            symbol = "photo", enabled = !busy)
                }

                error?.let { Text(it, style = MBType.caption, color = MB.red, textAlign = TextAlign.Center) }

                if (busy) CircularProgressIndicator(Modifier.size(26.dp), color = MB.green, strokeWidth = 2.5.dp)
                PrimaryCTA(confirmText, onClick = {
                    val b = bytes ?: return@PrimaryCTA
                    busy = true; error = null
                    scope.launch {
                        val err = runCatching { onConfirm(b) }.getOrElse { it.message ?: "تعذّر الإرسال" }
                        busy = false
                        if (err == null) onDismiss() else error = err
                    }
                }, enabled = bytes != null && !busy, symbol = "checkmark.circle.fill")
            }
        }
    }
}

/** تغيير صورة الحساب (زبون أو مندوب) — الطرف الآخر والإدارة يرونها. */
@Composable
fun AvatarEditSheet(role: String, name: String, onDismiss: () -> Unit) {
    PhotoPickSheet(
        title = "صورة الحساب",
        hint = "تظهر صورتك للطرف الآخر وللإدارة — اختر صورة واضحة لوجهك",
        confirmText = "حفظ الصورة",
        currentToken = com.jaynak.app.core.AvatarRegistry.get(name),
        onConfirm = { b ->
            try {
                val t = PhotoApi.upload("avatar", b, role = role)
                com.jaynak.app.core.AvatarRegistry.put(name, t)
                null
            } catch (e: Exception) { e.message ?: "تعذّر رفع الصورة" }
        },
        onDismiss = onDismiss,
    )
}

/** إثبات استلام/تسليم: يلزم تصوير الغرض، ثم تُرفع الصورة وبعدها ينفَّذ [onDone] (تقدّم حالة الطلب). */
@Composable
fun ProofSheet(remoteID: String?, stage: String, onDone: () -> Unit, onDismiss: () -> Unit) {
    val pickup = stage == "pickup"
    PhotoPickSheet(
        title = if (pickup) "تصوير الغرض عند الاستلام" else "تصوير الطلب عند التسليم",
        hint = if (pickup) "صوّر الغرض الذي استلمته من العميل قبل المتابعة"
        else "صوّر الطلب في مكان التسليم كإثبات قبل إنهاء الطلب",
        confirmText = if (pickup) "تأكيد الاستلام" else "تأكيد التسليم",
        cameraOnly = true,
        onConfirm = { b ->
            try {
                if (remoteID != null) PhotoApi.upload(stage, b, orderID = remoteID)
                onDone()
                null
            } catch (e: Exception) { e.message ?: "تعذّر رفع الصورة" }
        },
        onDismiss = onDismiss,
    )
}

/** صورتا الاستلام والتسليم للطلب (إن وُجدتا) — الضغط يفتحها كاملة. */
@Composable
fun ProofRow(remoteID: String?, modifier: Modifier = Modifier) {
    val x = com.jaynak.app.core.OrderExtras.of(remoteID) ?: return
    if (x.proofPickup.isEmpty() && x.proofDelivery.isEmpty()) return
    var viewing by remember { mutableStateOf<String?>(null) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("صورة الاستلام" to x.proofPickup, "صورة التسليم" to x.proofDelivery).forEach { (label, tok) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(16.dp))
                    .background(MB.ink.copy(alpha = 0.07f))
                    .clickableNoRipple { if (tok.isNotEmpty()) viewing = tok },
                    contentAlignment = Alignment.Center) {
                    if (tok.isNotEmpty()) RemoteImage(tok, Modifier.fillMaxWidth().height(130.dp))
                    else SFIcon("photo", size = 24.dp, tint = MB.ink3)
                }
                Text(label, style = MBType.micro, color = MB.ink2)
            }
        }
    }
    viewing?.let { com.jaynak.app.design.PhotoViewer(it) { viewing = null } }
}
