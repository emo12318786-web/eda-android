package com.eda.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bounded, allowlisted agent loop. The model may request only named local tools;
 * it cannot provide arbitrary code, URLs, shell commands, or Android intents.
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
        // Give the model one final chance to summarize, without allowing more tools.
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
            "get_time" -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            "get_date" -> SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
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

    private fun callModel(endpoint: String, model: String, apiKey: String, messages: JSONArray): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("${endpoint.trimEnd('/')}/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
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
        private const val MAX_STEPS = 3
        private val noteLock = Any()
        private const val SYSTEM_PROMPT = """You are Eda, a kind and concise Persian-speaking phone assistant. You may use only these local tools: get_time (no arguments), get_date (no arguments), save_note with arguments {"text":"..."} only when the user clearly asks to remember/save something, and search_notes with arguments {"query":"..."} when the user asks about their saved notes. Never claim a tool ran before its result is returned. Never invent tools or phone actions. Do not request or expose secrets. Respond with exactly one JSON object and no markdown: {"type":"tool_call","tool":"get_time|get_date|save_note|search_notes","arguments":{...}} when a tool is necessary, or {"type":"final","answer":"your answer"} otherwise. You cannot directly control the flashlight or other device settings from this agent loop. Answer in the user's language."""
    }
}
