package com.example.travelpool.screens.chat

import com.example.travelpool.data.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun currentUser() = auth.currentUser ?: throw IllegalStateException("Not logged in")

    fun messagesFlow(tripId: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = db.collection("trips")
            .document(tripId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    close(error)
                    return@addSnapshotListener
                }

                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(items)
            }

        awaitClose { reg.remove() }
    }

    suspend fun sendMessage(tripId: String, text: String): Result<Unit> {
        return try {
            val user = currentUser()
            val name = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "Traveler"

            val ref = db.collection("trips")
                .document(tripId)
                .collection("messages")
                .document()

            val msg = ChatMessage(
                id = ref.id,
                tripId = tripId,
                text = text.trim(),
                senderUid = user.uid,
                senderName = name,
                createdAt = System.currentTimeMillis()
            )

            ref.set(msg).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}