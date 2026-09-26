package com.myapps.onlineexaminationapps.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "", // "student" or "teacher"
    val nickname: String = "",
    val profilePhotoUrl: String = ""
) {
    val displayName: String
        get() = when {
            nickname.isNotBlank() -> nickname
            name.isNotBlank() -> name
            email.isNotBlank() -> email.substringBefore("@")
            else -> if (uid.isNotBlank()) "User (${uid.take(6)})" else "User"
        }
}
