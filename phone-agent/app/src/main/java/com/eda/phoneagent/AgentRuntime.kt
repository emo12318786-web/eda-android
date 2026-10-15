package com.eda.phoneagent

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bounded, allowlisted agent loop. The model can request named local tools only.
 * It cannot provide arbitrary code, shell commands, package names, or Android intents.
 */
internal class AgentRuntime(private val context: Context) {
    fun answer(prompt: String, endpoint: String, model: String, apiKey: String): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", prompt))

        repeat(MAX_STEPS) {
            val response = callModel(endpoint, model, apiKey, messages)
            val parsed = parseJsonObject(response)
            if (parsed == null) return response
            when (parsed.optString("type")) {
                "final" -> return parsed.optString("answer").ifBlank { "مدل پاسخ خالی فرستاد." }
                "tool_call" -> {
                    val name = parsed.optString("tool")
                    val args = parsed.optJSONObject("arguments") ?: JSONObject()
                    val result = runTool(name, args)
                    messages.put(JSONObject().put("role", "assistant").put("content", response))
                    messages.put(JSONObject().put("role", "user").put("content",
                        JSONObject().put("tool_result", result).toString()))
                }
                else -> return response
            }
        }
        messages.put(JSONObject().put("role", "user").put("content",
            "Tool-call limit reached. Return JSON only: {\"type\":\"final\",\"answer\":\"...\"}."))
        val last = callModel(endpoint, model, apiKey, messages)
        return try {
            JSONObject(last).optString("answer").ifBlank { "برای جلوگیری از اجرای بی‌پایان، کار را پس از $MAX_STEPS مرحله متوقف کردم." }
        } catch (_: Exception) {
            "برای جلوگیری از اجرای بی‌پایان، کار را پس از $MAX_STEPS مرحله متوقف کردم."
        }
    }

    private fun parseJsonObject(raw: String): JSONObject? {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try { JSONObject(text) } catch (_: Exception) {
            val first = text.indexOf('{')
            val last = text.lastIndexOf('}')
            if (first >= 0 && last > first) try { JSONObject(text.substring(first, last + 1)) } catch (_: Exception) { null }
            else null
        }
    }

    private fun runTool(name: String, args: JSONObject): String {
        val prefs = context.getSharedPreferences("eda_agent", Context.MODE_PRIVATE)
        return when (name) {
            "get_time" -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            "get_date" -> SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
            "get_battery" -> batteryStatus()
            "calculate" -> try {
                val expression = args.optString("expression").trim()
                if (expression.isBlank() || expression.length > 100) "خطا: عبارت محاسبه معتبر نیست."
                else {
                    val value = SafeCalculator.eval(expression)
                    "نتیجه: " + if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
                }
            } catch (_: Exception) { "محاسبه انجام نشد؛ عبارت ریاضی را بررسی کن." }
            "open_app" -> openAllowedApp(args.optString("app"))
            "open_wifi_settings" -> openSystemPanel("wifi")
            "open_sound_settings" -> openSystemPanel("sound")
            "open_display_settings" -> openSystemPanel("display")
            "save_note" -> {
                val note = args.optString("text").trim()
                if (note.isBlank()) "خطا: متن یادداشت خالی است."
                else {
                    synchronized(noteLock) {
                        val old = prefs.getString("notes", "").orEmpty()
                        prefs.edit().putString("notes", (old + "\n• " + note).takeLast(20000)).commit()
                    }
                    "یادداشت روی گوشی ذخیره شد."
                }
            }
            "search_notes" -> {
                val query = args.optString("query").trim()
                val notes = prefs.getString("notes", "").orEmpty()
                if (notes.isBlank()) "هیچ یادداشتی ذخیره نشده."
                else if (query.isBlank()) notes.takeLast(5000)
                else notes.lines().filter { it.contains(query, ignoreCase = true) }
                    .takeLast(20).joinToString("\n").ifBlank { "یادداشتی با این عبارت پیدا نشد." }
            }
            else -> "خطا: این ابزار مجاز نیست."
        }
    }

    private fun batteryStatus(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "وضعیت باتری خوانده نشد."
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = if (level >= 0) (level * 100 / scale) else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        if (percent < 0) return "درصد باتری خوانده نشد."
        return "شارژ باتری $percent از ۱۰۰ درصد است؛ " +
            if (status == BatteryManager.BATTERY_STATUS_FULL) "باتری کاملاً پر است."
            else if (charging) "گوشی در حال شارژ است." else "گوشی به شارژر وصل نیست."
    }

    private fun openSystemPanel(kind: String): String {
        val intent = when (kind) {
            "wifi" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI)
                else Intent(Settings.ACTION_WIFI_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            else -> return "این پنل مجاز نیست."
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            when (kind) {
                "wifi" -> "پنل وای‌فای باز شد. اندروید اجازهٔ خاموش‌کردن بی‌اجازهٔ وای‌فای را نمی‌دهد؛ از پنل آن را تغییر بده."
                "sound" -> "تنظیمات صدا باز شد."
                else -> "تنظیمات نمایشگر باز شد."
            }
        } catch (_: Exception) { "بازکردن تنظیمات ممکن نشد." }
    }

    private fun openAllowedApp(raw: String): String {
        val app = raw.trim().lowercase(Locale.ROOT)
        val packages = when (app) {
            "chrome", "browser", "مرورگر", "کروم" -> listOf("com.android.chrome", "com.google.android.browser")
            "youtube", "یوتیوب" -> listOf("com.google.android.youtube")
            "maps", "map", "نقشه", "گوگل مپس" -> listOf("com.google.android.apps.maps")
            "camera", "دوربین" -> listOf("com.sec.android.app.camera", "com.android.camera2", "com.google.android.GoogleCamera")
            "settings", "تنظیمات" -> listOf("com.android.settings")
            "whatsapp", "واتساپ" -> listOf("com.whatsapp")
            "telegram", "تلگرام" -> listOf("org.telegram.messenger", "org.thunderdog.challegram")
            "phone", "تلفن", "تماس" -> listOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer")
            "messages", "پیامک", "پیام‌ها" -> listOf("com.google.android.apps.messaging", "com.samsung.android.messaging")
            else -> return "این برنامه در فهرست امن نیست. می‌توانی از صفحهٔ برنامه‌ها آن را دستی باز کنی."
        }
        for (packageName in packages) {
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null) {
                return try {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    "برنامه باز شد: $app"
                } catch (_: Exception) { "بازکردن برنامه ممکن نشد." }
            }
        }
        return "برنامهٔ «$app» روی گوشی پیدا نشد."
    }

    private fun callModel(endpoint: String, model: String, apiKey: String, messages: JSONArray): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("${endpoint.trimEnd('/')}/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
            }
            val body = JSONObject().put("model", model).put("messages", messages)
                .put("temperature", 0.2).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) return "اتصال مدل ناموفق بود (HTTP $code). آدرس، مدل، کلید و اینترنت را بررسی کن."
            return JSONObject(text).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content").trim()
                .ifBlank { "مدل پاسخ خالی فرستاد." }
        } catch (e: Exception) {
            return "اتصال برقرار نشد. اینترنت و آدرس API را بررسی کن. جزئیات: ${e.javaClass.simpleName}"
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val MAX_STEPS = 4
        private val noteLock = Any()
        private const val SYSTEM_PROMPT = """You are Eda, a warm, concise Persian-speaking phone assistant. Understand the user's intent and use a local tool when a real device action or live fact is requested. Available tools:
- get_time: current local time, no arguments.
- get_date: current date, no arguments.
- get_battery: current battery percentage and charging state, no arguments.
- calculate: safely calculate a basic arithmetic expression, arguments {"expression":"12*(3+4)"}.
- open_app: open only a supported app by its friendly name, arguments {"app":"youtube"}; supported names include chrome, youtube, maps, camera, settings, whatsapp, telegram, phone, messages.
- open_wifi_settings: open Android's Wi-Fi panel; Android may require the user to toggle Wi-Fi manually.
- open_sound_settings: open Android sound settings.
- open_display_settings: open Android display settings.
- save_note with {"text":"..."} only when the user clearly asks to remember/save something.
- search_notes with {"query":"..."} when the user asks about saved notes.
Never claim an action succeeded before the tool result. Never invent tools, open arbitrary packages, run shell/code, send messages, or change sensitive settings silently. A request to send a message must not be treated as merely opening an app. Ask a short clarification if the requested app/action is ambiguous. Respond in the user's language, naturally and briefly. Return exactly one JSON object, no markdown: {"type":"tool_call","tool":"TOOL_NAME","arguments":{...}} when a tool is necessary, or {"type":"final","answer":"..."} otherwise."""
    }
}
