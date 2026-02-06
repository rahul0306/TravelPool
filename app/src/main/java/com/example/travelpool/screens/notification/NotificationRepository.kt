package com.example.travelpool.screens.notification

import com.example.travelpool.data.AppNotification
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class NotificationRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("Not logged in")

    fun notificationsFlow(limit: Long = 100): Flow<List<AppNotification>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList()); close(); return@callbackFlow
        }

        val reg = db.collection("users").document(userId)
            .collection("notifications")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { d ->
                    d.toObject(AppNotification::class.java)?.copy(id = d.id)
                }.orEmpty()
                trySend(list)
            }

        awaitClose { reg.remove() }
    }

    suspend fun pushToUsers(
        recipientUids: List<String>,
        tripId: String,
        type: String,
        title: String,
        body: String,
        deepLink: String
    ) {
        val now = System.currentTimeMillis()
        val batch = db.batch()
        recipientUids.distinct().forEach { toUid ->
            val ref = db.collection("users").document(toUid)
                .collection("notifications").document()
            val n = AppNotification(
                id = ref.id,
                uid = toUid,
                tripId = tripId,
                type = type,
                title = title,
                body = body,
                deepLink = deepLink,
                createdAt = now,
                isRead = false
            )
            batch.set(ref, n)
        }
        batch.commit().await()
    }

    suspend fun markRead(notificationId: String) {
        val userId = uid()
        db.collection("users").document(userId)
            .collection("notifications").document(notificationId)
            .update("isRead", true).await()
    }

    suspend fun markAllRead() {
        val userId = uid()
        val snap = db.collection("users").document(userId)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .get().await()

        val batch = db.batch()
        snap.documents.forEach { d ->
            batch.update(d.reference, "isRead", true)
        }
        batch.commit().await()
    }

    suspend fun deleteNotification(notificationId: String) {
        val userId = uid()
        db.collection("users").document(userId)
            .collection("notifications").document(notificationId)
            .delete().await()
    }
}