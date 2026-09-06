package com.thegreatnovel.jingyouhealth.ui

import com.thegreatnovel.jingyouhealth.model.AppLanguage

private data class RollingLoadTranslation(
    val en: String,
    val fr: String,
    val ar: String,
)

private val rollingLoadTranslations = mapOf(
    "滚动训练负荷" to RollingLoadTranslation("Rolling training load", "Charge d’entraînement glissante", "حمل التدريب المتحرك"),
    "每日负荷" to RollingLoadTranslation("Daily load", "Par jour", "الحمل اليومي"),
    "暂无完整窗口" to RollingLoadTranslation("No complete window yet", "Fenêtre complète indisponible", "لا توجد نافذة مكتملة بعد"),
    "节奏的计算方法" to RollingLoadTranslation("How Rhythm is calculated", "Comment le rythme est calculé", "كيف يُحسب الإيقاع"),
    "平时一周参考" to RollingLoadTranslation("Usual weekly reference", "Référence hebdomadaire habituelle", "المرجع الأسبوعي المعتاد"),
    "上一段28天参考" to RollingLoadTranslation("Previous 28-day reference", "Référence des 28 jours précédents", "مرجع الـ28 يومًا السابقة"),
    "7天滚动" to RollingLoadTranslation("7-day load", "Charge 7 j", "حمل 7 أيام"),
    "28天滚动" to RollingLoadTranslation("28-day load", "Charge 28 j", "حمل 28 يومًا"),
    "7 天滚动" to RollingLoadTranslation("Rolling 7 days", "Glissant sur 7 jours", "متحرك لـ7 أيام"),
    "28 天滚动" to RollingLoadTranslation("Rolling 28 days", "Glissant sur 28 jours", "متحرك لـ28 يومًا"),
    "参考" to RollingLoadTranslation("Reference", "Référence", "المرجع"),
    "缺少完整覆盖时只显示已记录值，不补造为零；虚线只是个人参考。" to RollingLoadTranslation(
        "With incomplete coverage, show recorded values only; do not fill gaps with zero. The dashed line is a personal reference.",
        "Avec une couverture incomplète, affichez seulement les valeurs enregistrées ; ne remplissez pas les manques par zéro. La ligne pointillée est une référence personnelle.",
        "عند عدم اكتمال التغطية، اعرض القيم المسجلة فقط؛ لا تملأ الفجوات بصفر. الخط المتقطع مرجع شخصي.",
    ),
)

internal fun translateRollingLoad(language: AppLanguage, source: String): String? {
    if (language == AppLanguage.CHINESE) return source
    val translation = rollingLoadTranslations[source] ?: return null
    return when (language) {
        AppLanguage.CHINESE -> source
        AppLanguage.ENGLISH -> translation.en
        AppLanguage.FRENCH -> translation.fr
        AppLanguage.ARABIC -> translation.ar
    }
}
