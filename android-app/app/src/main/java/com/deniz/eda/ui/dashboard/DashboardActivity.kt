package com.deniz.eda.ui.dashboard

import com.deniz.eda.core.Settings
import com.deniz.eda.ui.ThemeConfig
import com.deniz.eda.ui.ThemeActivity
import com.deniz.eda.ai.AiRouter
import com.deniz.eda.ai.TermuxDeepChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.content.Intent
import android.speech.RecognizerIntent
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.deniz.eda.service.EdaForegroundService

private val VoidBlack: Color get() = Color(ThemeConfig.current.background)
private val Panel: Color get() = Color(ThemeConfig.current.panel)
private val PanelRaised: Color get() = Color(ThemeConfig.current.raised)
private val Violet: Color get() = Color(ThemeConfig.current.accent)
private val Cyan: Color get() = Color(ThemeConfig.current.secondary)
private val Muted: Color get() = Color(ThemeConfig.current.muted)
private val Good: Color get() = Color(ThemeConfig.current.secondary)
private val Warn: Color get() = Color(ThemeConfig.current.accent)
private val Bad: Color get() = Color(ThemeConfig.current.accent)

class DashboardActivity : ComponentActivity() {
    private var pendingStartAction: String? = null
    private var appliedThemeId: String = ""
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingStartAction
        pendingStartAction = null
        if (granted && action != null) sendAction(action)
        else if (!granted) android.widget.Toast.makeText(this, "Eda için mikrofon izni gerekli.", android.widget.Toast.LENGTH_LONG).show()
    }
    private val phonePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        android.util.Log.i("EDA-Permissions", "Telefon izinleri: $result")
    }
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        ThemeConfig.refresh(this)
        appliedThemeId = ThemeConfig.selectedId(this)
        window.statusBarColor = ThemeConfig.current.background.toInt()
        window.navigationBarColor = ThemeConfig.current.background.toInt()
        viewModel.yenile()
        setContent {
            // Build the Material palette from the currently selected theme.
            // Keeping this dynamic prevents Material3 controls from falling back to violet/cyan
            // when the user selects one of the other 13 themes.
            val palette = ThemeConfig.current
            val themeBackground = Color(palette.background)
            val themePanel = Color(palette.panel)
            val themeRaised = Color(palette.raised)
            val themeAccent = Color(palette.accent)
            val themeSecondary = Color(palette.secondary)
            val themeMuted = Color(palette.muted)
            MaterialTheme(colorScheme = darkColorScheme(
                primary = themeAccent,
                onPrimary = themeBackground,
                primaryContainer = themeRaised,
                onPrimaryContainer = Color.White,
                secondary = themeSecondary,
                onSecondary = themeBackground,
                secondaryContainer = themePanel,
                onSecondaryContainer = Color.White,
                tertiary = themeSecondary,
                background = themeBackground,
                onBackground = Color.White,
                surface = themePanel,
                onSurface = Color.White,
                surfaceVariant = themeRaised,
                onSurfaceVariant = themeMuted,
                outline = themeMuted.copy(alpha = 0.65f),
                error = Color(0xFFFF6B6B),
                onError = Color(0xFF1A0505)
            )) {
                EdaDashboard(
                    viewModel = viewModel,
                    onAction = { action -> sendAction(action) },
                    onShareLog = { com.deniz.eda.utils.EdaLog.share(this) }
                )
            }
        }
        val requiredPhonePermissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        if (requiredPhonePermissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            phonePermissions.launch(requiredPhonePermissions)
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeConfig.refresh(this)
        val latestTheme = ThemeConfig.selectedId(this)
        if (appliedThemeId.isNotEmpty() && latestTheme != appliedThemeId) {
            appliedThemeId = latestTheme
            recreate()
        }
    }

    private fun sendAction(action: String) {
        // Request runtime microphone permission before any action that starts listening.
        if ((action == "ACTION_AKTIF" || action == "ACTION_BASLAT") &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingStartAction = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        when (action) {
            "ACTION_SETTINGS" -> { startActivity(Intent(this, com.deniz.eda.ui.SettingsActivity::class.java)); return }
            "ACTION_AI_GROQ" -> viewModel.setAiProvider("groq")
            "ACTION_AI_POLLINATIONS" -> viewModel.setAiProvider("pollinations")
            "ACTION_AI_OLLAMA" -> viewModel.setAiProvider("ollama")
            else -> {
                val intent = Intent(this, EdaForegroundService::class.java).apply { this.action = action }
                if (action == "ACTION_AKTIF" && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }
        viewModel.yenile()
    }
}

@Composable
private fun EdaDashboard(viewModel: DashboardViewModel, onAction: (String) -> Unit, onShareLog: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var deepChatOpen by remember { mutableStateOf(false) }
    var deepChatInput by remember { mutableStateOf("") }
    var deepChatReply by remember { mutableStateOf("Ollama · Termux üzerinden yerel sohbet.") }
    var deepChatBusy by remember { mutableStateOf(false) }
    var phoneControlOpen by remember { mutableStateOf(false) }
    var phoneScreenText by remember { mutableStateOf("Ekranı okumak için düğmeye bas.") }
    var phoneTarget by remember { mutableStateOf("") }
    var phoneTypedText by remember { mutableStateOf("") }
    var phoneControlStatus by remember { mutableStateOf("") }
    var jarvisOpen by remember { mutableStateOf(false) }
    var jarvisTask by remember { mutableStateOf("") }
    var jarvisReply by remember { mutableStateOf("") }
    var jarvisBusy by remember { mutableStateOf(false) }
    val chatScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val voiceInput = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                if (jarvisOpen) jarvisTask = spoken else deepChatInput = spoken
            }
        }
    }
    // Use observable dashboard state; Compose cannot observe mutable Settings directly.
    val active = state.mod == "AKTIF"
    val sleeping = state.mod == "UYKU"

    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            viewModel.yenile()
        }
    }
    val statusColor = when {
        active -> Good
        sleeping -> Warn
        else -> Bad
    }

    val screenArtworkId = remember(ThemeConfig.current.id) {
        context.resources.getIdentifier("bg_theme_${ThemeConfig.current.id}", "drawable", context.packageName)
    }

    Scaffold(
        // Transparent content lets the selected theme artwork sit behind every dashboard tab.
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Panel, contentColor = Violet) {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    icon = { Text("◈", fontSize = 20.sp) }, label = { Text("Merkez") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    icon = { Text("⌘", fontSize = 20.sp) }, label = { Text("Kontrol") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    icon = { Text("◉", fontSize = 20.sp) }, label = { Text("Sistem") })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(VoidBlack)) {
            if (screenArtworkId != 0) {
                Image(
                    painter = painterResource(id = screenArtworkId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.28f
                )
            }
            // Keep text readable while allowing the theme artwork to remain visible.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            VoidBlack.copy(alpha = 0.28f),
                            VoidBlack.copy(alpha = 0.46f),
                            VoidBlack.copy(alpha = 0.62f)
                        )
                    )
                )
            )
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // EDA monogram: luminous double-tone ring and signature dot.
                Box(
                    Modifier.size(54.dp).background(
                        Brush.linearGradient(listOf(PanelRaised, Panel, Violet.copy(alpha = 0.55f))), CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(2.dp),
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Violet, Cyan)))
                    ) {}
                    Text("E", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
                        .size(6.dp).background(Cyan, CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("EDA", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                    Text("KİŞİSEL ASİSTAN • V5", fontSize = 9.sp, color = Muted, letterSpacing = 1.4.sp)
                }
                Text("◐", modifier = Modifier.clickable { context.startActivity(Intent(context, ThemeActivity::class.java)) }.padding(8.dp), color = Cyan, fontSize = 22.sp)
                Text("⚙", modifier = Modifier.clickable { onAction("ACTION_SETTINGS") }.padding(8.dp), color = Violet, fontSize = 24.sp)
                Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(statusColor, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(if (active) "AKTİF" else if (sleeping) "UYKU" else "KAPALI",
                            color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (selectedTab == 0) {
                HeroPanel(state.kullaniciAdi, state.saat, state.tarih, statusColor, active, sleeping, onAction)
                SectionHeader("CANLI DURUM", "Telefon ve Eda’nın durumu")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("BATARYA", "⚡", "${state.pilYuzde}%", state.pilDurum,
                        if (state.pilYuzde <= 20) Bad else Good, Modifier.weight(1f))
                    MetricCard("BAĞLANTI", "⌁", if (state.internetStatus) "Bağlı" else "Bağlı değil",
                        "Ağ bağlantısı", if (state.internetStatus) Cyan else Warn, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("HAFIZA", "✧", state.hafizaSayi.toString(), "kayıtlı bilgi", Violet, Modifier.weight(1f))
                    MetricCard("HATIRLATICI", "◷", state.hatirlatmaSayi.toString(), "bekleyen hatırlatma", Warn, Modifier.weight(1f))
                }
                SectionHeader("YAPAY ZEKÂ", "Öncelikli sağlayıcı")
                ProviderPanel(state.aiSaglayici, onAction)
                SectionHeader("SON HAREKETLER", "Eda’nın son kayıtları")
                LogsPanel(state.sonLoglar)
            } else if (selectedTab == 1) {
                HeroMini("KONTROL", "Eda’nın çalışma durumunu buradan yönet.")
                SectionHeader("DERİN SOHBET", "Yerel Gemma 2:2B · Termux / Ollama")
                ControlTile("✧", "Derin sohbeti aç", "Uzun sorularını yerel Gemma modeliyle konuş", Violet) {
                    onAction("ACTION_AI_OLLAMA")
                    deepChatOpen = true
                }
                SectionHeader("EDA · SESLİ TELEFON KOMUTLARI", "Ayrı Jarvis veya kontrol ekranı açmadan")
                InfoNotice("📱  Telefonu Eda ile yönet", "Eda aktifken doğrudan söyle: “Eda, aşağı kaydır ve ekrandakini oku”, “Eda, ekranı oku”, “Eda, bildirimleri aç”, “Eda, geri git” veya “Eda, ekrandaki metne dokun, Ayarlar”. Basit komutlar yerel erişilebilirlik üzerinden doğrudan çalışır; normal sohbet ve derin sohbet akışı değişmez.")
                InfoNotice("🔐  İlk kurulum", "Yalnızca bir kez Android Erişilebilirlik ayarlarında EDA hizmetini aç. Kilidi aşma, parola/OTP okuma, mesaj gönderme, silme, ödeme veya son onay işlemleri otomatik yapılmaz.")
                SectionHeader("ÇALIŞMA MODU", "İhtiyacına göre seç")
                ControlTile("▶", "Eda’yı başlat", "Eda’yı dinlemeye hazır hâle getir", Good) { onAction("ACTION_AKTIF") }
                ControlTile("☾", "Uyku moduna al", "Uyandırma kelimesiyle yeniden etkinleşir", Cyan) { onAction("ACTION_UYKU") }
                ControlTile("■", "Eda'yı durdur", "Arka plan servisini kapat", Bad) { onAction("ACTION_STOP") }
                SectionHeader("ÖZEL MODLAR", "Sesli komutla güvenli kontrol")
                InfoNotice("🚘  Araba modu", "Eda'ya “araba modunu aç” diyebilirsin. Bu panel mevcut durumu gösterir.")
                InfoNotice("🛡  Güvenlik modu", "Eda’ya “güvenlik modunu aç” diyerek etkinleştirebilirsin.")
                InfoNotice("🎙  Sesli kullanım", "Eda aktifken “Eda” diye seslenip ardından isteğini söyle.")
            } else {
                HeroMini("SİSTEM DURUMU", "Bağlantıları ve kayıtlarını buradan kontrol et.")
                SectionHeader("YAPAY ZEKÂ BAĞLANTILARI", "Son bağlantı kontrolünün sonucu")
                StatusLine("🌐 İnternet", if (state.internetStatus) "Bağlı" else "Bağlı değil", state.internetStatus)
                StatusLine("🦙 Ollama · Termux", state.ollamaStatus,
                    state.ollamaStatus.contains("açık", true) || state.ollamaStatus.contains("acik", true) ||
                        state.ollamaStatus.contains("hazır", true))
                StatusLine("⚡ Groq", state.groqStatus,
                    (state.groqStatus.contains("bağlı", true) || state.groqStatus.contains("bagli", true)) &&
                        !state.groqStatus.contains("yok", true) && !state.groqStatus.contains("hata", true))
                StatusLine("🌸 Pollinations", state.pollinationsStatus,
                    state.pollinationsStatus.contains("açık", true) || state.pollinationsStatus.contains("acik", true) ||
                        state.pollinationsStatus.contains("çalışıyor", true) || state.pollinationsStatus.contains("calisiyor", true))
                SectionHeader("KAYITLARIN", "Bu telefonda saklanan bilgiler")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("HAFIZA", "✧", state.hafizaSayi.toString(), "kayıt", Violet, Modifier.weight(1f))
                    MetricCard("GÜNLÜK", "▤", state.gunlukSayi.toString(), "kayıt", Cyan, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("HATIRLATMA", "◷", state.hatirlatmaSayi.toString(), "bekleyen", Warn, Modifier.weight(1f))
                    MetricCard("LOG", "≋", state.logSayi.toString(), "kayıt", Good, Modifier.weight(1f))
                }
                SectionHeader("ARAÇLAR", "Sistem işlemleri")
                Button(onClick = { viewModel.yenile() }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) { Text("↻   Durumu yeniden kontrol et") }
                Button(onClick = onShareLog, modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) { Text("⇧   Tanılama kayıtlarını paylaş") }
                LogsPanel(state.sonLoglar)
            }
            Text("EDA • YARDIMA HAZIR", Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
                color = Muted, fontSize = 9.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center)
            }
        }
    }

    if (phoneControlOpen) {
        Dialog(onDismissRequest = { phoneControlOpen = false }) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(0.9f), shape = RoundedCornerShape(24.dp),
                color = Panel, border = BorderStroke(1.dp, Cyan.copy(alpha = 0.35f))) {
                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("EDA · PHONE CONTROL", color = Cyan, fontWeight = FontWeight.Bold)
                            Text("Phone Harness esinli · Yerel erişilebilirlik", color = Muted, fontSize = 11.sp)
                        }
                        Text("✕", Modifier.clickable { phoneControlOpen = false }.padding(8.dp), color = Muted)
                    }
                    Text("Bu güçlü bir izindir. Telefon kilidi aşılmaz; gönderme, silme, ödeme ve satın alma işlemleri otomatik yapılmaz.", color = Warn, fontSize = 12.sp)
                    Button(onClick = {
                        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            .onFailure { phoneControlStatus = "Erişilebilirlik ayarları açılamadı." }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Erişilebilirlik ayarlarında EDA'yı etkinleştir") }
                    OutlinedButton(onClick = {
                        phoneControlStatus = if (com.deniz.eda.phone.PhoneControlService.isEnabled()) {
                            "Ekran kontrolü etkin. Şimdi ekranı okuyabilir veya görevi Jarvis'e verebilirsin."
                        } else {
                            "EDA'nın erişilebilirlik izni henüz etkin değil. Ayarlardan EDA hizmetini açıp geri dön."
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Erişilebilirlik iznini kontrol et") }
                    Button(onClick = {
                        phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("read")
                        phoneScreenText = phoneControlStatus
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) { Text("↻ Ekranı oku") }
                    OutlinedButton(onClick = {
                        phoneControlStatus = "Görsel OCR çalışıyor…"
                        chatScope.launch {
                            val visualText = withContext(Dispatchers.IO) {
                                com.deniz.eda.phone.PhoneControlService.run("vision")
                            }
                            phoneControlStatus = "Görsel OCR tamamlandı."
                            phoneScreenText = visualText
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("⌕ Ekranı görsel OCR ile oku") }
                    Text(phoneControlStatus, color = Muted, fontSize = 12.sp)
                    Column(Modifier.weight(1f).fillMaxWidth().background(PanelRaised, RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState()).padding(10.dp)) {
                        Text(phoneScreenText, color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    OutlinedTextField(value = phoneTarget, onValueChange = { phoneTarget = it }, label = { Text("Dokunulacak öğenin metni") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(enabled = phoneTarget.isNotBlank(), onClick = {
                            phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("tap", phoneTarget)
                            phoneScreenText = com.deniz.eda.phone.PhoneControlService.run("read")
                        }, modifier = Modifier.weight(1f)) { Text("Metne dokun") }
                        OutlinedButton(enabled = phoneTarget.isNotBlank(), onClick = {
                            phoneControlStatus = "OCR metin eşleşmesi aranıyor…"
                            chatScope.launch {
                                val (actionResult, visualText) = withContext(Dispatchers.IO) {
                                    val action = com.deniz.eda.phone.PhoneControlService.run("tap_visual", phoneTarget)
                                    delay(1100)
                                    action to com.deniz.eda.phone.PhoneControlService.run("vision")
                                }
                                phoneControlStatus = actionResult
                                phoneScreenText = visualText
                            }
                        }, modifier = Modifier.weight(1f)) { Text("OCR ile dokun") }
                        OutlinedButton(onClick = {
                            phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("up")
                            phoneScreenText = com.deniz.eda.phone.PhoneControlService.run("read")
                        }, modifier = Modifier.weight(1f)) { Text("↑ Yukarı") }
                        OutlinedButton(onClick = {
                            phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("down")
                            phoneScreenText = com.deniz.eda.phone.PhoneControlService.run("read")
                        }, modifier = Modifier.weight(1f)) { Text("↓ Aşağı") }
                    }
                    OutlinedTextField(value = phoneTypedText, onValueChange = { phoneTypedText = it },
                        label = { Text("Etkin alana yazılacak metin") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(enabled = phoneTypedText.isNotBlank(), onClick = {
                            phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("type", phoneTypedText)
                            phoneScreenText = com.deniz.eda.phone.PhoneControlService.run("read")
                        }, modifier = Modifier.weight(1f)) { Text("Yaz") }
                        OutlinedButton(onClick = {
                            phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("back")
                            phoneScreenText = com.deniz.eda.phone.PhoneControlService.run("read")
                        }, modifier = Modifier.weight(1f)) { Text("Geri") }
                        OutlinedButton(onClick = {
                            phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("notifications")
                            phoneScreenText = com.deniz.eda.phone.PhoneControlService.run("read")
                        }, modifier = Modifier.weight(1f)) { Text("Bildirimler") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("home")
                            phoneScreenText = phoneControlStatus
                        }, modifier = Modifier.weight(1f)) { Text("Ana ekran") }
                        OutlinedButton(onClick = {
                            phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("recents")
                            phoneScreenText = phoneControlStatus
                        }, modifier = Modifier.weight(1f)) { Text("Son uygulamalar") }
                        OutlinedButton(onClick = {
                            phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("quick_settings")
                            phoneScreenText = phoneControlStatus
                        }, modifier = Modifier.weight(1f)) { Text("Hızlı ayarlar") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("power_dialog")
                            phoneScreenText = phoneControlStatus
                        }, modifier = Modifier.weight(1f)) { Text("Güç menüsü") }
                        OutlinedButton(onClick = {
                            phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("screenshot")
                            phoneControlStatus += "\n" + com.deniz.eda.phone.PhoneControlService.run("vision")
                            phoneScreenText = phoneControlStatus
                        }, modifier = Modifier.weight(1f)) { Text("Ekran görüntüsü") }
                        OutlinedButton(onClick = {
                            phoneControlStatus = com.deniz.eda.phone.PhoneControlService.run("lock_screen")
                            phoneScreenText = phoneControlStatus
                        }, modifier = Modifier.weight(1f)) { Text("Kilitle") }
                    }
                    Text("EDA normal uygulama izinleriyle korumalı alanları, kilit ekranını aşamaz; para transferi, silme ve gönderme gibi son işlemleri kendisi onaylamaz.", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }

    if (jarvisOpen) {
        Dialog(onDismissRequest = { if (!jarvisBusy) jarvisOpen = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f),
                shape = RoundedCornerShape(26.dp),
                color = Panel,
                border = BorderStroke(1.dp, Violet.copy(alpha = 0.4f))
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("EDA · JARVIS GÖREV AJANI", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Gemma 2:2B · Yalnızca yerel Ollama", color = Muted, fontSize = 11.sp)
                        }
                        Text("✕", color = Muted,
                            modifier = Modifier.clickable(enabled = !jarvisBusy) { jarvisOpen = false }.padding(8.dp))
                    }
                    Text(
                        "Görevini açıkça belirt. Eda en fazla 5 izinli araç adımı kullanır; gerektiğinde ekranı okuyup sonucu kontrol eder. Banka/ödeme, parola, OTP, mesaj gönderme, satın alma ve silme gibi son adımları sen yaparsın. Ekran içeriği bulut sağlayıcısına gönderilmez.",
                        color = Warn, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    OutlinedTextField(
                        value = jarvisTask,
                        onValueChange = { jarvisTask = it },
                        label = { Text("Yapılacak görev") },
                        placeholder = { Text("Örn. YouTube'u aç ve arama kutusuna ... yaz; aramayı gönderme.") },
                        enabled = !jarvisBusy,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    Column(
                        Modifier.weight(1f).fillMaxWidth()
                            .background(PanelRaised, RoundedCornerShape(14.dp))
                            .verticalScroll(rememberScrollState()).padding(12.dp)
                    ) {
                        Text(
                            jarvisReply.ifBlank { "Henüz görev çalıştırılmadı. Görevi yazıp Jarvis ile yürüt düğmesine bas." },
                            color = Color.White, fontSize = 14.sp, lineHeight = 21.sp
                        )
                    }
                    if (jarvisBusy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
                        Text("Yerel model görev üzerinde çalışıyor. İşlem sayısı sınırlandırılmıştır.", color = Muted, fontSize = 11.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !jarvisBusy,
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Eda'ya vermek istediğin görevi söyle")
                                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                }
                                runCatching { voiceInput.launch(intent) }
                                    .onFailure { jarvisReply = "Ses tanıma açılamadı. Görevi yazarak girebilirsin." }
                            },
                            modifier = Modifier.weight(0.8f)
                        ) { Text("🎙 Ses") }
                        OutlinedButton(
                            enabled = jarvisReply.isNotBlank() && !jarvisBusy,
                            onClick = {
                                val speak = Intent(context, EdaForegroundService::class.java).apply {
                                    action = "ACTION_SPEAK"
                                    putExtra("text", jarvisReply)
                                }
                                runCatching { context.startService(speak) }
                            },
                            modifier = Modifier.weight(0.9f)
                        ) { Text("Yanıtı oku") }
                        Button(
                            enabled = jarvisTask.isNotBlank() && !jarvisBusy,
                            onClick = {
                                val request = jarvisTask.trim()
                                // Common, low-risk app launches should not wait for the local LLM.
                                // Resolve them deterministically, then use Gemma only for other tasks.
                                val normalizedRequest = request.lowercase(java.util.Locale.ROOT)
                                val asksToOpen = Regex("\\b(aç|ac|başlat|baslat|open|launch)\\b").containsMatchIn(normalizedRequest)
                                val quickOpenApp = if (asksToOpen) when {
                                    normalizedRequest.contains("youtube") || normalizedRequest.contains("یوتیوب") -> "youtube"
                                    normalizedRequest.contains("chrome") || normalizedRequest.contains("tarayıcı") || normalizedRequest.contains("browser") -> "chrome"
                                    normalizedRequest.contains("whatsapp") || normalizedRequest.contains("واتساپ") -> "whatsapp"
                                    normalizedRequest.contains("telegram") || normalizedRequest.contains("تلگرام") -> "telegram"
                                    normalizedRequest.contains("harita") || normalizedRequest.contains("maps") || normalizedRequest.contains("نقشه") -> "maps"
                                    normalizedRequest.contains("kamera") || normalizedRequest.contains("camera") -> "camera"
                                    normalizedRequest.contains("mesaj") || normalizedRequest.contains("message") -> "messages"
                                    normalizedRequest.contains("telefon") || normalizedRequest.contains("phone") -> "phone"
                                    else -> null
                                } else null
                                jarvisBusy = true
                                jarvisReply = if (quickOpenApp != null) "Uygulama doğrudan açılıyor…" else "Yerel Jarvis görevi planlıyor ve izinli adımları uyguluyor…"
                                chatScope.launch {
                                    val result: String? = if (quickOpenApp != null) {
                                        runCatching {
                                            com.deniz.eda.utils.SystemTools.runTool("OPEN_APP", context, quickOpenApp)
                                        }.getOrElse { error ->
                                            android.util.Log.e("EDA-Jarvis", "Quick app launch failed", error)
                                            "Uygulama açılamadı. Telefonun uygulama listesini kontrol et."
                                        }
                                    } else runCatching {
                                        AiRouter.sor(
                                            kullaniciSorusu = request,
                                            sistemMesaji = """
                                                Sen Eda'nın Jarvis görev ajanısın. Kullanıcının son isteğini Türkçe olarak yerine getir.
                                                Yalnızca uygulamanın verdiği izinli araçları kullan; kod, shell, rastgele paket veya serbest Intent çalıştırma.
                                                Telefon görevinde önce ekranı oku, sonra kullanıcının açıkça istediği tek adımı uygula ve ekranı yeniden okuyarak sonucu doğrula.
                                                Erişilebilirlik hizmeti kapalıysa bunu açıkça söyle. Model veya araç sonucunu görmeden işlemin yapıldığını iddia etme.
                                                Parola, PIN, OTP, kimlik doğrulama, banka/ödeme/transfer, mesaj gönderme, satın alma, silme veya nihai onay adımını yapma; o noktada dur ve kullanıcıya bırak.
                                                Gereksiz adım ekleme; belirsiz bir uygulama veya hedef varsa tahmin yürütmek yerine sor.
                                                Doğal Türkçe kullan; kısa ama sonucu ve varsa başarısız adımı doğru anlat.
                                            """.trimIndent(),
                                            context = context,
                                            localOnly = true
                                        )
                                    }.getOrElse { error ->
                                        android.util.Log.e("EDA-Jarvis", "Local task failed", error)
                                        null
                                    }
                                    jarvisReply = result?.takeIf { it.isNotBlank() }
                                        ?: "Yerel Ollama yanıt vermedi. Termux'ta Ollama ve gemma2:2b modelinin çalıştığını kontrol et. Bu görev için bulut sağlayıcısına geçilmedi."
                                    jarvisBusy = false
                                }
                            },
                            modifier = Modifier.weight(1.3f)
                        ) { Text(if (jarvisBusy) "Çalışıyor…" else "Jarvis ile yürüt") }
                    }
                }
            }
        }
    }

    if (deepChatOpen) {
        Dialog(onDismissRequest = { if (!deepChatBusy) deepChatOpen = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f),
                shape = RoundedCornerShape(28.dp),
                color = Panel,
                border = BorderStroke(1.dp, Violet.copy(alpha = 0.35f))
            ) {
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("EDA · DERİN SOHBET", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text("Gemma 2:2B · Yerel yapay zekâ", color = Muted, fontSize = 11.sp)
                        }
                        Text("✕", color = Muted, modifier = Modifier.clickable { if (!deepChatBusy) deepChatOpen = false }.padding(8.dp))
                    }
                    HorizontalDivider(color = Violet.copy(alpha = 0.22f))
                    Column(Modifier.weight(1f).fillMaxWidth().background(PanelRaised, RoundedCornerShape(18.dp))
                        .verticalScroll(rememberScrollState()).padding(15.dp)) {
                        Text(deepChatReply, color = Color.White, fontSize = 15.sp, lineHeight = 23.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val stop = Intent(context, EdaForegroundService::class.java).setAction("ACTION_STOP_SPEAK")
                            runCatching { context.startService(stop) }
                        }, modifier = Modifier.weight(1f)) { Text("■ Durdur", fontSize = 12.sp) }
                        OutlinedButton(onClick = {
                            if (deepChatReply.isNotBlank() && deepChatReply != "Düşünüyorum…") {
                                val speak = Intent(context, EdaForegroundService::class.java).apply {
                                    action = "ACTION_SPEAK"; putExtra("text", deepChatReply)
                                }
                                runCatching { context.startService(speak) }
                            }
                        }, modifier = Modifier.weight(1f)) { Text("↻ Tekrar dinle", fontSize = 12.sp) }
                    }
                    OutlinedTextField(
                        value = deepChatInput, onValueChange = { deepChatInput = it },
                        label = { Text("Mesajın") }, placeholder = { Text("Eda'ya ne sormak istiyorsun?") },
                        enabled = !deepChatBusy, modifier = Modifier.fillMaxWidth(), maxLines = 3
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(enabled = !deepChatBusy, onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Eda'ya söylemek istediğini konuş")
                                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                            }
                            runCatching { voiceInput.launch(intent) }
                                .onFailure { android.widget.Toast.makeText(context, "Ses tanıma açılamadı.", android.widget.Toast.LENGTH_SHORT).show() }
                        }, modifier = Modifier.weight(0.85f)) { Text("🎙 Ses") }
                        Button(enabled = deepChatInput.isNotBlank() && !deepChatBusy, onClick = {
                            val question = deepChatInput.trim()
                            deepChatInput = ""; deepChatBusy = true; deepChatReply = "Düşünüyorum…"
                            TermuxDeepChat.run(context, question) { answer ->
                                deepChatReply = answer; deepChatBusy = false
                                val speakIntent = Intent(context, EdaForegroundService::class.java).apply {
                                    action = "ACTION_SPEAK"; putExtra("text", answer)
                                }
                                runCatching { context.startService(speakIntent) }
                            }
                        }, modifier = Modifier.weight(1.15f)) { Text(if (deepChatBusy) "Bekle…" else "Gönder") }
                    }
                    if (deepChatBusy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
                }
            }
        }
    }
}

@Composable
private fun HeroPanel(name: String, time: String, date: String, status: Color, active: Boolean, sleeping: Boolean, onAction: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val artworkId = remember(ThemeConfig.current.id) {
        context.resources.getIdentifier("bg_theme_${ThemeConfig.current.id}", "drawable", context.packageName)
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(
        Brush.linearGradient(listOf(PanelRaised, Panel, Color(ThemeConfig.current.background).copy(alpha = 0.96f)))
    ).padding(20.dp)) {
        if (artworkId != 0) {
            Image(
                painter = painterResource(id = artworkId),
                contentDescription = null,
                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
                alpha = 0.12f
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("MERHABA, ${name.uppercase()}", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
            Text("Ben Eda." + 10.toChar() + "Buradayım.", fontSize = 31.sp, lineHeight = 36.sp,
                fontWeight = FontWeight.Bold, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time, fontSize = 22.sp, color = Cyan, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text("•  $date", fontSize = 10.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = { onAction("ACTION_AKTIF") }, modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (active) Good.copy(alpha = .2f) else Violet),
                    shape = RoundedCornerShape(15.dp)) {
                    Text(if (active) "✓  Dinliyorum" else "▶  Eda'yı uyandır",
                        color = if (active) Good else VoidBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                OutlinedButton(onClick = { onAction("ACTION_UYKU") }, modifier = Modifier.weight(.72f).height(48.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .18f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(15.dp)) { Text(if (sleeping) "☾  Uyuyor" else "☾  Uyku", fontSize = 11.sp) }
            }
            Text("“Eda” de, konuşmaya başlayalım.", color = Color.White.copy(alpha = .68f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun HeroMini(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(PanelRaised, Panel)),
        RoundedCornerShape(22.dp)).padding(20.dp)) {
        Text(title, color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Box(Modifier.width(44.dp).height(2.dp).background(Brush.horizontalGradient(listOf(Violet, Cyan)), RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun MetricCard(label: String, icon: String, value: String, detail: String, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, color = tint, fontSize = 17.sp)
                Spacer(Modifier.width(7.dp))
                Text(label, color = Muted, fontSize = 9.sp, letterSpacing = .8.sp, fontWeight = FontWeight.Bold)
            }
            Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = tint, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProviderPanel(selected: String, onAction: (String) -> Unit) {
    val main = selected.split(" → ").firstOrNull().orEmpty()
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Öncelik zinciri", color = Muted, fontSize = 10.sp)
        Text(selected, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Groq", "Pollinations", "Ollama · Termux").forEach { provider ->
                val action = when (provider) {
                    "Groq" -> "ACTION_AI_GROQ"
                    "Pollinations" -> "ACTION_AI_POLLINATIONS"
                    else -> "ACTION_AI_OLLAMA"
                }
                val isSelected = main.contains(provider, true)
                OutlinedButton(onClick = { onAction(action) }, modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (isSelected) Violet else Violet.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (isSelected) Violet.copy(alpha = .16f) else PanelRaised),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)) {
                    Text(provider, color = if (isSelected) Violet else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ControlTile(icon: String, title: String, detail: String, tint: Color, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(tint.copy(alpha = .12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) { Text(icon, color = tint, fontSize = 21.sp) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(detail, color = Muted, fontSize = 10.sp)
            }
            Text("›", color = tint, fontSize = 24.sp)
        }
    }
}

@Composable
private fun InfoNotice(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(15.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(5.dp))
        Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun StatusLine(name: String, status: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (ok) Good else Warn, CircleShape))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(status, color = Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(if (ok) "OK" else "—", color = if (ok) Good else Warn, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LogsPanel(logs: List<String>) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (logs.isEmpty()) {
            Text("Henüz kayıt yok. Eda'yı kullandıkça burada görünecek.", color = Muted, fontSize = 11.sp)
        } else {
            logs.take(8).forEachIndexed { index, log ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", color = if (index == 0) Cyan else Violet, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(log, color = if (index == 0) Color.White else Muted, fontSize = 10.sp,
                        lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
