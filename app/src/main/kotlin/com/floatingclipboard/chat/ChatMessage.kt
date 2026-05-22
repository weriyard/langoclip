package com.floatingclipboard.chat

/**
 * One turn in a vocabulary-tutor chat. [Role.ASSISTANT] is used both for the seed greeting
 * (filled in by the ViewModel when the tab is opened) and for streamed model replies.
 */
data class ChatMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { USER, ASSISTANT }
}
