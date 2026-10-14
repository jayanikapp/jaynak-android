package com.jaynak.app.core

import org.json.JSONObject

/** مزامنة الزبائن والمندوبين مع السيرفر ليراهم المالك على أي جوال. */
object DirectorySync {
    data class PCustomer(val phone: String, val name: String, val createdAt: Long, val wallet: Double)
    data class PDriver(
        val phone: String, val name: String, val zone: String, val approval: String,
        val presence: String, val balance: Double, val completed: Int, val civilID: String, val createdAt: Long,
    )
    data class People(val customers: List<PCustomer>, val drivers: List<PDriver>)

    suspend fun push(role: String, name: String, zone: String?, civilID: String?) {
        val b = JSONObject().put("role", role).put("name", name)
        zone?.let { b.put("zone", it) }
        civilID?.let { b.put("civilID", it) }
        OrdersService.send("api/sync/profile", "POST", b)
    }

    suspend fun people(): People {
        val j = OrdersService.send("api/admin/people", "GET", null)
        val cs = j.optJSONArray("customers")
        val ds = j.optJSONArray("drivers")
        val customers = (0 until (cs?.length() ?: 0)).map {
            val o = cs!!.getJSONObject(it)
            PCustomer(o.optString("phone"), o.optString("name"), (o.optDouble("createdAt", 0.0) * 1000).toLong(),
                o.optDouble("walletBalance", 0.0))
        }
        val drivers = (0 until (ds?.length() ?: 0)).map {
            val o = ds!!.getJSONObject(it)
            PDriver(o.optString("phone"), o.optString("name"), o.optString("zone"), o.optString("approval", "pending"),
                o.optString("presence", "offline"), o.optDouble("earningsBalance", 0.0), o.optInt("completedOrders", 0),
                o.optString("civilID"), (o.optDouble("createdAt", 0.0) * 1000).toLong())
        }
        return People(customers, drivers)
    }

    suspend fun setDriverState(phone: String, approval: String?, balance: Double?) {
        val b = JSONObject().put("phone", phone)
        approval?.let { b.put("approval", it) }
        balance?.let { b.put("balance", it) }
        OrdersService.send("api/admin/driver-state", "POST", b)
    }

    /** حالة اعتماد المندوب كما يراها السيرفر (null إن لم يُسجَّل بعد). */
    suspend fun myApproval(): String? {
        val j = OrdersService.send("api/me", "GET", null)
        return j.optJSONObject("driver")?.optString("approval")?.takeIf { it.isNotEmpty() }
    }
}
