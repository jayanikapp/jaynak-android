package com.jaynak.app.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random


class DemoStore(private val store: SecureStore) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val serializer = ListSerializer(Order.serializer())

    private fun read(): MutableList<Order> =
        runCatching { json.decodeFromString(serializer, store.demoOrders).toMutableList() }
            .getOrElse { mutableListOf() }

    private fun write(list: List<Order>) {
        store.demoOrders = json.encodeToString(serializer, list)
    }

    private fun newRef(): String {
        val a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return "JN-" + (1..5).map { a[Random.nextInt(a.length)] }.joinToString("")
    }

    private val steps = listOf("searching", "accepted", "pickedUp", "delivered")

    fun create(
        pickup: Point, dropoff: Point, kind: String,
        payment: String, dispatchMode: String, note: String,
    ): Order {
        val price = Demo.quote(pickup, dropoff, kind)
        val o = Order(
            id = "demo-" + System.currentTimeMillis(),
            ref = newRef(),
            kind = kind,
            status = "searching",
            pickup = pickup.copy(zone = pickup.zone.ifBlank { Demo.zoneFor(pickup.lat, pickup.lon) }),
            dropoff = dropoff.copy(zone = dropoff.zone.ifBlank { Demo.zoneFor(dropoff.lat, dropoff.lon) }),
            price = price,
            payment = payment,
            note = note,
            createdAt = System.currentTimeMillis() / 1000.0,
            steps = steps,
            stepIndex = 0,
        )
        write(listOf(o) + read())
        return o
    }

    fun all(): List<Order> = read()

    fun one(id: String): Order =
        read().firstOrNull { it.id == id }
            ?: throw ApiError("not_found", "الطلب غير موجود")

    fun cancel(id: String) = mutate(id) { it.copy(status = "cancelled", stepIndex = -1) }

    fun accept(id: String) = mutate(id) {
        it.copy(status = "accepted", stepIndex = 1, driverID = "demo-d",
            driver = DriverPublic("حساب تجربة", "96890000000", "بوشر", 4.8, 18))
    }

    fun decline(id: String) = mutate(id) { it.copy(driverID = "declined") }

    fun advance(id: String) = mutate(id) {
        val next = (it.stepIndex + 1).coerceAtMost(steps.lastIndex)
        it.copy(status = steps[next], stepIndex = next)
    }

    private inline fun mutate(id: String, f: (Order) -> Order) {
        val list = read()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) throw ApiError("not_found", "الطلب غير موجود")
        list[i] = f(list[i])
        write(list)
    }


    private var onlineFlag: Boolean
        get() = store.demoOnline
        set(v) { store.demoOnline = v }

    fun setPresence(online: Boolean) { onlineFlag = online }

    fun feed(): DriverFeed {
        val list = read()
        val active = list.firstOrNull { it.status == "accepted" || it.status == "pickedUp" }
        val offers = if (active == null && onlineFlag) {
            list.filter { it.status == "searching" && it.driverID == null }
                .map { it.copy(km = it.price.km, onPath = false) }
        } else emptyList()

        return DriverFeed(
            driver = Driver("demo-d", "حساب تجربة", "approved",
                if (onlineFlag) "online" else "offline", 3.200, 18),
            active = active,
            offers = offers,
            board = emptyList(),
            destination = null,
            acceptRadiusKm = 3.0,
            tier = Demo.tier(),
        )
    }


    fun admin(): AdminOverview {
        val list = read()
        val live = list.filter { it.status in setOf("searching", "accepted", "pickedUp") }
        val done = list.filter { it.status == "delivered" }
        return AdminOverview(
            stats = AdminStats(
                ordersToday = list.size,
                revenueToday = round3(done.sumOf { it.price.total }),
                commissionToday = round3(done.sumOf { it.price.commission }),
                live = live.size,
                drivers = 1,
                online = if (onlineFlag) 1 else 0,
                pending = 0,
                customers = 1,
            ),
            live = live,
            drivers = listOf(
                AdminDriver("demo-d", "حساب تجربة", "96890000000", "بوشر",
                    "approved", if (onlineFlag) "online" else "offline", 3.200, 18),
            ),
            rules = Rules(
                Demo.BASE_FARE, Demo.PER_KM, Demo.PER_MINUTE, Demo.MINIMUM_FARE,
                Demo.FIXED_COMMISSION, Demo.MIN_EARNINGS_BALANCE,
            ),
            nextPayout = "—",
        )
    }

    private fun round3(v: Double) = Math.round(v * 1000) / 1000.0
}
