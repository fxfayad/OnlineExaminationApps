package com.myapps.onlineexaminationapps.model

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val messageType: String = "text", // "text" or "image"
    val message: String = "",
    val imageUrl: String = "",
    val timestamp: Any? = null
) {
    val formattedTime: String
        get() {
            if (timestamp == null) return ""
            val millis = when (timestamp) {
                is Long -> timestamp
                is Timestamp -> timestamp.seconds * 1000 + timestamp.nanoseconds / 1000000
                is Date -> timestamp.time
                is Number -> timestamp.toLong()
                else -> System.currentTimeMillis()
            }
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return sdf.format(Date(millis))
        }

    val timestampMillis: Long
        get() = when (timestamp) {
            is Long -> timestamp
            is Timestamp -> timestamp.seconds * 1000 + timestamp.nanoseconds / 1000000
            is Date -> timestamp.time
            is Number -> timestamp.toLong()
            else -> 0L
        }
}
