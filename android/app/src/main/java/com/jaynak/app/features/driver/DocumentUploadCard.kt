package com.jaynak.app.features.driver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.jaynak.app.core.DocumentAsset
import com.jaynak.app.core.DocumentKind
import com.jaynak.app.core.DocumentRules
import com.jaynak.app.core.DocumentState
import com.jaynak.app.core.DocumentStore
import com.jaynak.app.core.DocumentUploader
import com.jaynak.app.core.DriverDocuments
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.design.ActionDialog
import com.jaynak.app.design.DialogAction
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import com.jaynak.app.design.soft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

/** يفكّ الصورة ويطبّق اتجاه EXIF كما تفعل iOS تلقائياً. */
internal fun decodeOriented(bytes: ByteArray): Bitmap? {
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val m = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        else -> return raw
    }
    return runCatching { Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true) }.getOrDefault(raw)
}

/**
 * مقابل DocumentUploadCard.swift. حالة [docs] تعيش عند الأب؛ التعديلات تمرّ عبر [update]
 * (دالة تحويل) كي لا تضيع تحديثات الرفع المتزامنة.
 */
@Composable
fun DocumentUploadCard(
    kind: DocumentKind,
    docs: DriverDocuments,
    update: ((DriverDocuments) -> DriverDocuments) -> Unit,
    modifier: Modifier = Modifier,
    onChange: () -> Unit = {},
) {
    val store = LocalStore.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSource by remember { mutableStateOf(false) }
    var rejection by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val mine = docs.assetsOf(kind)
    val isFull = mine.size >= kind.maximumCount
    val satisfied = docs.has(kind)
    val accent = MB.accent

    fun upload(asset: DocumentAsset) {
        scope.launch {
            DocumentUploader.upload(asset, store.session?.driverID) { updated ->
                withContext(Dispatchers.Main) {
                    update { d -> d.copy(assets = d.assets.map { if (it.id == updated.id) updated else it }) }
                    onChange()
                }
            }
        }
    }

    fun add(data: ByteArray, mime: String) {
        try {
            val asset = DocumentStore.save(data, kind, mime)
            update { d -> d.copy(assets = d.assets + asset) }
            rejection = null
            Feedback.success()
            onChange()
            upload(asset)
        } catch (e: DocumentRules.Rejection) {
            rejection = e.message
            Feedback.warning()
        } catch (e: Exception) {
            rejection = DocumentRules.Rejection.Unreadable.message
        }
    }

    fun remove(asset: DocumentAsset) {
        DocumentStore.delete(asset)
        update { d -> d.copy(assets = d.assets.filter { it.id != asset.id }) }
        Feedback.impact(ImpactStyle.light)
        onChange()
    }

    fun ingestUris(uris: List<Uri>) {
        scope.launch {
            busy = true
            try {
                for (uri in uris.take(kind.maximumCount - mine.size)) {
                    val raw = withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    }
                    if (raw == null) { rejection = DocumentRules.Rejection.Unreadable.message; continue }
                    val mime = DocumentRules.sniff(raw)
                    if (mime == "application/pdf" || mime == "image/png") {
                        add(raw, mime)
                    } else {
                        val bmp = withContext(Dispatchers.IO) { decodeOriented(raw) }
                        val jpg = bmp?.let { withContext(Dispatchers.IO) { DocumentStore.jpegData(it) } }
                        if (jpg != null) add(jpg, "image/jpeg")
                        else rejection = DocumentRules.Rejection.UnsupportedType("").message
                    }
                }
            } finally { busy = false }
        }
    }

    // الكاميرا: ملف مؤقت عبر FileProvider لنحصل على الدقة الكاملة
    // مسار الملف يُحفظ مع حالة الشاشة: على الجوالات الضعيفة يُغلق التطبيق أثناء فتح الكاميرا فتضيع الصورة
    var cameraPath by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraPath?.let { File(it) }
        if (ok && f != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { runCatching { f.readBytes() }.getOrNull() }
                val bmp = bytes?.let { withContext(Dispatchers.IO) { decodeOriented(it) } }
                val jpg = bmp?.let { withContext(Dispatchers.IO) { DocumentStore.jpegData(it) } }
                if (jpg == null) rejection = DocumentRules.Rejection.Unreadable.message
                else add(jpg, "image/jpeg")
                f.delete()
            }
        } else f?.delete()
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File.createTempFile("shot", ".jpg", dir)
        cameraPath = f.absolutePath
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
        cameraLauncher.launch(uri)
    }
    val libraryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) ingestUris(uris)
    }
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) ingestUris(uris)
    }

    Column(
        modifier.fillMaxWidth()
            .glassSurface(20.dp, null, if (satisfied) 0.06f else 0.12f)
            .border(if (satisfied) 1.2.dp else 1.dp,
                if (satisfied) accent.copy(alpha = 0.45f) else MB.hairline, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End,
    ) {
        // الرأس
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(if (satisfied) accent.soft() else MB.ink.copy(alpha = 0.06f),
                RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                SFIcon(if (satisfied) "checkmark" else kind.symbol, size = 17.dp,
                    tint = if (satisfied) accent else MB.ink3)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(kind.title, style = MBType.callout, color = MB.ink)
                    if (!kind.isRequired) Text("اختياري", style = MBType.micro, color = MB.ink3)
                }
                val statusLine = run {
                    val uploaded = mine.count { it.state == DocumentState.uploaded }
                    when {
                        uploaded == mine.size -> if (mine.size > 1) "${mine.size} ملفات مرفوعة" else "مرفوع"
                        mine.any { it.state == DocumentState.uploading } -> "جاري الرفع…"
                        mine.any { it.state == DocumentState.failed } -> "فشل رفع ملف"
                        else -> "محفوظ على جهازك"
                    }
                }
                Text(if (satisfied) statusLine else kind.hint, style = MBType.micro,
                    color = if (satisfied) accent else MB.ink2, textAlign = TextAlign.End)
            }
        }

        if (mine.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                mine.forEach { asset ->
                    DocumentThumb(asset, onRetry = { upload(asset) }, onDelete = { remove(asset) })
                }
            }
        }

        rejection?.let { text ->
            Row(Modifier.fillMaxWidth().background(MB.red.copy(alpha = 0.10f), RoundedCornerShape(13.dp)).padding(11.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                SFIcon("exclamationmark.triangle.fill", size = 14.dp, tint = MB.red)
                Text(text, style = MBType.micro, color = MB.red, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Box(Modifier.pressable { rejection = null }) { SFIcon("xmark", size = 10.dp, tint = MB.ink3) }
            }
        }

        if (!isFull) {
            Row(Modifier.fillMaxWidth().alpha(if (busy) 0.6f else 1f), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    Modifier.weight(1f).clip(CircleShape).background(MB.toneGradient)
                        .pressable(enabled = !busy) { launchCamera() }.padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SFIcon("camera.fill", size = 13.dp, tint = Color.White)
                    Text("تصوير", style = MBType.caption, color = Color.White)
                }
                Row(
                    Modifier.weight(1f).clip(CircleShape).background(MB.ink.copy(alpha = 0.07f))
                        .pressable(enabled = !busy) { showSource = true }.padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SFIcon("ellipsis", size = 13.dp, tint = MB.ink)
                    Text("مصدر آخر", style = MBType.caption, color = MB.ink)
                }
            }
        }
    }

    if (showSource) {
        ActionDialog(
            title = kind.title,
            actions = listOf(
                DialogAction("التصوير بالكاميرا") { showSource = false; launchCamera() },
                DialogAction("اختيار من المعرض") { showSource = false; libraryLauncher.launch("image/*") },
                DialogAction("إرفاق ملف PDF") {
                    showSource = false
                    filesLauncher.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
                },
            ),
            cancelText = "إلغاء",
            onDismiss = { showSource = false },
        )
    }
}

