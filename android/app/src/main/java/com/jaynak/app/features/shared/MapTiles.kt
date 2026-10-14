package com.jaynak.app.features.shared

import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.MapView

/** خرائط واضحة بدقة عالية (CARTO Voyager للنهار و Dark Matter لليل) بدل OSM الخام. */
object MapTiles {
    private fun src(name: String, path: String) = XYTileSource(
        name, 0, 20, 256, "@2x.png",
        arrayOf("a", "b", "c", "d").map { "https://$it.basemaps.cartocdn.com/rastertiles/$path/" }.toTypedArray(),
        "© OpenStreetMap contributors © CARTO",
    )

    private val light = src("CartoVoyager2x", "voyager")
    private val night = src("CartoDark2x", "dark_all")

    fun apply(map: MapView, dark: Boolean) {
        val s = if (dark) night else light
        if (map.tileProvider.tileSource.name() != s.name()) map.setTileSource(s)
        map.overlayManager.tilesOverlay.setColorFilter(null)
        map.isTilesScaledToDpi = true
    }
}
