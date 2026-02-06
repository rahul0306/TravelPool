package com.example.travelpool.screens.profile

import com.example.travelpool.data.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

class ProfileRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("Not logged in")

    suspend fun fetchProfile(): Result<UserProfile> {
        return try {
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not logged in"))
            val snap = db.collection("users").document(user.uid).get().await()

            val fromDb = snap.toObject(UserProfile::class.java)
            val profile = (fromDb ?: UserProfile()).copy(
                uid = user.uid,
                name = (fromDb?.name?.takeIf { it.isNotBlank() } ?: user.displayName ?: "Traveler"),
                email = (fromDb?.email?.takeIf { it.isNotBlank() } ?: user.email.orEmpty())
            )

            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(name: String, homeAirport: String): Result<Unit> {
        return try {
            val userId = uid()
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not logged in"))

            db.collection("users").document(userId)
                .update(
                    mapOf(
                        "name" to name.trim(),
                        "homeAirport" to homeAirport.trim()
                    )
                ).await()

            val req = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(name.trim())
                .build()
            user.updateProfile(req).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}