@Composable
private fun DocumentThumb(asset: DocumentAsset, onRetry: () -> Unit, onDelete: () -> Unit) {
    var bmp by remember(asset.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(asset.id) { bmp = withContext(Dispatchers.IO) { DocumentStore.thumbnail(asset) } }
    val shape = RoundedCornerShape(14.dp)

    Box(Modifier.size(92.dp)) {
        Box(Modifier.fillMaxSize().background(MB.ink.copy(alpha = 0.07f), shape))
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Crop)
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
                SFIcon(if (asset.isImage) "photo" else "doc.richtext.fill", size = 20.dp, tint = MB.ink3)
                Text(asset.sizeLabel, style = MBType.num(9, FontWeight.Medium), color = MB.ink3)
            }
        }

        when (asset.state) {
            DocumentState.uploading -> Box(Modifier.fillMaxSize().clip(shape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            }
            DocumentState.failed -> Box(Modifier.fillMaxSize().clip(shape).background(Color.Black.copy(alpha = 0.52f))
                .pressable(onClick = onRetry), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SFIcon("arrow.clockwise", size = 15.dp, tint = Color.White)
                    Text("إعادة", style = MBType.micro, color = Color.White)
                }
            }
            DocumentState.uploaded -> Box(Modifier.align(Alignment.TopStart).padding(6.dp)
                .size(15.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                SFIcon("checkmark.circle.fill", size = 15.dp, tint = MB.green)
            }
            DocumentState.stored -> Box(Modifier.align(Alignment.TopStart).padding(5.dp)
                .size(20.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                SFIcon("phone", size = 11.dp, tint = Color.White)
            }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(5.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            .pressable(onClick = onDelete).padding(6.dp)) {
            SFIcon("trash.fill", size = 10.dp, tint = Color.White)
        }
    }
}

/** مقابل ReviewThumb — مصغّرة ٥٨×٥٨ في شاشة المراجعة. */
@Composable
fun ReviewThumb(asset: DocumentAsset) {
    var bmp by remember(asset.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(asset.id) { bmp = withContext(Dispatchers.IO) { DocumentStore.thumbnail(asset, 140) } }
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(58.dp).background(MB.ink.copy(alpha = 0.07f), shape), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Crop)
        else SFIcon(if (asset.isImage) "photo" else "doc.richtext.fill", size = 15.dp, tint = MB.ink3)
        if (asset.state == DocumentState.uploaded) {
            Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(11.dp).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center) {
                SFIcon("checkmark.circle.fill", size = 11.dp, tint = MB.green)
            }
        }
    }
}
