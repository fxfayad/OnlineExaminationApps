package com.myapps.onlineexaminationapps.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.myapps.onlineexaminationapps.model.User
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    fun getCurrentUser() = auth.currentUser

    suspend fun getUserRole(uid: String): String? = suspendCoroutine { continuation ->
        val currentUser = auth.currentUser
        Log.d("FIREBASE_DEBUG", "getUserRole fetching role for UID: $uid, Email: ${currentUser?.email}")
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role")
                    if (!role.isNullOrBlank()) {
                        val cleanedRole = role.trim().lowercase()
                        Log.d("FIREBASE_DEBUG", "User UID: $uid, Email: ${currentUser?.email}, Role: $cleanedRole")
                        continuation.resume(cleanedRole)
                    } else {
                        Log.w("FIREBASE_DEBUG", "User doc exists but role is empty. Setting default 'student' for UID: $uid")
                        firestore.collection("users").document(uid).update("role", "student")
                        continuation.resume("student")
                    }
                } else {
                    Log.w("FIREBASE_DEBUG", "User doc missing for UID $uid. Creating default student document.")
                    val user = User(
                        uid = uid,
                        email = currentUser?.email ?: "",
                        role = "student"
                    )
                    firestore.collection("users").document(uid).set(user)
                        .addOnCompleteListener {
                            continuation.resume("student")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("FIREBASE_DEBUG", "Auth/Firestore error getting role for UID: $uid", e)
                continuation.resume(null)
            }
    }

    fun signOut() {
        Log.d("FIREBASE_DEBUG", "User signed out: ${auth.currentUser?.uid}")
        auth.signOut()
    }
}
