package com.deniz.eda.ui

import android.Manifest
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * A truthful overview of the permissions declared in AndroidManifest.xml.
 * Runtime permissions are requested one at a time; special access opens Android settings.
 */
class PermissionCenterActivity : AppCompatActivity() {

    private data class PermissionItem(
        val permission: String,
        val title: String,
        val group: String,
        val special: String? = null,
        val note: String = ""
    )

    private val items = listOf(
        PermissionItem(Manifest.permission.RECORD_AUDIO, "Mikrofon", "Ses", note = "Eda'nın sesli komutları ve dinlemesi."),
        PermissionItem("com.termux.permission.RUN_COMMAND", "Termux komut çalıştırma", "Termux", note = "Termux ve gerekli güvenlik ayarlarıyla birlikte çalışır."),
        PermissionItem(Manifest.permission.INTERNET, "İnternet", "Bağlantı", note = "Çevrimiçi servisler için; Android'de normal izindir."),
        PermissionItem(Manifest.permission.ACCESS_NETWORK_STATE, "Ağ durumu", "Bağlantı", note = "Bağlantı durumunu kontrol eder; normal izindir."),
        PermissionItem(Manifest.permission.WRITE_SETTINGS, "Sistem ayarlarını değiştirme", "Özel erişim", "write_settings", "Parlaklık gibi sistem ayarlarını değiştirmek için özel erişim."),
        PermissionItem(Manifest.permission.FOREGROUND_SERVICE, "Ön plan servisi", "Arka plan", note = "Android servis bildirimi; kullanıcıdan ayrı izin penceresi açılmaz."),
        PermissionItem("android.permission.FOREGROUND_SERVICE_MICROPHONE", "Mikrofon ön plan servisi", "Arka plan", note = "Mikrofon servisinin türünü bildirir."),
        PermissionItem("android.permission.FOREGROUND_SERVICE_DATA_SYNC", "Veri eşitleme servisi", "Arka plan", note = "Veri eşitleme servisinin türünü bildirir."),
        PermissionItem("android.permission.FOREGROUND_SERVICE_HEALTH", "Sağlık ön plan servisi", "Sağlık", note = "Sağlık servisi için sistem bildirimi."),
        PermissionItem(Manifest.permission.POST_NOTIFICATIONS, "Bildirimler", "Bildirimler", note = "Android 13 ve üzeri sürümlerde kullanıcı onayı gerekir."),
        PermissionItem(Manifest.permission.RECEIVE_BOOT_COMPLETED, "Telefon açılışını algılama", "Arka plan", note = "Açılış olayını almaya yönelik normal izin."),
        PermissionItem(Manifest.permission.CAMERA, "Kamera / fener", "Cihaz", note = "Kamera ve flaş özellikleri için."),
        PermissionItem(Manifest.permission.ACCESS_FINE_LOCATION, "Kesin konum", "Konum", note = "Yalnızca konum özelliği kullanıldığında gerekli."),
        PermissionItem(Manifest.permission.ACCESS_COARSE_LOCATION, "Yaklaşık konum", "Konum", note = "Yalnızca konum özelliği kullanıldığında gerekli."),
        PermissionItem(Manifest.permission.SCHEDULE_EXACT_ALARM, "Kesin alarm erişimi", "Hatırlatmalar", "exact_alarm", "Android sürümüne göre özel erişim gerekebilir."),
        PermissionItem(Manifest.permission.USE_EXACT_ALARM, "Kesin alarm kullanımı", "Hatırlatmalar", note = "Android ve mağaza politikalarına tabi bir izin."),
        PermissionItem(Manifest.permission.VIBRATE, "Titreşim", "Bildirimler", note = "Normal izin; ayrı kullanıcı onayı gerekmez."),
        PermissionItem(Manifest.permission.SEND_SMS, "SMS gönderme", "SMS", note = "SMS göndermeden önce EDA'nın ayrıca onay akışını koruması gerekir."),
        PermissionItem(Manifest.permission.RECEIVE_SMS, "SMS alma", "SMS", note = "Gelen SMS bildirimini algılamak için."),
        PermissionItem(Manifest.permission.READ_SMS, "SMS okuma", "SMS", note = "Mesaj içeriğine erişen hassas izin."),
        PermissionItem(Manifest.permission.READ_PHONE_STATE, "Telefon durumunu okuma", "Telefon", note = "Arama durumunu algılamak için."),
        PermissionItem(Manifest.permission.READ_CALL_LOG, "Arama geçmişi", "Telefon", note = "Hassas ve Android dağıtım politikalarıyla kısıtlanabilen izin."),
        PermissionItem(Manifest.permission.READ_CONTACTS, "Kişiler", "Telefon", note = "Arayan adını ve kayıtlı kişileri bulmak için."),
        PermissionItem("android.permission.HIGH_SAMPLING_RATE_SENSORS", "Yüksek hızlı sensörler", "Sensörler", note = "Yalnızca ilgili güvenlik/hareket özelliği kullanılıyorsa."),
        PermissionItem("android.permission.health.READ_HEART_RATE", "Kalp atış hızı", "Sağlık", "health", "Health Connect üzerinden yetkilendirilir."),
        PermissionItem("android.permission.health.READ_SLEEP", "Uyku verileri", "Sağlık", "health", "Health Connect üzerinden yetkilendirilir."),
        PermissionItem("android.permission.health.READ_STEPS", "Adım sayısı", "Sağlık", "health", "Health Connect üzerinden yetkilendirilir.")
    )

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "İzin Merkezi"
        window.statusBarColor = android.graphics.Color.rgb(8, 8, 8)
        window.navigationBarColor = android.graphics.Color.rgb(8, 8, 8)
        val scroll = ScrollView(this).apply { setBackgroundColor(android.graphics.Color.rgb(8, 8, 8)) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(text("İZİN MERKEZİ", 24, true))
        root.addView(text("Manifest'te tanımlı 27 izin. Durumlar bu telefonda yeniden kontrol edilir; normal izinler için ayrı onay penceresi açılmaz.", 14, false).apply {
            setTextColor(0xFFBBBBBB.toInt())
        }, marginBottom(14))

        val groups = items.groupBy { it.group }
        groups.forEach { (group, groupItems) ->
            root.addView(text(group, 16, true).apply { setPadding(0, dp(10), 0, dp(6)) })
            groupItems.forEach { item ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0xFF181818.toInt())
                        cornerRadius = dp(14).toFloat()
                        setStroke(dp(1), 0xFF484848.toInt())
                    }
                }
                card.addView(text(item.title, 15, true))
                card.addView(text(item.permission, 11, false).apply { setTextColor(0xFFAAAAAA.toInt()) })
                card.addView(text(item.note, 12, false).apply {
                    setTextColor(0xFFBBBBBB.toInt())
                    setPadding(0, dp(4), 0, dp(4))
                })
                val actionRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                actionRow.addView(text(status(item), 13, true).apply {
                    setTextColor(if (isGranted(item)) 0xFF9BE7A3.toInt() else 0xFFFFD080.toInt())
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val button = Button(this).apply {
                    text = actionLabel(item)
                    setOnClickListener { handle(item) }
                }
                actionRow.addView(button)
                card.addView(actionRow)
                root.addView(card, marginBottom(8))
            }
        }

