package com.jaynak.app.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** بيانات المستخدمين والمرتبات كما يحتفظ بها السيرفر — للإدارة فقط. */
object AdminData {
    data class Rank(val id: String, val kind: String, val title: String, val minPoints: Int)

    data class Person(
        val role: String, val phone: String, val name: String,
        val wallet: Double, val points: Int, val rank: String, val rankId: String?,
        val orders: Int, val approval: String, val zone: String, val presence: String,
    )

    var people by mutableStateOf(listOf<Person>())
    var ranks by mutableStateOf(listOf<Rank>())
    var platform by mutableStateOf(0.0)

    private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k)

    suspend fun refresh() {
        if (!OrdersService.isAvailable) return
        val j = OrdersService.send("api/admin/people", "GET", null)
        val out = ArrayList<Person>()
        val ds = j.optJSONArray("drivers")
        for (i in 0 until (ds?.length() ?: 0)) {
            val o = ds!!.getJSONObject(i)
            out.add(Person("driver", o.s("phone"), o.s("name"), o.optDouble("earningsBalance", 0.0),
                o.optInt("points", 0), o.s("rank"), o.s("rankId").ifEmpty { null },
                o.optInt("completedOrders", 0), o.s("approval"), o.s("zone"), o.s("presence")))
        }
        val cs = j.optJSONArray("customers")
        for (i in 0 until (cs?.length() ?: 0)) {
            val o = cs!!.getJSONObject(i)
            out.add(Person("customer", o.s("phone"), o.s("name"), o.optDouble("walletBalance", 0.0),
                o.optInt("points", 0), o.s("rank"), o.s("rankId").ifEmpty { null },
                o.optInt("ordersCount", 0), "", "", ""))
        }
        val rs = j.optJSONArray("ranks")
        val rl = ArrayList<Rank>()
        for (i in 0 until (rs?.length() ?: 0)) {
            val o = rs!!.getJSONObject(i)
            rl.add(Rank(o.s("id"), o.s("kind"), o.s("title"), o.optInt("minPoints", 0)))
        }
        people = out
        ranks = rl
        platform = j.optDouble("platformWallet", 0.0)
    }

    /** تعديل محفظة/نقاط/رتبة حساب. rankId=null مع setRank=true يعني الرتبة التلقائية. */
    suspend fun adjust(phone: String, role: String, wallet: Double, points: Int,
                       rankId: String? = null, setRank: Boolean = false) {
        val b = JSONObject().put("phone", phone).put("role", role)
            .put("walletDelta", wallet).put("pointsDelta", points)
        if (setRank) b.put("rankId", rankId ?: "")
        OrdersService.send("api/admin/adjust", "POST", b)
        refresh()
    }

    suspend fun saveRank(id: String?, kind: String, title: String, minPoints: Int) {
        val b = JSONObject().put("action", "save").put("kind", kind).put("title", title).put("minPoints", minPoints)
        if (id != null) b.put("id", id)
        OrdersService.send("api/admin/ranks", "POST", b)
        refresh()
    }

    suspend fun deleteRank(id: String) {
        OrdersService.send("api/admin/ranks", "POST", JSONObject().put("action", "delete").put("id", id))
        refresh()
    }
}
