package com.deniz.eda.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores a short, private on-device conversation window so follow-up questions
 * can refer to recent turns. Only user/assistant text is stored; agent tool
 * traces, screen contents and credentials are never added by this class.
 */
object ConversationHistoryStore {
    private const val PREFS = "eda_conversation_history"
    private const val KEY_MESSAGES = "messages"
    private const val MAX_MESSAGES = 12
    private const val MAX_TEXT_CHARS = 4000

    @Synchronized
    fun read(context: Context): List<ChatMessage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val messages = mutableListOf<ChatMessage>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val role = item.optString("role")
                val text = item.optString("content").take(MAX_TEXT_CHARS)
                if (role in setOf("user", "assistant") && text.isNotBlank()) {
                    messages.add(ChatMessage(role, text))
                }
            }
            messages.takeLast(MAX_MESSAGES)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun addExchange(context: Context, userText: String, assistantText: String) {
        if (userText.isBlank() || assistantText.isBlank()) return
        val messages = JSONArray()
        read(context).forEach { message ->
            messages.put(JSONObject().put("role", message.role).put("content", message.content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText.take(MAX_TEXT_CHARS)))
        messages.put(JSONObject().put("role", "assistant").put("content", assistantText.take(MAX_TEXT_CHARS)))
        val start = maxOf(0, messages.length() - MAX_MESSAGES)
        val bounded = JSONArray()
        for (i in start until messages.length()) bounded.put(messages.get(i))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MESSAGES, bounded.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_MESSAGES).apply()
    }
}
