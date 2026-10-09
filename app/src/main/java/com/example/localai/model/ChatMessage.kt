package com.example.localai.model

/** 一条聊天消息 */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role { USER, ASSISTANT }
}