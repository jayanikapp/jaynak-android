package com.jaynak.app.features.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.viewinterop.AndroidView
import com.jaynak.app.core.Coord
import com.jaynak.app.core.Feedback
import com.jaynak.app.core.Geo
import com.jaynak.app.core.ImpactStyle
import com.jaynak.app.core.LocationService
import com.jaynak.app.core.Place
import com.jaynak.app.design.BrandBackground
import com.jaynak.app.design.GlassButton
import com.jaynak.app.design.GlassIconButton
import com.jaynak.app.design.JSheet
import com.jaynak.app.design.KeyValueRow
import com.jaynak.app.design.MB
import com.jaynak.app.design.MBField
import com.jaynak.app.design.MBType
import com.jaynak.app.design.PrimaryCTA
import com.jaynak.app.design.SFIcon
import com.jaynak.app.design.SegmentedPicker
import com.jaynak.app.design.clickableNoRipple
import com.jaynak.app.design.glassChip
import com.jaynak.app.design.glassSurface
import com.jaynak.app.design.pressable
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.TilesOverlay

/** مقابل PlacePicker.swift. */
@Composable
fun PlacePicker(
    title: String,
    tint: Color,
    saved: List<Place> = emptyList(),
    onPick: (Place) -> Unit,
    onDismiss: () -> Unit,
) {
    val loc = LocationService
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }
    var droppedCoord by remember { mutableStateOf(Geo.cityCenter) }
    var droppedName by remember { mutableStateOf("") }

    val results: List<Place> = run {
        val all = saved + Geo.places.filter { p -> saved.none { it.name == p.name } }
        if (query.isEmpty()) all else all.filter { it.name.contains(query) || it.zone.contains(query) }
    }

    JSheet(onDismiss = onDismiss) {
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
                SegmentedPicker(listOf("بحث", "على الخريطة"), mode, { mode = it },
                    Modifier.padding(horizontal = 18.dp).padding(top = 10.dp))

                if (mode == 0) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LocateCard(tint) { p ->
                            Feedback.success(); onPick(p); onDismiss()
                        }

                        Row(
                            Modifier.fillMaxWidth().height(48.dp).glassSurface(16.dp, null, 0.08f).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            SFIcon("magnifyingglass", size = 16.dp, tint = MB.ink3)
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text("ابحث باسم المكان أو الولاية", style = MBType.body, color = MB.ink3)
                                BasicTextField(query, { query = it }, singleLine = true,
                                    textStyle = MBType.body.copy(color = MB.ink),
                                    cursorBrush = SolidColor(MB.green), modifier = Modifier.fillMaxWidth())
                            }
                        }

                        if (saved.isNotEmpty() && query.isEmpty()) {
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Text("أماكنك المحفوظة", style = MBType.caption, color = MB.ink2)
                            }
                        }

                        results.forEach { p ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .pressable(0.985f) { Feedback.selection(); onPick(p); onDismiss() }
                                    .glassSurface(18.dp, null, 0.08f).padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(38.dp).background(tint.copy(alpha = 0.16f), CircleShape),
                                    contentAlignment = Alignment.Center) {
                                    SFIcon(if (saved.any { it.name == p.name }) "star.fill" else "mappin",
                                        size = 14.dp, tint = tint)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(p.name, style = MBType.headline, color = MB.ink)
                                    Text(if (p.details.isEmpty()) p.zone else "${p.zone} · ${p.details}",
                                        style = MBType.caption, color = MB.ink2)
                                }
                            }
                        }
                    }
                } else {
                    MapMode(
                        tint = tint, dropped = droppedCoord,
                        onDropped = { droppedCoord = it },
                        droppedName = droppedName, onName = { droppedName = it },
                        onPick = { onPick(it); onDismiss() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocateCard(tint: Color, onPlace: (Place) -> Unit) {
    val loc = LocationService
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .pressable {
                    val p = loc.currentPlace
                    if (p != null && !loc.isResolving) onPlace(p) else loc.locate()
                }
                .glassSurface(20.dp, tint.copy(alpha = 0.14f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp)
                    .shadow(8.dp, CircleShape, ambientColor = tint.copy(alpha = 0.45f), spotColor = tint.copy(alpha = 0.45f))
                    .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.55f))), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (loc.isResolving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else SFIcon("location.fill", size = 17.dp, tint = Color.White)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (loc.isResolving) "نحدّد موقعك…" else "استخدم موقعي الحالي",
                    style = MBType.headline, color = MB.ink)
                Text(loc.currentPlace?.name ?: (if (loc.isDenied) "الإذن مرفوض" else "تحديد دقيق بضغطة واحدة"),
                    style = MBType.caption, color = if (loc.isDenied) MB.red else MB.ink2, maxLines = 1)
            }
            SFIcon("chevron.forward", size = 12.dp, tint = tint)
        }

        loc.error?.let { err ->
            Row(
                Modifier.fillMaxWidth().glassSurface(16.dp, null, 0.06f).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SFIcon("exclamationmark.triangle.fill", size = 14.dp, tint = MB.red)
                Text(err, style = MBType.micro, color = MB.ink2, modifier = Modifier.weight(1f))
                if (loc.isDenied) {
                    GlassButton("الإعدادات", onClick = {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }, tint = MB.red, horizontal = 12.dp, vertical = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun MapMode(
    tint: Color,
    dropped: Coord,
    onDropped: (Coord) -> Unit,
    droppedName: String,
    onName: (String) -> Unit,
    onPick: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loc = LocationService
    val dark = MB.dark
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(loc.current) {
        val new = loc.current ?: return@LaunchedEffect
        onDropped(new)
        if (droppedName.isEmpty()) onName(loc.resolvedName ?: "")
        mapRef?.let { m ->
            m.controller.animateTo(GeoPoint(new.lat, new.lon), 16.0, 600L)
        }
    }

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                    Configuration.getInstance().userAgentValue = "jaynak-android"
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(11.0)
                        controller.setCenter(GeoPoint(Geo.cityCenter.lat, Geo.cityCenter.lon))
                        overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                Feedback.impact(ImpactStyle.light)
                                onDropped(Coord(p.latitude, p.longitude)); return true
                            }
                            override fun longPressHelper(p: GeoPoint): Boolean = false
                        }))
                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean { tick++; return false }
                            override fun onZoom(event: ZoomEvent?): Boolean { tick++; return false }
                        })
                        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> tick++ }
                        mapRef = this
                    }
                },
                update = { it.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null) },
            )
            val map = mapRef
            if (map != null && tick >= 0) {
                val p = map.projection?.toPixels(GeoPoint(dropped.lat, dropped.lon), null)
                if (p != null) {
                    var w by remember { mutableIntStateOf(0) }
                    var h by remember { mutableIntStateOf(0) }
                    Box(Modifier.onSizeChanged { w = it.width; h = it.height }
                        .absoluteOffset { IntOffset(p.x - w / 2, p.y - h) }) { MapPin("mappin", tint, "هنا") }
                }
            }
            Box(Modifier.fillMaxSize().padding(14.dp)) {
                GlassIconButton("location.fill", { loc.locate() }, tint = tint, prominent = true,
                    modifier = Modifier.align(Alignment.TopStart))
                Text("انقر على الخريطة لتحديد النقطة", style = MBType.micro, color = MB.ink,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 0.dp)
                        .glassChip().padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }

        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MBField("وصف المكان", droppedName, onName, placeholder = "مثال: منزل ١٢، شارع ٣", symbol = "text.alignright")
            KeyValueRow("الإحداثيات", "%.4f, %.4f".format(java.util.Locale.US, dropped.lat, dropped.lon))
            KeyValueRow("الولاية", Geo.nearestZone(dropped), isNumber = false)
            PrimaryCTA("تثبيت هذه النقطة", symbol = "mappin.and.ellipse", tint = tint,
                enabled = droppedName.isNotEmpty(),
                onClick = {
                    onPick(Place(name = droppedName, zone = Geo.nearestZone(dropped),
                        coord = dropped, details = "نقطة على الخريطة"))
                })
        }
    }
}
