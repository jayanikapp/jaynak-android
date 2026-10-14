package com.jaynak.app.core

import android.content.Context

/**
 * قفل زمني لمحاولات التحقق: بعد عدد معيّن من المحاولات يُقفل الإرسال
 * ٥ دقائق مع عدّاد تنازلي على الشاشة، ويزول القفل تلقائياً بانتهائه.
 */
object SendLock {
    const val LOCK_MS = 5 * 60 * 1000L
    private const val MAX_SENDS = 3
    private const val MAX_WRONG = 5

    private val prefs get() = Boot.app.getSharedPreferences("sendlock", Context.MODE_PRIVATE)

    private var until: Long
        get() = prefs.getLong("until", 0L)
        set(v) { prefs.edit().putLong("until", v).apply() }

    private var sends: Int
        get() = prefs.getInt("sends", 0)
        set(v) { prefs.edit().putInt("sends", v).apply() }

    private var wrong: Int
        get() = prefs.getInt("wrong", 0)
        set(v) { prefs.edit().putInt("wrong", v).apply() }

    private var windowStart: Long
        get() = prefs.getLong("window", 0L)
        set(v) { prefs.edit().putLong("window", v).apply() }

    /** الثواني المتبقية، أو ٠ إن لم يكن هناك قفل (وتُصفَّر العدّادات عند انتهائه). */
    fun remainingSec(): Int {
        val left = until - nowMs()
        if (left <= 0) {
            if (until != 0L) { until = 0L; sends = 0; wrong = 0; windowStart = 0L }
            return 0
        }
        return ((left + 999) / 1000).toInt()
    }

    fun format(sec: Int): String = "%02d:%02d".format(sec / 60, sec % 60)

    fun lock() {
        until = nowMs() + LOCK_MS
        sends = 0
        wrong = 0
    }

    /** يُستدعى قبل كل إرسال؛ يرمي استثناءً إن كان القفل فعّالاً أو بلغت المحاولات حدّها. */
    fun beforeSend() {
        val left = remainingSec()
        if (left > 0) throw OTPService.Failure.RateLimited("الإرسال موقوف مؤقتاً — يُفتح بعد ${format(left)}")
        val now = nowMs()
        if (windowStart == 0L || now - windowStart > LOCK_MS) { windowStart = now; sends = 0 }
        sends += 1
        if (sends > MAX_SENDS) {
            lock()
            throw OTPService.Failure.RateLimited("محاولات كثيرة — يُفتح الإرسال بعد ${format(remainingSec())}")
        }
    }

    fun onWrongCode() {
        wrong += 1
        if (wrong >= MAX_WRONG) lock()
    }

    fun clear() {
        until = 0L; sends = 0; wrong = 0; windowStart = 0L
    }
}
