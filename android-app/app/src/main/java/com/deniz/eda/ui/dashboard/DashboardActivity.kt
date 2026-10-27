package com.deniz.eda.ui.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deniz.eda.R
import com.deniz.eda.service.EdaForegroundService
import com.deniz.eda.ui.theme.EdaTheme

class DashboardActivity : ComponentActivity() {
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.yenile()
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
        val intent = Intent(this, EdaForegroundService::class.java).apply { this.action = action }
        startService(intent)
        viewModel.yenile()
    }
}

@Composable
fun ThroneDashboardScreen(viewModel: DashboardViewModel, onAction: (String) -> Unit) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050506))) {
        // تصویر پس‌زمینه (فرشته تاریک)
        Image(
            painter = painterResource(id = R.drawable.throne_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // هدر بالای صفحه
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("EDA", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF243D))
                    Text("PERSONAL AI", fontSize = 12.sp, color = Color(0xFFB9ADB2))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFFFF243D))
                    Spacer(Modifier.width(16.dp))
                    // دکمه وضعیت (اکتیو/خاموش)
                    Text(
                        text = if (state.mod == "AKTIF") "AKTİF" else "UYKU",
                        color = if (state.mod == "AKTIF") Color(0xFFFF243D) else Color(0xFFB9ADB2),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFF121015), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            // متن وسط
            Text("Sana nasıl yardımcı olabilirim?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)

            Spacer(Modifier.height(20.dp))

            // دکمه بزرگ میکروفون (با هاله قرمز)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                Box(modifier = Modifier.size(120.dp).background(Color(0xFFFF243D).copy(alpha = 0.2f), CircleShape))
                Box(modifier = Modifier.size(90.dp).background(Color(0xFF050506), CircleShape)) // داخل مشکی
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Mikrofon",
                    tint = Color(0xFFFF243D),
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.height(30.dp))

            // ۶ دکمه مربعی (گرید)
            val buttons = listOf(
                Triple("Sohbet", Icons.Default.Email, "ACTION_SOHBET"),
                Triple("Kontrol", Icons.Default.Settings, "ACTION_KONTROL"),
                Triple("Mesaj", Icons.Default.Email, "ACTION_MESAJ"),
                Triple("Telefon", Icons.Default.Phone, "ACTION_TELEFON"),
                Triple("Konum", Icons.Default.LocationOn, "ACTION_KONUM"),
                Triple("Uygulamalar", Icons.Default.List, "ACTION_UYGULAMALAR")
            )

            // Grid چیدمان 3x2
            for (i in 0 until 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (j in 0 until 3) {
                        val item = buttons[i * 3 + j]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(90.dp)
                                .background(Color(0xFF121015).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(item.second, contentDescription = null, tint = Color(0xFFFF243D))
                                Spacer(Modifier.height(6.dp))
                                Text(item.first, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(20.dp))

            // کارت کلاغ (Raven Card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121015).copy(alpha = 0.9f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF243D).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // به جای عکس کلاغ، از یه آیکون یا متن استفاده می‌کنیم
                    Text("\"Korkma... ben buradayım...\"", color = Color(0xFFFF243D), fontSize = 16.sp, fontStyle = FontStyle.Italic)
                }
            }

            Spacer(Modifier.height(60.dp)) // فاصله برای منوی پایین
        }
    }
}
