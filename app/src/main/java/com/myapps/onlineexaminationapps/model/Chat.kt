package com.myapps.onlineexaminationapps.model

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Chat(
    val chatId: String = "",
    val studentId: String = "",
    val teacherId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageType: String = "text", // "text" or "image"
    val lastMessageTime: Any? = null
) {
    val formattedLastMessageTime: String
        get() {
            if (lastMessageTime == null) return ""
            val millis = when (lastMessageTime) {
                is Long -> lastMessageTime
                is Timestamp -> lastMessageTime.seconds * 1000 + lastMessageTime.nanoseconds / 1000000
                is Date -> lastMessageTime.time
                is Number -> lastMessageTime.toLong()
                else -> return ""
            }
            val now = System.currentTimeMillis()
            val diff = now - millis
            val sdf = if (diff < 24 * 60 * 60 * 1000) {
                SimpleDateFormat("h:mm a", Locale.getDefault())
            } else {
                SimpleDateFormat("MMM d", Locale.getDefault())
            }
            return sdf.format(Date(millis))
        }

    val lastMessageTimeMillis: Long
        get() = when (lastMessageTime) {
            is Long -> lastMessageTime
            is Timestamp -> lastMessageTime.seconds * 1000 + lastMessageTime.nanoseconds / 1000000
            is Date -> lastMessageTime.time
            is Number -> lastMessageTime.toLong()
            else -> 0L
        }
}
