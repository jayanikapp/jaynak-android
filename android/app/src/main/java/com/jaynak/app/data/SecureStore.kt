package com.jaynak.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey


class SecureStore(context: Context) {

    private val plain: SharedPreferences =
        context.getSharedPreferences("jaynak", Context.MODE_PRIVATE)

    private val secure: SharedPreferences? = runCatching {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "jaynak-secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()


    val encrypted: Boolean get() = secure != null

    var token: String?
        get() = (secure ?: plain).getString(KEY_TOKEN, null)
        set(v) = (secure ?: plain).edit().apply {
            if (v == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, v)
        }.apply()

    var phone: String?
        get() = plain.getString(KEY_PHONE, null)
        set(v) = plain.edit().putString(KEY_PHONE, v).apply()

    var serverUrl: String
        get() = plain.getString(KEY_URL, "") ?: ""
        set(v) = plain.edit().putString(KEY_URL, v.trimEnd('/')).apply()


    var demo: Boolean
        get() = plain.getBoolean(KEY_DEMO, false)
        set(v) = plain.edit().putBoolean(KEY_DEMO, v).apply()


    var demoOrders: String
        get() = plain.getString(KEY_DEMO_ORDERS, "[]") ?: "[]"
        set(v) = plain.edit().putString(KEY_DEMO_ORDERS, v).apply()

    var demoOnline: Boolean
        get() = plain.getBoolean(KEY_DEMO_ONLINE, false)
        set(v) = plain.edit().putBoolean(KEY_DEMO_ONLINE, v).apply()

    fun leaveDemo() {
        plain.edit()
            .remove(KEY_DEMO).remove(KEY_DEMO_ORDERS).remove(KEY_DEMO_ONLINE)
            .apply()
    }

    fun signOut() {
        (secure ?: plain).edit().remove(KEY_TOKEN).apply()
        plain.edit().remove(KEY_PHONE).apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_PHONE = "phone"
        const val KEY_URL = "home_url"
        const val KEY_DEMO = "demo"
        const val KEY_DEMO_ORDERS = "demo_orders"
        const val KEY_DEMO_ONLINE = "demo_online"
    }
}
