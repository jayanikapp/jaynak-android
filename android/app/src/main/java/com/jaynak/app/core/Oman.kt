package com.jaynak.app.core

/** التقسيم الإداري لسلطنة عُمان — ١١ محافظة و٦١ ولاية. */
object Oman {

    data class Governorate(val name: String, val wilayat: List<String>) {
        val id: String get() = name
    }

    val governorates: List<Governorate> = listOf(
        Governorate("مسقط",
            listOf("مسقط", "مطرح", "بوشر", "السيب", "العامرات", "قريات")),

        Governorate("ظفار",
            listOf("صلالة", "ثمريت", "طاقة", "مرباط", "سدح", "رخيوت",
                "ضلكوت", "المزيونة", "مقشن", "شليم وجزر الحلانيات")),

        Governorate("مسندم",
            listOf("خصب", "بخاء", "دبا", "مدحاء")),

        Governorate("البريمي",
            listOf("البريمي", "محضة", "السنينة")),

        Governorate("الداخلية",
            listOf("نزوى", "سمائل", "بهلاء", "أدم", "الحمراء",
                "منح", "إزكي", "بدبد")),

        Governorate("شمال الباطنة",
            listOf("صحار", "شناص", "لوى", "صحم", "الخابورة", "السويق")),

        Governorate("جنوب الباطنة",
            listOf("الرستاق", "العوابي", "نخل", "وادي المعاول",
                "بركاء", "المصنعة")),

        Governorate("شمال الشرقية",
            listOf("إبراء", "المضيبي", "بدية", "القابل",
                "وادي بني خالد", "دماء والطائيين")),

        Governorate("جنوب الشرقية",
            listOf("صور", "الكامل والوافي", "جعلان بني بو علي",
                "جعلان بني بو حسن", "مصيرة")),

        Governorate("الظاهرة",
            listOf("عبري", "ينقل", "ضنك")),

        Governorate("الوسطى",
            listOf("هيماء", "محوت", "الدقم", "الجازر")),
    )

    const val defaultGovernorate = "مسقط"
    const val defaultWilaya = "بوشر"

    fun wilayat(governorate: String): List<String> =
        governorates.firstOrNull { it.name == governorate }?.wilayat ?: emptyList()

    /** المحافظة التي تتبعها ولاية ما — تُستخدم لترقية البيانات القديمة. */
    fun governorate(ofWilaya: String): String? =
        governorates.firstOrNull { ofWilaya in it.wilayat }?.name
}
