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
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.jaynak.app.core.RoadRouter
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
    driverHeading: Float? = null,
    routePath: List<Coord>? = null,
    cars: List<CarMarker> = emptyList(),
    pins: List<PinMarker> = emptyList(),
    onCarTap: (String) -> Unit = {},
    fitPoints: List<Coord>? = null,
    fitKey: Any? = null,
    followDriver: Boolean = true,
    driverStale: Boolean = false,
) {
    val ctx = LocalContext.current
    val dark = MB.dark
    val density = LocalDensity.current
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var frame by remember { mutableLongStateOf(0L) }
    var didFit by remember { mutableStateOf(false) }

    val syntheticRoute: List<Coord> = remember(pickup, dropoff, driver, showRoute) {
        if (!showRoute || pickup == null || dropoff == null) emptyList()
        else if (driver != null) Geo.route(driver, pickup.coord) + Geo.route(pickup.coord, dropoff.coord)
        else Geo.route(pickup.coord, dropoff.coord)
    }
    // مسار حقيقي على الشوارع (OSRM) يحلّ محل التقريبي عند وصوله
    val roadKey = "%.3f,%.3f|%.4f,%.4f|%.4f,%.4f".format(java.util.Locale.US,
        driver?.lat ?: 0.0, driver?.lon ?: 0.0, pickup?.coord?.lat ?: 0.0, pickup?.coord?.lon ?: 0.0,
        dropoff?.coord?.lat ?: 0.0, dropoff?.coord?.lon ?: 0.0)
    var road by remember { mutableStateOf<List<Coord>?>(null) }
    LaunchedEffect(roadKey, showRoute, routePath) {
        if (!showRoute || !routePath.isNullOrEmpty() || pickup == null || dropoff == null) {
            road = null; return@LaunchedEffect
        }
        RoadRouter.route(listOfNotNull(driver, pickup.coord, dropoff.coord))?.let { road = it }
    }
    val routeCoords: List<Coord> =
        if (showRoute && !routePath.isNullOrEmpty()) routePath
        else if (!showRoute) emptyList()
        else road ?: syntheticRoute
    val green = MB.green
    val greenArgb = MB.green.toArgb()
    val redArgb = MB.red.toArgb()
    val overlayPins: List<MapPinOverlay.Pin> = buildList {
        pickup?.let { add(MapPinOverlay.Pin(it.coord.lat, it.coord.lon, "arrow.up.circle.fill", greenArgb, "استلام")) }
        dropoff?.let { add(MapPinOverlay.Pin(it.coord.lat, it.coord.lon, "flag.checkered", redArgb, "تسليم")) }
        pins.forEach { add(MapPinOverlay.Pin(it.coord.lat, it.coord.lon, it.symbol, it.tint.toArgb(), it.label)) }
    }
    val overlayDots: List<MapPinOverlay.Dot> = nearby.map { MapPinOverlay.Dot(it.lat, it.lon) }
    val overlayCars: List<MapCarOverlay.Car> = buildList {
        driver?.let { dc -> add(MapCarOverlay.Car("driver", dc.lat, dc.lon, driverHeading, 0xFFE5303B.toInt(), null, false, driverStale)) }
        cars.forEach { add(MapCarOverlay.Car(it.id, it.coord.lat, it.coord.lon, it.heading, it.tint.toArgb(), it.label, it.selected, it.stale)) }
    }

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

    // مزامنة الدبابيس مع الخريطة كل إطار أثناء السحب والتكبير
    LaunchedEffect(mapRef) {
        val m = mapRef ?: return@LaunchedEffect
        var lat = 0.0; var lon = 0.0; var z = 0.0; var sx = 0; var sy = 0
        while (true) {
            withFrameNanos { }
            val c = m.mapCenter
            if (c.latitude != lat || c.longitude != lon || m.zoomLevelDouble != z || m.scrollX != sx || m.scrollY != sy) {
                lat = c.latitude; lon = c.longitude; z = m.zoomLevelDouble; sx = m.scrollX; sy = m.scrollY
                frame++
            }
        }
    }

    // الملاءمة الأولى + عند تغيّر نقطتي الطلب
    LaunchedEffect(mapRef, pickup?.name, dropoff?.name, fitKey) {
        didFit = false
        val pts = fitPoints?.takeIf { it.isNotEmpty() } ?: buildList {
            pickup?.let { add(it.coord) }; dropoff?.let { add(it.coord) }; driver?.let { add(it) }
        }.ifEmpty { nearby }
        if (mapRef != null && pts.isNotEmpty()) { didFit = true; fitTo(pts, 1.9, true) }
    }
    // تتبّع المندوب
    val driverKey = driver?.let { "%.4f,%.4f".format(java.util.Locale.US, it.lat, it.lon) } ?: ""
    LaunchedEffect(driverKey) {
        if (followDriver && driver != null && pickup != null && dropoff != null) {
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
                    MapTiles.apply(this, dark)
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
                MapTiles.apply(map, dark)
                map.overlays.removeAll { it is Polyline }
                if (routeCoords.isNotEmpty()) {
                    val geo = routeCoords.map { GeoPoint(it.lat, it.lon) }
                    // حدّ داكن ثم خط ملوّن فوقه: يظهر المسار واضحاً على أي خلفية
                    listOf(9f to android.graphics.Color.argb(120, 0, 0, 0),
                        5.5f to android.graphics.Color.argb(240, (green.red * 255).roundToInt(),
                            (green.green * 255).roundToInt(), (green.blue * 255).roundToInt())
                    ).forEach { (w, col) ->
                        map.overlays.add(Polyline(map).apply {
                            setPoints(geo)
                            outlinePaint.color = col
                            outlinePaint.strokeWidth = w * density.density
                            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                            outlinePaint.isAntiAlias = true
                        })
                    }
                }
                map.overlays.removeAll { it is MapPinOverlay }
                map.overlays.add(MapPinOverlay(density.density).also { it.pins = overlayPins; it.dots = overlayDots })
                val carOv = (map.overlays.firstOrNull { it is MapCarOverlay } as? MapCarOverlay)
                    ?: MapCarOverlay(density.density)
                map.overlays.remove(carOv)
                carOv.onTap = onCarTap
                carOv.setCars(overlayCars)
                map.overlays.add(carOv)
                map.invalidate()
            },
        )

        if (!interactive) Box(Modifier.fillMaxSize().pointerInput(Unit) {})

        DisposableEffect(Unit) {
            onDispose { mapRef?.onDetach(); mapRef = null }
        }
    }
}

