package com.myapps.onlineexaminationapps.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Chapter(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val createdBy: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
)
