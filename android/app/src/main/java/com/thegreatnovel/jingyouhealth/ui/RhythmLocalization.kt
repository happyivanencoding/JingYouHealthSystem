package com.thegreatnovel.jingyouhealth.ui

import com.thegreatnovel.jingyouhealth.model.AppLanguage

private data class RhythmTranslation(
    val en: String,
    val fr: String,
    val ar: String,
)

/** Copy for the compact JingYou Rhythm surface; kept separate from the shared UI dictionaries. */
private val rhythmTranslations = mapOf(
    "上一段28天参考" to RhythmTranslation("Previous 28 days", "28 jours précédents", "الأيام الـ28 السابقة"),
    "比参照高" to RhythmTranslation("Above reference", "Au-dessus du repère", "أعلى من المرجع"),
    "比参照低" to RhythmTranslation("Below reference", "Sous le repère", "أقل من المرجع"),
    "接近参照" to RhythmTranslation("Close to reference", "Proche du repère", "قريب من المرجع"),
    "按记录天数折算" to RhythmTranslation("Adjusted for recorded days", "Ajusté aux jours enregistrés", "معدّل حسب الأيام المسجّلة"),
    "浅色带是参照附近，圆点是当前负荷。" to RhythmTranslation("The pale band surrounds your reference; the dot shows your current load.", "La bande claire entoure votre repère ; le point indique votre charge actuelle.", "يمثل الشريط الفاتح النطاق حول مرجعك، والنقطة حملك الحالي."),
    "28天参照使用再前面的独立28天，和当前28天不重叠。" to RhythmTranslation("The 28-day reference uses the preceding 28 days, with no overlap with the current window.", "Le repère sur 28 jours utilise les 28 jours précédents, sans chevauchement avec la période actuelle.", "يستخدم مرجع 28 يومًا الفترة السابقة من 28 يومًا دون تداخل مع الفترة الحالية."),
    "浅色带表示参照的75%至125%，不是训练安全区。" to RhythmTranslation("The pale band spans 75–125% of the reference; it is not a training safety zone.", "La bande claire couvre 75–125 % du repère ; ce n’est pas une zone de sécurité d’entraînement.", "يمتد الشريط الفاتح من 75 إلى 125٪ من المرجع، وليس منطقة أمان للتدريب."),
    "JingYou 节奏" to RhythmTranslation("JingYou Rhythm", "Rythme JingYou", "إيقاع JingYou"),
    "今天的节奏" to RhythmTranslation("Today's rhythm", "Le rythme d’aujourd’hui", "إيقاع اليوم"),
    "恢复" to RhythmTranslation("Recovery", "Récupération", "التعافي"),
    "近 7 天刺激" to RhythmTranslation("Last 7 days' stimulus", "Stimulation des 7 derniers jours", "تحفيز آخر 7 أيام"),
    "近 28 天积累" to RhythmTranslation("28-day accumulation", "Accumulation sur 28 jours", "تراكم 28 يومًا"),
    "平时每周参考" to RhythmTranslation("Usual weekly reference", "Référence hebdomadaire habituelle", "المرجع الأسبوعي المعتاد"),
    "查看滚动四周" to RhythmTranslation("View rolling four weeks", "Voir les 4 semaines glissantes", "عرض الأسابيع الأربعة المتحركة"),
    "把训练交给 Coach" to RhythmTranslation("Ask Coach about training", "Confier l’entraînement à Coach", "اسأل Coach عن التدريب"),
    "我们的计算方法" to RhythmTranslation("How we calculate", "Notre méthode de calcul", "طريقة حسابنا"),
    "训练方向" to RhythmTranslation("Training direction", "Direction d’entraînement", "اتجاه التدريب"),
    "综合体能" to RhythmTranslation("Overall fitness", "Forme générale", "اللياقة العامة"),
    "耐力表现" to RhythmTranslation("Endurance performance", "Performance d’endurance", "أداء التحمل"),
    "力量与增肌" to RhythmTranslation("Strength & muscle gain", "Force et prise de muscle", "القوة وبناء العضلات"),
    "选择你的训练目标" to RhythmTranslation("Choose your training goal", "Choisissez votre objectif d’entraînement", "اختر هدفك التدريبي"),
    "你的目标会决定训练方向，每个账号单独保存。" to RhythmTranslation(
        "Your goal guides training direction; it is saved separately for each account.",
        "Votre objectif guide la direction ; il est enregistré séparément pour chaque compte.",
        "يحدد هدفك اتجاه التدريب؛ ويُحفظ لكل حساب على حدة.",
    ),
    "今天感觉如何" to RhythmTranslation("How do you feel today?", "Comment vous sentez-vous aujourd’hui ?", "كيف تشعر اليوم؟"),
    "精神充足" to RhythmTranslation("Energized", "En forme", "أشعر بالنشاط"),
    "感觉一般" to RhythmTranslation("Okay", "Moyen", "أشعر بأنني بخير"),
    "有些疲劳" to RhythmTranslation("A little fatigued", "Un peu fatigué(e)", "أشعر ببعض التعب"),
    "尚未填写" to RhythmTranslation("Not entered yet", "Pas encore renseigné", "لم يُسجّل بعد"),
    "清除今天的感受" to RhythmTranslation("Clear today’s feeling", "Effacer le ressenti du jour", "مسح شعور اليوم"),
    "今天先轻松一点" to RhythmTranslation("Keep it easy today", "Allez-y doucement aujourd’hui", "خفّف اليوم"),
    "先巩固这一周" to RhythmTranslation("Consolidate this week first", "Consolidez d’abord cette semaine", "ثبّت هذا الأسبوع أولًا"),
    "可以稳步积累" to RhythmTranslation("Build steadily", "Accumulez progressivement", "يمكنك التراكم تدريجيًا"),
    "给力量留个位置" to RhythmTranslation("Make room for strength", "Gardez une place pour la force", "اترك مساحة لتدريب القوة"),
    "把有氧基础补起来" to RhythmTranslation("Build your aerobic base", "Renforcez votre base aérobie", "ابنِ قاعدتك الهوائية"),
    "先积累一些记录" to RhythmTranslation("Build up some records first", "Accumulez d’abord quelques relevés", "اجمع بعض السجلات أولًا"),
    "轻松有氧" to RhythmTranslation("Easy aerobic", "Aérobie facile", "تمارين هوائية خفيفة"),
    "适量力量" to RhythmTranslation("Moderate strength", "Force modérée", "قوة باعتدال"),
    "保持平常节奏" to RhythmTranslation("Keep your usual rhythm", "Gardez votre rythme habituel", "حافظ على إيقاعك المعتاد"),
    "先恢复，再安排训练" to RhythmTranslation("Recover first, then plan training", "Récupérez d’abord, puis planifiez", "تعافَ أولًا ثم خطط للتدريب"),
    "记录的负荷正在上升" to RhythmTranslation("Recorded load is rising", "La charge enregistrée augmente", "الحمل المسجّل يرتفع"),
    "低于近期训练习惯" to RhythmTranslation("Below your recent training pattern", "Sous votre habitude récente", "أقل من نمطك التدريبي الأخير"),
    "接近近期训练习惯" to RhythmTranslation("Close to your recent training pattern", "Proche de votre habitude récente", "قريب من نمطك التدريبي الأخير"),
    "正在建立负荷参考" to RhythmTranslation("Building your load reference", "Référence de charge en construction", "جارٍ بناء مرجع الحمل"),
    "记录还不够完整" to RhythmTranslation("Records are not complete yet", "Les relevés sont encore incomplets", "السجلات غير مكتملة بعد"),
    "这次先以轻松活动为主" to RhythmTranslation("Keep this one easy", "Privilégiez une activité facile cette fois", "اجعل نشاطك هذه المرة خفيفًا"),
    "近几天高强度较密集" to RhythmTranslation("High-intensity sessions have been close together", "Les séances intenses ont été rapprochées ces derniers jours", "الجلسات عالية الشدة متقاربة في الأيام الأخيرة"),
    "睡眠偏短，先照顾恢复" to RhythmTranslation("Sleep has been short; prioritize recovery", "Le sommeil est court ; donnez la priorité à la récupération", "النوم قصير؛ أعطِ الأولوية للتعافي"),
    "恢复信号偏低" to RhythmTranslation("Recovery signals are low", "Les signaux de récupération sont faibles", "إشارات التعافي منخفضة"),
    "你今天感觉疲劳" to RhythmTranslation("You feel tired today", "Vous vous sentez fatigué(e) aujourd’hui", "تشعر بالتعب اليوم"),
    "近期力量训练较少" to RhythmTranslation("Few recent strength sessions", "Peu de séances de force récemment", "جلسات القوة قليلة مؤخرًا"),
    "近期有氧活动较少" to RhythmTranslation("Little recent aerobic activity", "Peu d’activité aérobie récemment", "النشاط الهوائي قليل مؤخرًا"),
    "最近刚做过力量训练" to RhythmTranslation("You trained strength recently", "Vous avez fait de la force récemment", "أجريت تدريب قوة مؤخرًا"),
    "方向随你的目标调整" to RhythmTranslation("Direction follows your goal", "La direction s’adapte à votre objectif", "يتكيف الاتجاه مع هدفك"),
    "负荷主要来自估算" to RhythmTranslation("Load is mostly estimated", "La charge est surtout estimée", "الحمل تقديري في الغالب"),
    "部分负荷已由你自评" to RhythmTranslation("Some load uses your own rating", "Une partie de la charge vient de votre auto-évaluation", "جزء من الحمل مبني على تقييمك"),
    "负荷来自你的用力感受" to RhythmTranslation("Load comes from your perceived effort", "La charge vient de votre effort perçu", "الحمل مبني على إحساسك بالجهد"),
    "继续补充训练后的用力程度，会让参考更贴近你。" to RhythmTranslation(
        "Add your post-training effort ratings to make the reference more personal.",
        "Continuez à noter votre effort après l’entraînement pour affiner votre référence.",
        "واصل تسجيل جهدك بعد التدريب ليصبح المرجع أقرب إليك.",
    ),
    "已记录" to RhythmTranslation("Recorded", "Enregistré", "مسجّل"),
    "天" to RhythmTranslation("days", "jours", "يوم"),
    "相比平时" to RhythmTranslation("Compared with usual", "Par rapport à l’habitude", "مقارنة بالمعتاد"),
    "训练量较少不等于训练不足，训练量较高也不能单独诊断过度训练。" to RhythmTranslation(
        "Less training does not mean undertraining; more training alone cannot diagnose overtraining.",
        "Moins d’entraînement ne signifie pas sous-entraînement ; plus d’entraînement ne suffit pas à diagnostiquer le surentraînement.",
        "قلة التدريب لا تعني نقص التدريب؛ وكثرة التدريب وحدها لا تشخّص فرط التدريب.",
    ),
    "恢复看你的睡眠、HRV和静息心率等个人信号；刺激看最近7天，积累看最近28天。" to RhythmTranslation(
        "Recovery uses personal signals such as sleep, HRV, and resting HR; stimulus uses the last 7 days, accumulation the last 28.",
        "La récupération utilise vos signaux personnels, comme le sommeil, la VFC et la FC au repos ; la stimulation couvre les 7 derniers jours, l’accumulation les 28 derniers.",
        "يعتمد التعافي على إشاراتك الشخصية مثل النوم وHRV ونبض الراحة؛ ويغطي التحفيز آخر 7 أيام والتراكم آخر 28 يومًا.",
    ),
    "AU = 运动分钟 × 用力程度；未自评时使用类别估算，并标记来源。" to RhythmTranslation(
        "AU = exercise minutes × perceived effort; category estimates are used when unrated and the source is marked.",
        "UA = minutes d’exercice × effort perçu ; sans auto-évaluation, une estimation par catégorie est utilisée et la source est indiquée.",
        "AU = دقائق التمرين × الجهد المُدرَك؛ عند غياب التقييم يُستخدم تقدير الفئة ويُشار إلى مصدره.",
    ),
    "最近7天与更早独立的28天每周参考比较，避免把当前这周重复放进参考。" to RhythmTranslation(
        "Compare the last 7 days with an earlier independent 28-day weekly reference, without counting the current week twice.",
        "Comparez les 7 derniers jours à une référence hebdomadaire indépendante des 28 jours précédents, sans compter deux fois la semaine actuelle.",
        "قارن آخر 7 أيام بمرجع أسبوعي مستقل من الـ28 يومًا السابقة، من دون احتساب الأسبوع الحالي مرتين.",
    ),
    "当参考缺少记录时，不把缺失当作休息日。" to RhythmTranslation(
        "When reference coverage is missing, do not treat it as rest.",
        "En cas de relevés manquants, ne les assimilez pas à du repos.",
        "عند نقص السجلات، لا تعتبرها أيام راحة.",
    ),
    "有氧和力量分别看训练天数与最近安排，不机械比较两类AU占比。" to RhythmTranslation(
        "Review aerobic and strength days and recent scheduling separately; do not mechanically compare their AU shares.",
        "Examinez séparément les jours et la programmation récents de l’aérobie et de la force ; ne comparez pas mécaniquement leurs parts d’UA.",
        "راجع أيام وبرمجة الهوائي والقوة كلٌّ على حدة؛ لا تقارن حصص AU بينهما آليًا.",
    ),
    "恢复优先，再看负荷变化、最近强度、你的感受和目标。" to RhythmTranslation(
        "Prioritize recovery, then consider load changes, recent intensity, how you feel, and your goal.",
        "Priorisez la récupération, puis examinez l’évolution de la charge, l’intensité récente, votre ressenti et votre objectif.",
        "أعطِ الأولوية للتعافي، ثم راجع تغير الحمل والشدة الأخيرة وشعورك وهدفك.",
    ),
    "这些是可检查的初始规则，会随方法版本更新；不是临床验证的统一量表。" to RhythmTranslation(
        "These are checkable starting rules that may change with the method version; they are not a clinically validated universal scale.",
        "Ce sont des règles initiales vérifiables, susceptibles d’évoluer avec la méthode ; ce n’est pas une échelle universelle validée cliniquement.",
        "هذه قواعد أولية قابلة للمراجعة وقد تتغير مع إصدار المنهج؛ وليست مقياسًا موحدًا مثبتًا سريريًا.",
    ),
    "查看更多组成" to RhythmTranslation("View more components", "Voir plus de composantes", "عرض مزيد من المكونات"),
    "根据我的恢复、短长期负荷和综合训练目标，今天应如何安排？" to RhythmTranslation(
        "Based on my recovery, short- and long-term load, and overall training goal, how should I plan today?",
        "Selon ma récupération, mes charges à court et long terme et mon objectif global, comment organiser ma journée ?",
        "بناءً على تعافيّ وحملي القصير والطويل الأمد وهدفي التدريبي العام، كيف أنظم يومي؟",
    ),
    "近7天 / 平时一周" to RhythmTranslation("Last 7 days / usual week", "7 derniers jours / semaine habituelle", "آخر 7 أيام / أسبوع معتاد"),
    "方法与依据" to RhythmTranslation("Method and evidence", "Méthode et fondements", "المنهج والأساس"),
    "有氧训练" to RhythmTranslation("Aerobic training", "Entraînement aérobie", "التدريب الهوائي"),
    "近 7 天训练分布" to RhythmTranslation("Last 7 days' training distribution", "Répartition de l’entraînement sur 7 jours", "توزيع التدريب خلال آخر 7 أيام"),
    "平时每周参考 = 此前28天记录的AU ÷ 有记录天数 × 7。" to RhythmTranslation(
        "Usual weekly reference = AU recorded in the previous 28 days ÷ recorded days × 7.",
        "Référence hebdomadaire habituelle = UA des 28 jours précédents ÷ jours enregistrés × 7.",
        "المرجع الأسبوعي المعتاد = AU المسجّلة خلال الـ28 يومًا السابقة ÷ الأيام المسجّلة × 7.",
    ),
    "超过平时25%或低于平时25%，只标记训练习惯的变化，不是安全线。" to RhythmTranslation(
        "More than 25% above or below usual only marks a change in training pattern; it is not a safety line.",
        "Plus de 25 % au-dessus ou au-dessous de l’habitude signale seulement un changement de rythme ; ce n’est pas une limite de sécurité.",
        "تجاوز المعتاد بأكثر من 25٪ أو الانخفاض عنه بأكثر من 25٪ يعلّم تغير نمط التدريب فقط؛ وليس خط أمان.",
    ),
    "综合体能以每周两天力量、两天有氧作为初始安排参考；同一天不会重复计数。" to RhythmTranslation(
        "Overall fitness starts with two strength and two aerobic days per week; the same day is counted once.",
        "Pour la forme générale, on part de deux jours de force et deux jours d’aérobie par semaine ; une même journée n’est comptée qu’une fois.",
        "تبدأ اللياقة العامة بمرجع يومين للقوة ويومين للهوائي أسبوعيًا؛ ولا يُحتسب اليوم نفسه مرتين.",
    ),
    "根据我的恢复、短长期负荷和当前训练目标，今天应如何安排？" to RhythmTranslation(
        "Based on my recovery, short- and long-term load, and current training goal, how should I plan today?",
        "Selon ma récupération, mes charges à court et long terme et mon objectif actuel, comment organiser ma journée ?",
        "بناءً على تعافيّ وحملي القصير والطويل الأمد وهدفي التدريبي الحالي، كيف أنظم يومي؟",
    ),
)

internal fun translateRhythm(language: AppLanguage, source: String): String? {
    if (language == AppLanguage.CHINESE) return source
    val translation = rhythmTranslations[source] ?: return null
    return when (language) {
        AppLanguage.CHINESE -> source
        AppLanguage.ENGLISH -> translation.en
        AppLanguage.FRENCH -> translation.fr
        AppLanguage.ARABIC -> translation.ar
    }
}
