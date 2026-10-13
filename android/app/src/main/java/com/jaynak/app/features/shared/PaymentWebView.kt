package com.jaynak.app.features.shared

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

enum class PaymentWebOutcome { success, cancelled }

/**
 * مقابل PaymentWebView.swift. صفحات النجاح/الإلغاء لا تُحمَّل أبداً، بل تُراقَب فقط؛
 * التأكيد الحقيقي يتم من الخادم بعدها.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentWebView(
    url: String,
    successPrefix: String,
    cancelPrefix: String,
    modifier: Modifier = Modifier,
    onFinish: (PaymentWebOutcome) -> Unit,
) {
    val finished = remember { booleanArrayOf(false) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    private fun intercept(target: String): Boolean {
                        val outcome = when {
                            successPrefix.isNotEmpty() && target.startsWith(successPrefix) -> PaymentWebOutcome.success
                            cancelPrefix.isNotEmpty() && target.startsWith(cancelPrefix) -> PaymentWebOutcome.cancelled
                            else -> return false
                        }
                        if (!finished[0]) { finished[0] = true; onFinish(outcome) }
                        return true
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        intercept(request.url.toString())

                    override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                        if (u != null && intercept(u)) view.stopLoading()
                    }
                }
                loadUrl(url)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}
