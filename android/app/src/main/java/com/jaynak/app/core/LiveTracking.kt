package com.jaynak.app.core

import org.json.JSONArray
import org.json.JSONObject

/** تتبّع حيّ: موقع المندوب الحقيقي، المسار على الطرق، المسافة والوقت. */
object LiveTracking {

    data class Loc(val coord: Coord, val heading: Float?, val speed: Double, val stale: Boolean)

    data class Track(
        val status: String,
        val driverName: String?,
        val driverPhone: String?,
        val loc: Loc?,
        val route: List<Coord>,
        val distanceKm: Double?,
        val etaMin: Int?,
        val target: String?,
        val statusLine: String,
    )

    data class FleetDriver(val id: String, val name: String, val phone: String, val loc: Loc?)

    data class FleetOrder(
        val id: String,
        val ref: String,
        val status: String,
        val pickup: Place,
        val dropoff: Place,
        val customerPhone: String,
        val recipientPhone: String,
        val total: Double,
        val driver: FleetDriver?,
        val target: String?,
        val distanceKm: Double?,
        val etaMin: Int?,
        val statusLine: String,
    )

    data class Fleet(val orders: List<FleetOrder>, val idle: List<FleetDriver>)

    suspend fun track(remoteID: String): Track {
        val j = OrdersService.send("api/orders/$remoteID/track", "GET", null)
        val d = j.optJSONObject("driver")
        return Track(
            status = j.optString("status"),
            driverName = d?.optString("name"),
            driverPhone = d?.optString("phone"),
            loc = d?.optJSONObject("loc")?.let(::loc),
            route = path(j.optJSONArray("route")),
            distanceKm = if (j.isNull("distanceKm")) null else j.optDouble("distanceKm"),
            etaMin = if (j.isNull("etaMin")) null else j.optInt("etaMin"),
            target = j.str("target"),
            statusLine = j.optString("statusLine"),
        )
    }

    suspend fun fleet(): Fleet {
        val j = OrdersService.send("api/admin/fleet", "GET", null)
        val orders = j.optJSONArray("orders") ?: JSONArray()
        val idle = j.optJSONArray("idleDrivers") ?: JSONArray()
        return Fleet(
            orders = (0 until orders.length()).mapNotNull { runCatching { order(orders.getJSONObject(it)) }.getOrNull() },
            idle = (0 until idle.length()).mapNotNull { runCatching { driver(idle.getJSONObject(it)) }.getOrNull() },
        )
    }

    /** يُستدعى من خدمة موقع المندوب. */
    suspend fun publish(lat: Double, lon: Double, heading: Float, speed: Float) {
        OrdersService.send("api/driver/location", "POST",
            JSONObject().put("lat", lat).put("lon", lon).put("heading", heading.toDouble())
                .put("speed", speed.toDouble()))
    }

    private fun loc(o: JSONObject) = Loc(
        coord = Coord(o.optDouble("lat"), o.optDouble("lon")),
        heading = if (o.isNull("heading")) null else o.optDouble("heading").toFloat(),
        speed = o.optDouble("speed", 0.0),
        stale = o.optBoolean("stale", false),
    )

    private fun driver(o: JSONObject) = FleetDriver(
        id = o.optString("id"), name = o.optString("name"), phone = o.optString("phone"),
        loc = o.optJSONObject("loc")?.let(::loc),
    )

    private fun place(o: JSONObject) = Place(
        name = o.optString("name"),
        zone = o.optString("zone"),
        coord = Coord(o.optDouble("lat"), o.optDouble("lon")),
    )

    private fun order(r: JSONObject): FleetOrder {
        val o = r.getJSONObject("order")
        return FleetOrder(
            id = o.optString("id"), ref = o.optString("ref"), status = o.optString("status"),
            pickup = place(o.getJSONObject("pickup")), dropoff = place(o.getJSONObject("dropoff")),
            customerPhone = o.optString("customerPhone"),
            recipientPhone = o.optString("recipientPhone"),
            total = o.optJSONObject("price")?.optDouble("total", 0.0) ?: 0.0,
            driver = r.optJSONObject("driver")?.let(::driver),
            target = r.str("target"),
            distanceKm = if (r.isNull("distanceKm")) null else r.optDouble("distanceKm"),
            etaMin = if (r.isNull("etaMin")) null else r.optInt("etaMin"),
            statusLine = r.optString("statusLine"),
        )
    }

    private fun path(a: JSONArray?): List<Coord> =
        if (a == null) emptyList()
        else (0 until a.length()).map { val p = a.getJSONArray(it); Coord(p.getDouble(0), p.getDouble(1)) }

    /** اتجاه (درجات من الشمال) من نقطة إلى أخرى. */
    fun bearing(a: Coord, b: Coord): Float {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val y = Math.sin(dl) * Math.cos(p2)
        val x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)
        return ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360).toFloat()
    }

    fun kmText(km: Double?): String = when {
        km == null -> "—"
        km < 1.0 -> "${(km * 1000 / 10).toInt() * 10} م"
        else -> "%.1f كم".format(java.util.Locale.US, km)
    }
}
