package com.jaynak.app.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Serializable
enum class DocumentKind {
    civilIDFront, civilIDBack, license, ownership, vehicle;

    val title: String
        get() = when (this) {
            civilIDFront -> "البطاقة المدنية — الوجه"
            civilIDBack -> "البطاقة المدنية — الظهر"
            license -> "رخصة القيادة"
            ownership -> "ملكية المركبة"
            vehicle -> "صور المركبة"
        }

    val hint: String
        get() = when (this) {
            civilIDFront -> "الوجه الذي يحمل صورتك ورقمك المدني"
            civilIDBack -> "الوجه الخلفي كاملاً وواضحاً"
            license -> "رخصة سارية غير منتهية"
            ownership -> "استمارة الملكية باسمك أو بتوكيل"
            vehicle -> "صورتان على الأقل: الأمام واللوحة"
        }

    val symbol: String
        get() = when (this) {
            civilIDFront, civilIDBack -> "person.text.rectangle.fill"
            license -> "creditcard.fill"
            ownership -> "doc.on.doc.fill"
            vehicle -> "car.fill"
        }

    val isRequired: Boolean get() = this != civilIDBack

    val minimumCount: Int get() = if (this == vehicle) 2 else 1
    val maximumCount: Int get() = if (this == vehicle) 4 else 1
}

@Serializable
enum class DocumentState {
    /** محفوظ على الجهاز، لم يُرفع بعد */
    stored,
    uploading,
    uploaded,
    failed;

    val isSettled: Boolean get() = this == uploaded || this == stored
}

@Serializable
data class DocumentAsset(
    val id: Id = newId(),
    val kind: DocumentKind,

    val filename: String,
    val mime: String,
    val bytes: Int,

    val remoteID: String? = null,
    val uploadedAt: Long? = null,
    val state: DocumentState = DocumentState.stored,
    val failureReason: String? = null,
) {
    val isImage: Boolean get() = mime.startsWith("image/")
    val sizeLabel: String
        get() {
            val kb = bytes / 1024.0
            return if (kb < 1024) String.format(Locale.US, "%.0f ك.ب", kb)
            else String.format(Locale.US, "%.1f م.ب", kb / 1024)
        }
}

object DocumentRules {

    const val maxBytes = 8 * 1024 * 1024
    val allowedMIME: Set<String> = setOf("image/jpeg", "image/png", "application/pdf")
    val allowedExtensions: Set<String> = setOf("jpg", "jpeg", "png", "pdf")

    sealed class Rejection(override val message: String) : Exception(message) {
        class TooLarge(val size: Int) : Rejection("الملف أكبر من الحجم المسموح (٨ م.ب)")
        class UnsupportedType(val mime: String) :
            Rejection("صيغة الملف غير مدعومة — استخدم JPG أو PNG أو PDF")
        object Unreadable : Rejection("تعذّر قراءة الملف، أعد المحاولة")
    }

    fun validate(data: ByteArray, mime: String): Rejection? {
        if (data.isEmpty()) return Rejection.Unreadable
        if (data.size > maxBytes) return Rejection.TooLarge(data.size)
        if (mime !in allowedMIME) return Rejection.UnsupportedType(mime)

        val sniffed = sniff(data)
        if (sniffed == null || sniffed !in allowedMIME) return Rejection.UnsupportedType(mime)
        return null
    }

    /** يتعرّف على النوع من أول بايتات الملف، لا من امتداده ولا MIME العميل. */
    fun sniff(data: ByteArray): String? {
        if (data.size < 4) return null
        val b = data.take(8).map { it.toInt() and 0xFF }
        if (b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF) return "image/jpeg"
        if (b[0] == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) return "image/png"
        if (b[0] == 0x25 && b[1] == 0x50 && b[2] == 0x44 && b[3] == 0x46) return "application/pdf"
        return null
    }
}

object DocumentStore {

    val directory: File
        get() {
            val dir = File(Boot.app.filesDir, "JaynakDocuments")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun file(filename: String): File = File(directory, filename)

    fun save(data: ByteArray, kind: DocumentKind, mime: String): DocumentAsset {
        DocumentRules.validate(data, mime)?.let { throw it }
        val ext = if (mime == "application/pdf") "pdf" else if (mime == "image/png") "png" else "jpg"
        val name = "${kind.name}-${newId()}.$ext"
        file(name).writeBytes(data)
        return DocumentAsset(kind = kind, filename = name, mime = mime, bytes = data.size)
    }

    fun data(asset: DocumentAsset): ByteArray? =
        runCatching { file(asset.filename).readBytes() }.getOrNull()

    fun thumbnail(asset: DocumentAsset, maxSide: Int = 240): Bitmap? {
        if (!asset.isImage) return null
        val bytes = data(asset) ?: return null
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val longest = max(image.width, image.height)
        val scale = min(1f, maxSide.toFloat() / longest)
        if (scale >= 1f) return image
        return Bitmap.createScaledBitmap(
            image, (image.width * scale).toInt().coerceAtLeast(1),
            (image.height * scale).toInt().coerceAtLeast(1), true,
        )
    }

    fun delete(asset: DocumentAsset) {
        runCatching { file(asset.filename).delete() }
    }

    /** يصغّر الصورة لأطول ضلع 2000 ويضغطها JPEG 82٪ — كما في iOS. */
    fun jpegData(image: Bitmap, maxDimension: Int = 2000): ByteArray? {
        val longest = max(image.width, image.height)
        val scale = min(1f, maxDimension.toFloat() / longest)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                image, (image.width * scale).toInt().coerceAtLeast(1),
                (image.height * scale).toInt().coerceAtLeast(1), true,
            )
        } else image
        val out = ByteArrayOutputStream()
        return if (resized.compress(Bitmap.CompressFormat.JPEG, 82, out)) out.toByteArray() else null
    }
}
