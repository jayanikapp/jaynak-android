package com.jaynak.app.core

import android.text.format.DateUtils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** تنسيق التواريخ بالعربية مع أرقام لاتينية (مثل بقية التطبيق). */
object Fmt {
    val locale: Locale = Locale.forLanguageTag("ar-OM-u-nu-latn")

    fun timeShort(ms: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(ms))

    /** day + month + hour + minute — مقابل .formatted(.dateTime.day().month().hour().minute()) */
    fun dayMonthTime(ms: Long): String =
        SimpleDateFormat("d MMM، h:mm a", locale).format(Date(ms))

    fun dayMonth(ms: Long): String = SimpleDateFormat("d MMM", locale).format(Date(ms))

    fun monthYear(ms: Long): String = SimpleDateFormat("MMMM yyyy", locale).format(Date(ms))

    fun dayMonthYear(ms: Long): String = SimpleDateFormat("d MMM yyyy", locale).format(Date(ms))

    fun dateShort(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(ms))

    fun dateTimeShort(ms: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(ms))

    fun weekdayDayMonth(ms: Long): String = SimpleDateFormat("EEEE d MMMM", locale).format(Date(ms))

    /** مقابل .relative(presentation: .named) — «قبل ٣ دقائق» */
    fun relative(ms: Long): String =
        DateUtils.getRelativeTimeSpanString(ms, nowMs(), DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}
