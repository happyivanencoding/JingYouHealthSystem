package com.thegreatnovel.jingyouhealth.ui

import com.thegreatnovel.jingyouhealth.model.AppLanguage

private data class ActivityTranslation(
    val en: String,
    val fr: String,
    val ar: String,
)

/** Activity-only strings kept separate so the root localization table stays small. */
private val activityTranslations = mapOf(
    "滚动四周" to ActivityTranslation("Scroll through four weeks", "Parcourir quatre semaines", "تصفّح أربعة أسابيع"),
    "左右滑动查看" to ActivityTranslation("Swipe left or right to browse", "Balayez à gauche ou à droite pour parcourir", "اسحب يمينًا أو يسارًا للتصفح"),
    "近四周累计" to ActivityTranslation("Last four weeks", "Total des quatre dernières semaines", "إجمالي الأسابيع الأربعة الأخيرة"),
    "选择结束日期" to ActivityTranslation("Choose end date", "Choisir la date de fin", "اختر تاريخ النهاية"),
    "结束日" to ActivityTranslation("End date", "Date de fin", "تاريخ النهاية"),
    "历史不足四周" to ActivityTranslation("Less than four weeks of history", "Moins de quatre semaines d’historique", "السجل أقل من أربعة أسابيع"),
    "活动" to ActivityTranslation("Activities", "Activités", "الأنشطة"),
    "运动记录" to ActivityTranslation("Activity", "Activité", "النشاط"),
    "按周查看负荷" to ActivityTranslation("Review load by week", "Charge par semaine", "راجع الحمل أسبوعيًا"),
    "按月查看负荷" to ActivityTranslation("Review load by month", "Charge par mois", "راجع الحمل شهريًا"),
    "选择日期" to ActivityTranslation("Choose date", "Choisir une date", "اختر التاريخ"),
    "上一个时段" to ActivityTranslation("Previous period", "Période précédente", "الفترة السابقة"),
    "下一个时段" to ActivityTranslation("Next period", "Période suivante", "الفترة التالية"),
    "周" to ActivityTranslation("Week", "Semaine", "أسبوع"),
    "月" to ActivityTranslation("Month", "Mois", "شهر"),
    "全部" to ActivityTranslation("All", "Toutes", "الكل"),
    "低强度有氧" to ActivityTranslation("Easy aerobic", "Aérobie douce", "هوائي خفيف"),
    "高强度有氧" to ActivityTranslation("Hard aerobic", "Aérobie intense", "هوائي عالي الشدة"),
    "无氧" to ActivityTranslation("Anaerobic", "Anaérobie", "لاهوائي"),
    "力量训练" to ActivityTranslation("Strength", "Renforcement", "تمارين القوة"),
    "周累计" to ActivityTranslation("Week total", "Total de la semaine", "إجمالي الأسبوع"),
    "本月累计" to ActivityTranslation("Month total", "Total du mois", "إجمالي الشهر"),
    "活动次数" to ActivityTranslation("Sessions", "Séances", "الجلسات"),
    "运动小时" to ActivityTranslation("Training hours", "Heures d’entraînement", "ساعات التدريب"),
    "内部负荷" to ActivityTranslation("Internal load", "Charge interne", "الحمل الداخلي"),
    "其中估算" to ActivityTranslation("Estimated", "Estimées", "تقديرية"),
    "内部负荷趋势" to ActivityTranslation("Internal load trend", "Tendance de la charge interne", "اتجاه الحمل الداخلي"),
    "按日累加 AU" to ActivityTranslation("Daily AU accumulation", "Accumulation quotidienne en UA", "تجميع AU اليومي"),
    "当天活动" to ActivityTranslation("Activity that day", "Activité du jour", "نشاط ذلك اليوم"),
    "查看整个时段" to ActivityTranslation("View whole period", "Voir toute la période", "عرض الفترة كاملة"),
    "当天没有活动" to ActivityTranslation("No activity that day", "Aucune activité ce jour-là", "لا يوجد نشاط في ذلك اليوم"),
    "该时段没有活动" to ActivityTranslation("No activity in this period", "Aucune activité sur cette période", "لا يوجد نشاط في هذه الفترة"),
    "换一个日期或筛选条件看看" to ActivityTranslation("Try another date or filter", "Essayez une autre date ou un filtre", "جرّب تاريخًا أو تصفية أخرى"),
    "同步健康档案后，这里会显示真实活动记录" to ActivityTranslation("Real activity records appear here after your health profile syncs", "Les activités réelles apparaîtront après la synchronisation du profil santé", "ستظهر سجلات النشاط الحقيقية بعد مزامنة ملفك الصحي"),
    "日期未知" to ActivityTranslation("Date unknown", "Date inconnue", "تاريخ غير معروف"),
    "估算" to ActivityTranslation("Estimated", "Estimée", "تقديري"),
    "自评" to ActivityTranslation("Reported", "Déclarée", "مُبلّغ عنه"),
    "待自评" to ActivityTranslation("Rate effort", "À évaluer", "قيّم الجهد"),
    "确定" to ActivityTranslation("Done", "Terminer", "تم"),
    "取消" to ActivityTranslation("Cancel", "Annuler", "إلغاء"),
    "主观用力程度" to ActivityTranslation("Perceived effort", "Effort perçu", "الجهد المُدرَك"),
    "未自评，当前为估算" to ActivityTranslation("Not reported; currently estimated", "Non déclarée ; estimation actuelle", "لم تُسجَّل؛ التقدير الحالي"),
    "分类" to ActivityTranslation("Category", "Catégorie", "الفئة"),
    "自动识别" to ActivityTranslation("Automatic", "Automatique", "تلقائي"),
    "清除自评" to ActivityTranslation("Clear rating", "Effacer l’évaluation", "مسح التقييم"),
    "保存中" to ActivityTranslation("Saving", "Enregistrement", "جارٍ الحفظ"),
    "保存自评" to ActivityTranslation("Save rating", "Enregistrer", "حفظ التقييم"),
)

internal fun translateActivity(language: AppLanguage, source: String): String? {
    if (language == AppLanguage.CHINESE) return source
    val translation = activityTranslations[source] ?: return null
    return when (language) {
        AppLanguage.CHINESE -> source
        AppLanguage.ENGLISH -> translation.en
        AppLanguage.FRENCH -> translation.fr
        AppLanguage.ARABIC -> translation.ar
    }
}
