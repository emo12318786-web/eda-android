package com.deniz.eda.ui.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniz.eda.service.EdaForegroundService
import com.deniz.eda.ui.theme.EdaTheme

// رنگ‌های تم Throne
val BgBlack = Color(0xFF050506)
val CardDark = Color(0xFF121015)
val RedPrimary = Color(0xFFFF243D)
val RedDark = Color(0xFF8F071A)
val TextWhite = Color(0xFFF5F1F2)

class DashboardActivity : ComponentActivity() {
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // برای جلوگیری از کرش، درخواست به دیتابیس رو کمی به تعویق میندازیم
        setContent {
            EdaTheme {
                ThroneDashboardScreen(
                    viewModel = viewModel,
                    onAction = { action -> sendAction(action) }
                )
            }
        }
    }

    private fun sendAction(action: String) {
        try {
            val intent = Intent(this, EdaForegroundService::class.java).apply { this.action = action }
            startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("Dashboard", "Action error: \${e.message}")
        }
    }
}

@Composable
fun ThroneDashboardScreen(viewModel: DashboardViewModel, onAction: (String) -> Unit) {
    // گرفتن وضعیت با مقدار پیش‌فرض برای جلوگیری از کرش
    val state by viewModel.state.collectAsState()
    val isActive = state.sonMod == "AKTIF" || state.mod == "AKTIF"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
    ) {
        // هاله قرمز نئونی در پس‌زمینه (بدون نیاز به عکس)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(RedPrimary.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width / 2, size.height / 3),
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width / 2, size.height / 3)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // هدر
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("EDA", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = RedPrimary, letterSpacing = 2.sp)
                    Text("PERSONAL AI", fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    
                    // دکمه وضعیت
                    Box(
                        modifier = Modifier
                            .background(CardDark, RoundedCornerShape(20.dp))
                            .border(1.dp, if (isActive) RedPrimary else Color.DarkGray, RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(if (isActive) RedPrimary else Color.Gray, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isActive) "AKTİF" else "UYKU", color = if (isActive) RedPrimary else Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            Text("Sana nasıl yardımcı olabilirim?", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)

            Spacer(Modifier.height(30.dp))

            // دکمه میکروفون (نئونی)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                // هاله بیرونی
                Box(modifier = Modifier.size(130.dp).background(RedPrimary.copy(alpha = 0.15f), CircleShape))
                // هاله میانی
                Box(modifier = Modifier.size(100.dp).border(2.dp, RedPrimary.copy(alpha = 0.5f), CircleShape))
                // دکمه اصلی
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(BgBlack, CircleShape)
                        .border(2.dp, RedPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Mic", tint = RedPrimary, modifier = Modifier.size(40.dp))
                }
            }

            Spacer(Modifier.height(40.dp))

            // دکمه‌های گرید (شیشه‌ای)
            val buttons = listOf(
                Triple("Sohbet", Icons.Default.Email, "ACTION_SOHBET"),
                Triple("Kontrol", Icons.Default.Settings, "ACTION_KONTROL"),
                Triple("Mesaj", Icons.Default.Email, "ACTION_MESAJ"),
                Triple("Telefon", Icons.Default.Phone, "ACTION_TELEFON"),
                Triple("Konum", Icons.Default.LocationOn, "ACTION_KONUM"),
                Triple("Uygulamalar", Icons.Default.List, "ACTION_UYGULAMALAR")
            )

            for (i in 0 until 2) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (j in 0 until 3) {
                        val item = buttons[i * 3 + j]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(95.dp)
                                .background(CardDark.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                                .border(1.dp, RedDark.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(8.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(item.second, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(item.first, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(30.dp))

            // کارت کلاغ (طرح‌بندی شده با کد)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, RedDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(CardDark, RedDark.copy(alpha = 0.2f))))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "\"Korkma... ben buradayım...\"",
                        color = RedPrimary,
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(80.dp))
        }
    }
}
