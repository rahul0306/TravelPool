package com.example.travelpool.screens.auth

import com.example.travelpool.data.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

class AuthRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val db: FirebaseFirestore = Firebase.firestore
) {
    suspend fun signUp(name: String, email: String, password: String): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("No user after signup"))

            val profileUpdates = userProfileChangeRequest { displayName = name }
            user.updateProfile(profileUpdates).await()

            val profile = UserProfile(
                uid = user.uid,
                name = name,
                email = email
            )

            db.collection("users")
                .document(user.uid)
                .set(profile)
                .await()

            user.sendEmailVerification().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            auth.currentUser?.reload()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun currentUserUid(): String? = auth.currentUser?.uid
    fun currentUserEmail(): String? = auth.currentUser?.email
    fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified == true

    suspend fun reloadCurrentUser() {
        auth.currentUser?.reload()?.await()
    }

    suspend fun resendVerificationEmail(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        auth.signOut()
    }

    suspend fun fetchCurrentUserProfile(): UserProfile? {
        val uid = auth.currentUser?.uid ?: return null
        return db.collection("users").document(uid).get().await()
            .toObject(UserProfile::class.java)
    }
}