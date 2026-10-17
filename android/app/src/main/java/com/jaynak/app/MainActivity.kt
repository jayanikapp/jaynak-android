package com.jaynak.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jaynak.app.core.AppStore
import com.jaynak.app.core.LocationService
import com.jaynak.app.core.Notifications
import com.jaynak.app.core.UserRole
import com.jaynak.app.design.JaynakTheme
import com.jaynak.app.design.LocalStore
import com.jaynak.app.design.ToastBanner
import com.jaynak.app.features.admin.AdminRoot
import com.jaynak.app.features.auth.AuthSheet
import com.jaynak.app.features.customer.CustomerRoot
import com.jaynak.app.features.driver.DriverRoot
import com.jaynak.app.features.splash.SplashView
import kotlinx.coroutines.delay

/** مقابل JaynakApp.swift + RootView. */
class MainActivity : ComponentActivity() {

    private val store: AppStore by lazy { AppStore() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.jaynak.app.core.Boot.activity = this
        enableEdgeToEdge()
        setContent {
            // اتجاه RTL إجباري كما في iOS
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalStore provides store,
            ) {
                JaynakTheme { RootView(store) }
            }
        }
    }
}

@Composable
private fun RootView(store: AppStore) {
    var showSplash by remember { mutableStateOf(true) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { res ->
        LocationService.onPermissionResult(res.values.any { it })
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    DisposableEffect(Unit) {
        LocationService.requestPermission = {
            locationLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ))
        }
        Notifications.requestPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onDispose {
            LocationService.requestPermission = null
            Notifications.requestPermission = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (showSplash) {
            SplashView { showSplash = false }
        } else {
            when (store.session?.role) {
                null, UserRole.customer -> CustomerRoot()
                UserRole.driver -> DriverRoot()
                UserRole.admin -> AdminRoot()
            }
        }

        val scope = rememberCoroutineScope()
        LaunchedEffect(store.session?.phone) {
            store.blockedReason = ""
            while (true) {
                store.pollBlockedReason()
                store.refreshNearbyDrivers()
                delay(20_000)
            }
        }
        if (store.blockedReason.isNotEmpty()) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.82f))
                .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.Text("⚠️", fontSize = 44.sp)
                    androidx.compose.material3.Text("الحساب متوقف", color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    androidx.compose.material3.Text("راجع الإدارة بسبب انخفاض النقاط", color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    androidx.compose.material3.Text(store.blockedReason, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    androidx.compose.material3.TextButton(onClick = { scope.launch { store.pollBlockedReason() } }) {
                        androidx.compose.material3.Text("تحديث")
                    }
                    androidx.compose.material3.TextButton(onClick = { store.signOut() }) {
                        androidx.compose.material3.Text("تسجيل الخروج", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // طبقة الإشعار العلوي
        val toast = store.toast
        LaunchedEffect(toast?.id) {
            if (toast != null) {
                delay(2800)
                if (store.toast?.id == toast.id) store.toast = null
            }
        }
        Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp)) {
            AnimatedVisibility(
                visible = toast != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                // يحتفظ بآخر قيمة أثناء الخروج
                val shown = remember(toast?.id) { toast }
                if (shown != null) ToastBanner(shown)
            }
        }

        store.authRequest?.let { req ->
            AuthSheet(
                initialRole = req.role, reason = req.reason,
                onDismiss = { store.authRequest = null },
            )
        }
    }
}