        root.addView(text("Diğer özel erişimler", 16, true).apply { setPadding(0, dp(14), 0, dp(6)) })
        root.addView(text("Erişilebilirlik ve cihaz yöneticisi, Manifest'teki uses-permission listesine dahil değildir; ayrı Android ekranlarından etkinleştirilir.", 13, false).apply {
            setTextColor(0xFFBBBBBB.toInt())
        }, marginBottom(8))
        root.addView(Button(this).apply {
            text = "Erişilebilirlik ayarlarını aç"
            setOnClickListener { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }, marginBottom(8))
        root.addView(Button(this).apply {
            text = "EDA cihaz yöneticisini aç"
            setOnClickListener {
                val component = ComponentName(this@PermissionCenterActivity, "com.deniz.eda.receiver.EdaDeviceAdminReceiver")
                openSettings(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component).putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "EDA'nın telefon kilitleme özelliği için."))
            }
        }, marginBottom(8))
        root.addView(Button(this).apply {
            text = "Tüm izinleri yeniden kontrol et"
            setOnClickListener { render() }
        })
    }

    private fun isGranted(item: PermissionItem): Boolean {
        return when (item.special) {
            "write_settings" -> Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(this)
            "exact_alarm" -> Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (getSystemService(ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            "health" -> ContextCompat.checkSelfPermission(this, item.permission) == PackageManager.PERMISSION_GRANTED
            else -> ContextCompat.checkSelfPermission(this, item.permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun status(item: PermissionItem): String {
        if (item.permission == "com.termux.permission.RUN_COMMAND") {
            return if (packageManager.getLaunchIntentForPackage("com.termux") != null) "Termux yüklü · izin durumunu Termux'ta kontrol et" else "Termux bulunamadı"
        }
        if (item.special == "health") return if (isGranted(item)) "Verildi" else "Health Connect izni gerekli"
        if (isGranted(item)) return "Verildi"
        return if (item.special != null || isRuntimePermission(item.permission)) "Gerekli / verilmedi" else "Sistem izni"
    }

    private fun actionLabel(item: PermissionItem): String = when {
        isGranted(item) -> "Ayarları aç"
        item.special == "health" -> "Sağlık ayarları"
        item.special != null -> "İzin ver"
        item.permission == "com.termux.permission.RUN_COMMAND" -> "Termux'u aç"
        isRuntimePermission(item.permission) -> "İzin ver"
        else -> "Uygulama ayarları"
    }

    private fun handle(item: PermissionItem) {
        when (item.special) {
            "write_settings" -> openSettings(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            "exact_alarm" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                } else render()
            }
            "health" -> {
                val healthIntent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                if (healthIntent.resolveActivity(packageManager) != null) openSettings(healthIntent)
                else openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            else -> when {
                item.permission == "com.termux.permission.RUN_COMMAND" -> {
                    val launch = packageManager.getLaunchIntentForPackage("com.termux")
                    if (launch != null) openSettings(launch)
                    else openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
                isRuntimePermission(item.permission) && !isGranted(item) -> requestPermissions(arrayOf(item.permission), REQUEST_PERMISSION)
                else -> openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
    }

    private fun isRuntimePermission(permission: String): Boolean {
        return permission in setOf(
            Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_SLEEP",
            "android.permission.health.READ_STEPS"
        )
    }

    private fun openSettings(intent: Intent) {
        try { startActivity(intent) } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
        }
    }

    private fun text(value: String, size: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(0xFFFFFFFF.toInt())
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun marginBottom(dpValue: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(dpValue) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object { private const val REQUEST_PERMISSION = 7401 }
}
