package com.deniz.eda.ai

data class ChatMessage(val role: String, val content: String)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 500
)

data class ChatChoice(val message: ChatMessage)
data class ChatCompletionResponse(val choices: List<ChatChoice>?)
