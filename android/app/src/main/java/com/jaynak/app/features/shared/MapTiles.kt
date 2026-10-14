package com.jaynak.app.features.shared

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

/** Detailed street map (Esri World Street Map, no API key). Night mode = inverted colors. */
object MapTiles {
    private val street = object : OnlineTileSourceBase(
        "EsriStreet", 0, 19, 256, ".png",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"),
        "Esri, HERE, Garmin, OpenStreetMap contributors",
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
    }

    fun apply(map: MapView, dark: Boolean) {
        if (map.tileProvider.tileSource.name() != street.name()) map.setTileSource(street)
        map.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        map.isTilesScaledToDpi = true
    }
}
