package com.jaynak.app.features.shared

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jaynak.app.core.Boot
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView

enum class MapStyle(val title: String) {
    clear("واضحة"),
    streets("شوارع"),
    satellite("قمر صناعي"),
}

/** خرائط بلا مفتاح: OpenStreetMap القياسية (أسماء الشوارع والمدن بالعربية في عُمان) هي الافتراضية. */
object MapTiles {
    private fun esri(name: String, path: String, maxZ: Int, ext: String = ".png") = object : OnlineTileSourceBase(
        name, 0, maxZ, 256, ext,
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/$path/MapServer/tile/"),
        "Esri, HERE, Garmin, OpenStreetMap contributors",
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
    }

    private val streets by lazy { esri("EsriStreet", "World_Street_Map", 19) }
    private val satellite by lazy { esri("EsriImagery", "World_Imagery", 19, ".jpg") }

    private fun source(s: MapStyle): ITileSource = when (s) {
        MapStyle.clear -> TileSourceFactory.MAPNIK
        MapStyle.streets -> streets
        MapStyle.satellite -> satellite
    }

    private const val PREFS = "jaynak.map"
    var style by mutableStateOf(load())
        private set

    private fun load(): MapStyle = runCatching {
        MapStyle.valueOf(
            Boot.app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("style", MapStyle.clear.name)!!)
    }.getOrDefault(MapStyle.clear)

    fun choose(s: MapStyle) {
        style = s
        runCatching { Boot.app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("style", s.name).apply() }
    }

    /** حدود سلطنة عُمان وما حولها: الخريطة لا تخرج عنها فتبقى عُمان هي المحور دائماً. */
    private val oman = BoundingBox(27.2, 60.9, 16.0, 51.6)

    fun apply(map: MapView, dark: Boolean) {
        val src = source(style)
        if (map.tileProvider.tileSource.name() != src.name()) map.setTileSource(src)
        map.overlayManager.tilesOverlay.setColorFilter(null)
        map.isTilesScaledToDpi = true
        map.setTilesScaleFactor(1.2f)           // أسماء وشوارع أكبر وأوضح
        map.setScrollableAreaLimitDouble(oman)
    }
}
