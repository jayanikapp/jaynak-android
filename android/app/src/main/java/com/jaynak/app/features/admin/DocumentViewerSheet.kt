package com.jaynak.app.features.admin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AdminDocumentService
import com.jaynak.app.core.DocumentKind
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.EmptyStateView
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.clickableNoRipple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private suspend fun fetchBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = URL(url).openStream().use { it.readBytes() }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

/** مقابل DocumentViewerSheet.swift — عرض صور مستندات المندوب للمسؤول. */
@Composable
fun DocumentViewerSheet(driverPhone: String, kind: DocumentKind, onDismiss: () -> Unit) {
    var links by remember { mutableStateOf<List<AdminDocumentService.DocLink>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(driverPhone, kind) {
        try {
            links = AdminDocumentService.links(driverPhone, kind)
        } catch (e: Exception) {
            loadError = e.message ?: "تعذّر التحميل"
        }
        loading = false
    }

    JSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.6f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق", style = MBType.callout, color = MB.green, modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(kind.title, style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.padding(horizontal = 20.dp))
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(color = MB.green)
                        loadError != null -> EmptyStateView("exclamationmark.triangle.fill", "تعذّر التحميل", loadError!!)
                        links.isEmpty() -> EmptyStateView("doc.text", "لا مستندات", "لم يرفع المندوب هذا المستند بعد")
                        else -> {
                            val pager = rememberPagerState { links.size }
                            Box(Modifier.fillMaxSize()) {
                                HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                                    var bmp by remember(links[page].url) { mutableStateOf<Bitmap?>(null) }
                                    var failed by remember(links[page].url) { mutableStateOf(false) }
                                    LaunchedEffect(links[page].url) {
                                        bmp = fetchBitmap(links[page].url)
                                        failed = bmp == null
                                    }
                                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                        val b = bmp
                                        when {
                                            b != null -> Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                            failed -> EmptyStateView("xmark.octagon.fill", "تعذّر عرض الملف",
                                                "قد يكون المستند PDF — غير مدعوم في هذا العارض بعد")
                                            else -> CircularProgressIndicator(color = MB.green)
                                        }
                                    }
                                }
                                if (links.size > 1) {
                                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        repeat(links.size) { i ->
                                            Box(Modifier.size(7.dp).background(
                                                if (i == pager.currentPage) MB.ink else MB.ink.copy(alpha = 0.25f), CircleShape))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
