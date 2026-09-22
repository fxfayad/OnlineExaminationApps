package com.myapps.onlineexaminationapps.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "" // "student" or "teacher"
)
