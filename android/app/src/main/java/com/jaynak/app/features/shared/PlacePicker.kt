package com.jaynak.app.features.shared

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import com.jaynak.app.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jaynak.app.core.Coord
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.Geo
import com.jaynak.app.core.LocationService
import com.jaynak.app.core.Place
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassChip
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/**
 * شاشة اختيار موقع واحدة: خريطة سلسة بدبوس ثابت في المنتصف، وشريط بحث فوقها.
 * كل ما يكتبه الزبون ينتقل إليه الدبوس (أول نتيجة)، ويمكنه لصق رابط موقع فيتحدّد فوراً، أو تحريك الخريطة بنفسه.
 */
@Composable
fun PlacePicker(
    title: String,
    tint: Color,
    saved: List<Place> = emptyList(),
    onPick: (Place) -> Unit,
    onDismiss: () -> Unit,
) {
    val loc = LocationService
    val dark = MB.dark
    val clipboard = LocalClipboardManager.current

    var query by remember { mutableStateOf("") }
    var pinZone by remember { mutableStateOf("") }
    var usedLink by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        var last = ""
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val t = clipboard.getText()?.text?.trim().orEmpty()
                if (t.isNotEmpty() && t != last && com.jaynak.app.core.MapLinks.looksLikeLink(t)) { last = t; query = t }
            }
        }
        lifecycle.addObserver(ob)
        onDispose { lifecycle.removeObserver(ob) }
    }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf(false) }

    var pinCoord by remember { mutableStateOf(Geo.cityCenter) }
    var pinName by remember { mutableStateOf("") }
    var named by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settleJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val ignoreUntil = remember { longArrayOf(0L) }

    fun flyTo(c: Coord, zoom: Double) {
        ignoreUntil[0] = System.currentTimeMillis() + 1100
        mapRef?.controller?.animateTo(GeoPoint(c.lat, c.lon), zoom, 550L)
    }

    LaunchedEffect(loc.current) {
        val new = loc.current ?: return@LaunchedEffect
        named = false
        flyTo(Coord(new.lat, new.lon), 17.0)
    }

    LaunchedEffect(query) {
        val q = query.trim()
        linkError = false
        if (q.length < 2) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        kotlinx.coroutines.delay(450)
        if (com.jaynak.app.core.MapLinks.looksLikeLink(q)) {
            val c = com.jaynak.app.core.MapLinks.resolve(q)
            if (c != null) {
                usedLink = q
                results = emptyList(); showList = false; named = false
                flyTo(c, 17.0)
            } else linkError = true
            searching = false
            return@LaunchedEffect
        }
        val local = (saved + Geo.places).filter { it.name.contains(q) || it.zone.contains(q) }.distinctBy { it.name }
        val remote = com.jaynak.app.core.PlaceSearch.search(q)
        results = (local + remote).distinctBy { it.name + it.coord.lat }.take(8)
        results.firstOrNull()?.let { named = true; pinName = it.name; flyTo(it.coord, 15.5) }
        showList = results.isNotEmpty()
        searching = false
    }

    // لا نعيد التركيب مع كل حركة إصبع: نؤجل العمل الثقيل (اسم المكان) حتى تستقر الخريطة.
    fun settle() {
        val m = mapRef ?: return
        settleJob[0]?.cancel()
        settleJob[0] = scope.launch {
            kotlinx.coroutines.delay(450)
            val c = m.mapCenter
            val coord = Coord(c.latitude, c.longitude)
            pinCoord = coord
            com.jaynak.app.core.PlaceSearch.area(coord)?.let { pinZone = it }
            if (!named) {
                resolving = true
                com.jaynak.app.core.PlaceSearch.reverse(coord)?.let { if (!named) pinName = it }
                resolving = false
            }
        }
    }
    LaunchedEffect(mapRef) { if (mapRef != null) settle() }

    JSheet(onDismiss = onDismiss, gestures = false) {   // سحب الشاشة بإصبع واحد يخص الخريطة فقط
        Box(Modifier.fillMaxSize()) {
            BrandBackground(intensity = 0.5f)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("إلغاء", style = MBType.callout, color = MB.green,
                        modifier = Modifier.clickableNoRipple(onDismiss))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(title, style = MBType.headline, color = MB.ink)
                    }
                    Spacer(Modifier.size(36.dp))
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                            Configuration.getInstance().userAgentValue = "jaynak-android"
                            Configuration.getInstance().cacheMapTileCount = 96
                            Configuration.getInstance().tileDownloadThreads = 8
                            Configuration.getInstance().tileFileSystemThreads = 6
                            Configuration.getInstance().animationSpeedDefault = 250
                            MapView(context).apply {
                                MapTiles.apply(this, dark)
                                setMultiTouchControls(true)
                                setOnTouchListener { v, e ->
                                    if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN || e.actionMasked == android.view.MotionEvent.ACTION_MOVE)
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    false
                                }
                                isHorizontalMapRepetitionEnabled = false
                                isVerticalMapRepetitionEnabled = false
                                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                                val start = loc.current ?: pinCoord
                                controller.setZoom(if (loc.current != null) 17.0 else 12.0)
                                controller.setCenter(GeoPoint(start.lat, start.lon))
                                addMapListener(object : MapListener {
                                    override fun onScroll(event: ScrollEvent?): Boolean {
                                        if (System.currentTimeMillis() > ignoreUntil[0]) { named = false; showList = false }
                                        settle(); return false
                                    }
                                    override fun onZoom(event: ZoomEvent?): Boolean { settle(); return false }
                                })
                                mapRef = this
                            }
                        },
                        update = { MapTiles.apply(it, dark) },
                    )
                    MapStyleSwitch(Modifier.align(Alignment.BottomEnd).padding(10.dp))
                    var pinH by remember { mutableIntStateOf(0) }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.onSizeChanged { pinH = it.height }.offset { IntOffset(0, -pinH / 2) }) {
                            MapPin("mappin", tint, "هنا")
                        }
                    }

                    Column(Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopCenter),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().height(50.dp).glassSurface(18.dp, null, 0.22f).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            SFIcon("magnifyingglass", size = 16.dp, tint = MB.ink3)
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text("ابحث عن مكان، أو الصق رابط الموقع", style = MBType.body, color = MB.ink3, maxLines = 1)
                                BasicTextField(query, { query = it; showList = true }, singleLine = true,
                                    textStyle = MBType.body.copy(color = MB.ink),
                                    cursorBrush = SolidColor(MB.green), modifier = Modifier.fillMaxWidth())
                            }
                            if (searching) CircularProgressIndicator(Modifier.size(16.dp), color = MB.green, strokeWidth = 2.dp)
                            else if (query.isNotEmpty())
                                Box(Modifier.clickableNoRipple { query = ""; results = emptyList(); showList = false }) {
                                    SFIcon("xmark", size = 13.dp, tint = MB.ink3)
                                }
                            else Text("قوقل ماب", style = MBType.callout, color = tint,
                                modifier = Modifier.clickableNoRipple {
                                    runCatching {
                                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://www.google.com/maps"))
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                })
                            if (!searching && query.isEmpty()) Text("  لصق", style = MBType.callout, color = tint,
                                modifier = Modifier.clickableNoRipple {
                                    clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { query = it.trim() }
                                })
                        }

                        if (linkError) Text("لم نستطع قراءة موقع من هذا الرابط — جرّب رابط «مشاركة الموقع» أو اكتب اسم المكان",
                            style = MBType.micro, color = MB.red, modifier = Modifier.glassChip().padding(horizontal = 12.dp, vertical = 7.dp))

                        if (query.isEmpty() && saved.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                saved.take(8).forEach { p ->
                                    Row(Modifier.glassChip().pressable { named = true; pinName = p.name; flyTo(p.coord, 16.5) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SFIcon("star.fill", size = 11.dp, tint = tint)
                                        Text(p.name, style = MBType.caption, color = MB.ink, maxLines = 1)
                                    }
                                }
                            }
                        }

                        if (showList && results.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().heightIn(max = 250.dp)
                                .glassSurface(18.dp, null, 0.3f).verticalScroll(rememberScrollState())) {
                                results.forEach { p ->
                                    Row(Modifier.fillMaxWidth()
                                        .pressable(0.99f) {
                                            Feedback.selection()
                                            named = true; pinName = p.name; showList = false
                                            flyTo(p.coord, 16.5)
                                        }.padding(horizontal = 14.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SFIcon(if (saved.any { it.name == p.name }) "star.fill" else "mappin", size = 14.dp, tint = tint)
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                            Text(p.name, style = MBType.callout, color = MB.ink, maxLines = 1)
                                            Text(if (p.details.isEmpty()) p.zone else "${p.zone} · ${p.details}",
                                                style = MBType.micro, color = MB.ink2, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(Modifier.fillMaxSize().padding(14.dp)) {
                        GlassIconButton("location.fill", { named = false; loc.locate() }, tint = tint, prominent = true,
                            modifier = Modifier.align(Alignment.BottomStart))
                        Text(if (resolving) "نحدّد العنوان…" else "حرّك الخريطة ليستقر الدبوس على المكان",
                            style = MBType.micro, color = MB.ink,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .glassChip().padding(horizontal = 12.dp, vertical = 7.dp))
                        loc.error?.let { err ->
                            Text(err, style = MBType.micro, color = MB.red,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 44.dp)
                                    .glassChip().padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }

                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MBField("وصف المكان", pinName, { named = it.isNotEmpty(); pinName = it },
                        placeholder = "مثال: منزل ١٢، شارع ٣", symbol = "text.alignright")
                    KeyValueRow("الولاية", (pinZone.ifEmpty { Geo.nearestZone(pinCoord) }), isNumber = false)
                    PrimaryCTA("تثبيت هذه النقطة", symbol = "mappin.and.ellipse", tint = tint,
                        onClick = {
                            Feedback.success()
                            onPick(Place(name = pinName.ifEmpty { (pinZone.ifEmpty { Geo.nearestZone(pinCoord) }) },
                                zone = (pinZone.ifEmpty { Geo.nearestZone(pinCoord) }), coord = pinCoord, details = "نقطة على الخريطة", link = usedLink))
                            onDismiss()
                        })
                }
            }
        }
    }
}
