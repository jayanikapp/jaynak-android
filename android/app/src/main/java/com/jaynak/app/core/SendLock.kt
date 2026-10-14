package com.jaynak.app.core

import android.content.Context

/**
 * نظام القفل الزمني أُلغي بالكامل: لا يُوقَف أي حساب ولا يُقفل الإرسال أبداً.
 * أُبقيت الواجهة كما هي حتى لا تتغيّر بقية الشيفرة، وكل دوالّها لا تفعل شيئاً.
 */
object SendLock {
    const val LOCK_MS = 0L

    /** تنظيف أي قفل قديم محفوظ على الجهاز من إصدارات سابقة. */
    private fun purge() {
        runCatching {
            Boot.app.getSharedPreferences("sendlock", Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    /** دائماً ٠: لا يوجد قفل. */
    fun remainingSec(): Int { purge(); return 0 }

    fun format(sec: Int): String = "%02d:%02d".format(sec / 60, sec % 60)

    fun lock() {}

    fun beforeSend() {}

    fun onWrongCode() {}

    fun clear() { purge() }
}
