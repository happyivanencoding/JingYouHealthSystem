package com.thegreatnovel.jingyouhealth.ui

import com.thegreatnovel.jingyouhealth.model.AppLanguage

private data class CalendarTranslation(val en: String, val fr: String, val ar: String)

private val calendarTranslations = mapOf(
    "活动日历" to CalendarTranslation("Activity calendar", "Calendrier des activités", "تقويم الأنشطة"),
    "未记录运动" to CalendarTranslation("No activity recorded", "Aucune activité enregistrée", "لم يُسجّل أي نشاط"),
    "查看这天结束的四周" to CalendarTranslation("View the four weeks ending on this day", "Voir les quatre semaines se terminant ce jour-là", "عرض الأسابيع الأربعة المنتهية في هذا اليوم"),
    "本月活动" to CalendarTranslation("This month’s activity", "Activités du mois", "نشاط هذا الشهر"),
    "活动总数" to CalendarTranslation("Total activities", "Total des activités", "إجمالي الأنشطة"),
    "周一" to CalendarTranslation("Mon", "Lun", "الاثنين"),
    "周二" to CalendarTranslation("Tue", "Mar", "الثلاثاء"),
    "周三" to CalendarTranslation("Wed", "Mer", "الأربعاء"),
    "周四" to CalendarTranslation("Thu", "Jeu", "الخميس"),
    "周五" to CalendarTranslation("Fri", "Ven", "الجمعة"),
    "周六" to CalendarTranslation("Sat", "Sam", "السبت"),
    "周日" to CalendarTranslation("Sun", "Dim", "الأحد"),
    "上一月" to CalendarTranslation("Previous month", "Mois précédent", "الشهر السابق"),
    "下一月" to CalendarTranslation("Next month", "Mois suivant", "الشهر التالي"),
)

internal fun translateActivityCalendar(language: AppLanguage, source: String): String? {
    if (language == AppLanguage.CHINESE) return source
    val translation = calendarTranslations[source] ?: return null
    return when (language) {
        AppLanguage.CHINESE -> source
        AppLanguage.ENGLISH -> translation.en
        AppLanguage.FRENCH -> translation.fr
        AppLanguage.ARABIC -> translation.ar
    }
}