@Composable
private fun NearDot(c: Coord, px: (Coord) -> IntOffset?) {
    Box(
        Modifier.absoluteOffset {
            val o = px(c) ?: return@absoluteOffset IntOffset(-10000, -10000)
            IntOffset(o.x - 5.dp.roundToPx(), o.y - 5.dp.roundToPx())
        }
            .size(10.dp)
            .background(MB.green.copy(alpha = 0.5f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
    )
}

@Composable
private fun CarAt(c: CarMarker, px: (Coord) -> IntOffset?, onTap: () -> Unit) {
    val (pos, rot, _) = rememberSmoothCar(c.coord, c.heading)
    var w by remember { mutableIntStateOf(0) }
    var h by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .onSizeChanged { w = it.width; h = it.height }
            .absoluteOffset {
                val o = px(pos) ?: return@absoluteOffset IntOffset(-10000, -10000)
                IntOffset(o.x - w / 2, o.y - h / 2)
            }
            .pointerInput(c.id) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        LiveCar(rot, tint = c.tint, dim = c.stale, selected = c.selected)
        c.label?.let {
            MapOverlayChip(Modifier.offset(y = 44.dp)) {
                Text(it, style = MBType.micro, color = Color.White)
            }
        }
    }
}

@Composable
private fun PinAt(coord: Coord, px: (Coord) -> IntOffset?, content: @Composable () -> Unit) {
    var w by remember { mutableIntStateOf(0) }
    var h by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .onSizeChanged { w = it.width; h = it.height }
            .absoluteOffset {
                val o = px(coord) ?: return@absoluteOffset IntOffset(-10000, -10000)
                IntOffset(o.x - w / 2, o.y - h)
            }
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
        val local = phone.filter { it.isDigit() }.takeLast(8)
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${AppConfig.countryDialCode}$local"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** يفتح محادثة واتساب مع الرقم مباشرة. */
    fun whatsapp(context: Context, phone: String) {
        val local = phone.filter { it.isDigit() }.takeLast(8)
        val full = AppConfig.countryDialCode.filter { it.isDigit() } + local
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$full"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
