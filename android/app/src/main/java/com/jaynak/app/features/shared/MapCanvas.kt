package com.jaynak.app.features.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jaynak.app.core.Coord
import com.jaynak.app.core.Geo
import com.jaynak.app.core.Place
import com.jaynak.app.core.AppConfig
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBType
import com.jaynak.app.design.SFIcon
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * مقابل MapCanvas.swift (MapKit). على أندرويد: osmdroid + دبابيس Compose
 * تُوضَع فوق الخريطة بإسقاط الإحداثيات، فتبقى نفس عناصر iOS (MapPin / DriverPuck).
 */
@Composable
fun MapCanvas(
    pickup: Place?,
    dropoff: Place?,
    modifier: Modifier = Modifier,
    driver: Coord? = null,
    driverName: String? = null,
    nearby: List<Coord> = emptyList(),
    showRoute: Boolean = true,
    interactive: Boolean = true,
) {
    val ctx = LocalContext.current
    val dark = MB.dark
    val density = LocalDensity.current
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var didFit by remember { mutableStateOf(false) }

    val routeCoords: List<Coord> = remember(pickup, dropoff, driver, showRoute) {
        if (!showRoute || pickup == null || dropoff == null) emptyList()
        else if (driver != null) Geo.route(driver, pickup.coord) + Geo.route(pickup.coord, dropoff.coord)
        else Geo.route(pickup.coord, dropoff.coord)
    }
    val green = MB.green

    fun fitTo(pts: List<Coord>, factor: Double, animate: Boolean) {
        val map = mapRef ?: return
        if (pts.isEmpty()) return
        val lats = pts.map { it.lat }
        val lons = pts.map { it.lon }
        val cLat = (lats.min() + lats.max()) / 2
        val cLon = (lons.min() + lons.max()) / 2
        val spanLat = max(0.012, (lats.max() - lats.min()) * factor)
        val spanLon = max(0.012, (lons.max() - lons.min()) * factor)
        val box = BoundingBox(cLat + spanLat / 2, cLon + spanLon / 2, cLat - spanLat / 2, cLon - spanLon / 2)
        map.post { map.zoomToBoundingBox(box, animate) ; tick++ }
    }

    // الملاءمة الأولى + عند تغيّر نقطتي الطلب
    LaunchedEffect(mapRef, pickup?.name, dropoff?.name) {
        didFit = false
        val pts = buildList {
            pickup?.let { add(it.coord) }; dropoff?.let { add(it.coord) }; driver?.let { add(it) }
        }.ifEmpty { nearby }
        if (mapRef != null && pts.isNotEmpty()) { didFit = true; fitTo(pts, 1.9, true) }
    }
    // تتبّع المندوب
    val driverKey = driver?.let { "%.4f,%.4f".format(java.util.Locale.US, it.lat, it.lon) } ?: ""
    LaunchedEffect(driverKey) {
        if (driver != null && pickup != null && dropoff != null) {
            fitTo(listOf(driver, pickup.coord, dropoff.coord), 1.8, true)
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                Configuration.getInstance().load(
                    context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                Configuration.getInstance().userAgentValue = "jaynak-android"
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(interactive)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    minZoomLevel = 4.0
                    controller.setZoom(10.0)
                    controller.setCenter(GeoPoint(Geo.cityCenter.lat, Geo.cityCenter.lon))
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean { tick++; return false }
                        override fun onZoom(event: ZoomEvent?): Boolean { tick++; return false }
                    })
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> tick++ }
                    mapRef = this
                }
            },
            update = { map ->
                // وضع داكن: عكس ألوان البلاط
                map.overlayManager.tilesOverlay.setColorFilter(
                    if (dark) TilesOverlay.INVERT_COLORS else null)
                map.overlays.removeAll { it is Polyline }
                if (routeCoords.isNotEmpty()) {
                    val line = Polyline(map).apply {
                        setPoints(routeCoords.map { GeoPoint(it.lat, it.lon) })
                        outlinePaint.color = android.graphics.Color.argb(
                            217, (green.red * 255).roundToInt(), (green.green * 255).roundToInt(),
                            (green.blue * 255).roundToInt())
                        outlinePaint.strokeWidth = 5f * density.density
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    map.overlays.add(line)
                }
                map.invalidate()
            },
        )

        if (!interactive) Box(Modifier.fillMaxSize().pointerInput(Unit) {})

        DisposableEffect(Unit) {
            onDispose { mapRef?.onDetach(); mapRef = null }
        }

        // دبابيس Compose فوق الخريطة (المرساة: أسفل الوسط كما في Annotation)
        val map = mapRef
        if (map != null && tick >= 0) {
            fun px(c: Coord): IntOffset? {
                val proj = map.projection ?: return null
                val p = proj.toPixels(GeoPoint(c.lat, c.lon), null)
                return IntOffset(p.x, p.y)
            }
            nearby.forEach { c ->
                px(c)?.let { o ->
                    Box(
                        Modifier.offset { IntOffset(o.x - 5.dp.roundToPx(), o.y - 5.dp.roundToPx()) }
                            .size(10.dp)
                            .background(MB.green.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    )
                }
            }
            pickup?.let { p ->
                px(p.coord)?.let { o -> PinAt(o) { MapPin("arrow.up.circle.fill", MB.green, "استلام") } }
            }
            dropoff?.let { d ->
                px(d.coord)?.let { o -> PinAt(o) { MapPin("flag.checkered", MB.red, "تسليم") } }
            }
            driver?.let { dc ->
                px(dc)?.let { o -> PinAt(o) { DriverPuck() } }
            }
        }
    }
}

@Composable
private fun PinAt(o: IntOffset, content: @Composable () -> Unit) {
    var w by remember { mutableIntStateOf(0) }
    var h by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .onSizeChanged { w = it.width; h = it.height }
            .offset { IntOffset(o.x - w / 2, o.y - h) }
    ) { content() }
}

@Composable
fun MapOverlayChip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .shadow(5.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .background(Color.Black.copy(alpha = 0.62f), CircleShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun MapPin(symbol: String, tint: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier.size(30.dp)
                .shadow(6.dp, CircleShape, ambientColor = tint.copy(alpha = 0.5f), spotColor = tint.copy(alpha = 0.5f))
                .background(tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) { SFIcon(symbol, size = 14.dp, tint = Color.White) }
        MapOverlayChip { Text(label, style = MBType.micro, color = Color.White) }
    }
}

@Composable
fun DriverPuck() {
    val t by rememberInfiniteTransition(label = "puck").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse")
    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        val d = (34f + 24f * t).dp
        Box(Modifier.size(d).alpha(0.9f * (1 - t)).background(MB.red.copy(alpha = 0.22f), CircleShape))
        Box(
            Modifier.size(34.dp)
                .shadow(5.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                .background(Brush.verticalGradient(listOf(MB.red, MB.red.copy(alpha = 0.7f))), CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { SFIcon("car.fill", size = 14.dp, tint = Color.White) }
    }
}

/** مقابل NavigationLauncher: Google Maps ثم Waze ثم المتصفح. */
object NavigationLauncher {
    fun open(context: Context, coord: Coord, name: String) {
        val lat = coord.lat; val lon = coord.lon
        val tries = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon&mode=d")).setPackage("com.google.android.apps.maps"),
            Intent(Intent.ACTION_VIEW, Uri.parse("waze://?ll=$lat,$lon&navigate=yes")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon&travelmode=driving")),
        )
        for (i in tries) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(i) }.isSuccess) return
        }
    }

    fun call(context: Context, phone: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${AppConfig.countryDialCode}$phone"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
