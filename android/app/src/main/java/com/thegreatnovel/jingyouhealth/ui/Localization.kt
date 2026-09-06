package com.thegreatnovel.jingyouhealth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.thegreatnovel.jingyouhealth.model.AppLanguage

private data class Translation(val en: String, val fr: String, val ar: String)

private val translations = mapOf(
    "今日" to Translation("Today", "Aujourd’hui", "اليوم"),
    "趋势" to Translation("Trends", "Tendances", "الاتجاهات"),
    "运动" to Translation("Activities", "Activités", "الأنشطة"),
    "教练" to Translation("Coach", "Coach", "المدرب"),
    "设置" to Translation("Settings", "Réglages", "الإعدادات"),
    "早上好" to Translation("Good morning", "Bonjour", "صباح الخير"),
    "下午好" to Translation("Good afternoon", "Bon après-midi", "مساء الخير"),
    "晚上好" to Translation("Good evening", "Bonsoir", "مساء الخير"),
    "今天的身体状态" to Translation("Your body today", "Votre état aujourd’hui", "حالة جسمك اليوم"),
    "恢复状态很好" to Translation("Recovery is strong", "Très bonne récupération", "تعافيك ممتاز"),
    "今天可以按计划训练，身体已经准备好了。" to Translation("You can train as planned today. Your body is ready.", "Vous pouvez vous entraîner comme prévu aujourd’hui. Votre corps est prêt.", "يمكنك التدريب كما هو مخطط اليوم. جسمك مستعد."),
    "恢复状态不错" to Translation("Recovery looks good", "Bonne récupération", "تعافيك جيد"),
    "保持正常节奏，留意训练后的身体反馈。" to Translation("Keep your normal rhythm and notice how your body responds after training.", "Gardez votre rythme habituel et observez vos sensations après l’entraînement.", "حافظ على إيقاعك المعتاد وانتبه لاستجابة جسمك بعد التدريب."),
    "恢复状态一般" to Translation("Recovery is moderate", "Récupération moyenne", "التعافي متوسط"),
    "今天更适合稍微降低训练强度。" to Translation("A slightly lighter training day fits better today.", "Une séance un peu plus légère conviendra mieux aujourd’hui.", "يُفضّل اليوم خفض شدة التدريب قليلًا."),
    "身体需要恢复" to Translation("Your body needs recovery", "Votre corps a besoin de récupérer", "جسمك يحتاج إلى التعافي"),
    "优先睡眠、补水和轻量活动，让身体缓一缓。" to Translation("Prioritize sleep, hydration, and light movement today.", "Privilégiez le sommeil, l’hydratation et une activité légère aujourd’hui.", "أعطِ الأولوية للنوم والترطيب والحركة الخفيفة اليوم."),
    "正在了解你的状态" to Translation("Learning your state", "Analyse de votre état", "جارٍ فهم حالتك"),
    "同步更多数据后，我会把今天最重要的信号放在这里。" to Translation("After more data syncs, your most important signals will appear here.", "Après la synchronisation de davantage de données, vos signaux les plus importants apparaîtront ici.", "بعد مزامنة المزيد من البيانات، ستظهر أهم إشاراتك هنا."),
    "影响恢复" to Translation("Recovery contributors", "Facteurs de récupération", "عوامل التعافي"),
    "今日信号" to Translation("Today’s signals", "Signaux du jour", "إشارات اليوم"),
    "恢复准备度" to Translation("Readiness", "Préparation", "الاستعداد"),
    "昨夜 HRV" to Translation("Last-night HRV", "VFC de la nuit", "HRV الليلة الماضية"),
    "静息心率" to Translation("Resting HR", "FC au repos", "نبض الراحة"),
    "睡眠" to Translation("Sleep", "Sommeil", "النوم"),
    "身体电量" to Translation("Body Battery", "Body Battery", "طاقة الجسم"),
    "压力" to Translation("Stress", "Stress", "الإجهاد"),
    "步数" to Translation("Steps", "Pas", "الخطوات"),
    "最近运动" to Translation("Recent activities", "Activités récentes", "الأنشطة الأخيرة"),
    "查看全部" to Translation("View all", "Tout voir", "عرض الكل"),
    "下拉同步 Garmin 最新数据" to Translation("Pull down to sync latest Garmin data", "Tirez vers le bas pour synchroniser Garmin", "اسحب لأسفل لمزامنة أحدث بيانات Garmin"),
    "正在读取 Garmin" to Translation("Reading Garmin", "Lecture de Garmin", "جارٍ قراءة Garmin"),
    "正在整理今天" to Translation("Organizing today", "Organisation de la journée", "جارٍ ترتيب بيانات اليوم"),
    "已更新" to Translation("Updated", "Mis à jour", "تم التحديث"),
    "同步失败" to Translation("Sync failed", "Échec de synchronisation", "فشلت المزامنة"),
    "过去 30 天" to Translation("Last 30 days", "30 derniers jours", "آخر 30 يومًا"),
    "HRV 趋势" to Translation("HRV trend", "Tendance VFC", "اتجاه HRV"),
    "静息心率趋势" to Translation("Resting HR trend", "FC au repos", "اتجاه نبض الراحة"),
    "睡眠时长" to Translation("Sleep duration", "Durée du sommeil", "مدة النوم"),
    "压力趋势" to Translation("Stress trend", "Tendance du stress", "اتجاه الإجهاد"),
    "暂无数据" to Translation("No data yet", "Pas encore de données", "لا توجد بيانات بعد"),
    "分钟" to Translation("min", "min", "د"),
    "小时" to Translation("h", "h", "س"),
    "公里" to Translation("km", "km", "كم"),
    "训练负荷" to Translation("Training load", "Charge d’entraînement", "حمل التدريب"),
    "平均心率" to Translation("Avg HR", "FC moyenne", "متوسط النبض"),
    "开始新的对话" to Translation("Start a new chat", "Nouvelle conversation", "ابدأ محادثة جديدة"),
    "问问你的身体" to Translation("Ask about your body", "Posez une question sur votre corps", "اسأل عن جسمك"),
    "比如：我今天适合跑 10km 吗？" to Translation("For example: Should I run 10 km today?", "Ex. : est-ce une bonne journée pour courir 10 km ?", "مثال: هل يناسبني الجري 10 كم اليوم؟"),
    "发送" to Translation("Send", "Envoyer", "إرسال"),
    "正在读取睡眠" to Translation("Reading sleep", "Lecture du sommeil", "جارٍ قراءة النوم"),
    "正在比较最近几周" to Translation("Comparing recent weeks", "Comparaison des dernières semaines", "جارٍ مقارنة الأسابيع الأخيرة"),
    "正在形成建议" to Translation("Forming a recommendation", "Préparation de la recommandation", "جارٍ إعداد التوصية"),
    "历史对话" to Translation("Chat history", "Historique", "سجل المحادثات"),
    "外观" to Translation("Appearance", "Apparence", "المظهر"),
    "亮色" to Translation("Light", "Clair", "فاتح"),
    "跟随系统" to Translation("System", "Système", "النظام"),
    "暗色" to Translation("Dark", "Sombre", "داكن"),
    "语言" to Translation("Language", "Langue", "اللغة"),
    "中文" to Translation("Chinese", "Chinois", "الصينية"),
    "英语" to Translation("English", "Anglais", "الإنجليزية"),
    "法语" to Translation("French", "Français", "الفرنسية"),
    "阿拉伯语" to Translation("Arabic", "Arabe", "العربية"),
    "连接状态" to Translation("Connection", "Connexion", "الاتصال"),
    "已连接私人健康服务器" to Translation("Connected to private health server", "Connecté au serveur de santé privé", "متصل بخادم الصحة الخاص"),
    "需要连接电脑" to Translation("Computer connection required", "Connexion à l’ordinateur requise", "يلزم الاتصال بالكمبيوتر"),
    "USB 调试登录" to Translation("USB debug sign-in", "Connexion USB de débogage", "تسجيل دخول تصحيح USB"),
    "连接 JingYou" to Translation("Connect JingYou", "Connecter JingYou", "الاتصال بـ JingYou"),
    "手机负责体验，电脑负责 Garmin、数据库和 Agent。" to Translation("Your phone is the experience; your computer runs Garmin, the database, and the agent.", "Le téléphone porte l’expérience ; l’ordinateur gère Garmin, la base et l’agent.", "الهاتف للواجهة، والكمبيوتر يدير Garmin وقاعدة البيانات والوكيل."),
    "通过浏览器安全登录，JingYou 会自动连接你的健康档案。" to Translation("Sign in securely in your browser and JingYou will connect your health profile automatically.", "Connectez-vous en toute sécurité dans votre navigateur ; JingYou reliera automatiquement votre profil santé.", "سجّل الدخول بأمان عبر المتصفح، وسيقوم JingYou بربط ملفك الصحي تلقائيًا."),
    "正在连接" to Translation("Connecting", "Connexion…", "جارٍ الاتصال"),
    "连接失败" to Translation("Connection failed", "Connexion échouée", "فشل الاتصال"),
)

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.CHINESE }

fun translate(language: AppLanguage, sourceChinese: String): String {
    if (language == AppLanguage.CHINESE) return sourceChinese
    val item = translations[sourceChinese] ?: return sourceChinese
    return when (language) {
        AppLanguage.CHINESE -> sourceChinese
        AppLanguage.ENGLISH -> item.en
        AppLanguage.FRENCH -> item.fr
        AppLanguage.ARABIC -> item.ar
    }
}

@Composable
fun tr(sourceChinese: String): String = translate(LocalAppLanguage.current, sourceChinese)

@Composable
fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalLayoutDirection provides if (language.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}
