package com.example.travelpool.screens.itinerary

import com.example.travelpool.data.ItineraryItem
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

class ItineraryRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun currentUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Not logged in")

    private fun currentName(): String =
        auth.currentUser?.displayName ?: "Member"

    fun itineraryFlow(tripId: String): Flow<List<ItineraryItem>> = callbackFlow {
        val reg = db.collection("trips")
            .document(tripId)
            .collection("itinerary")
            .orderBy("startTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val items = snap?.documents?.mapNotNull { d ->
                    d.toObject(ItineraryItem::class.java)?.copy(
                        id = d.id,
                        tripId = tripId
                    )
                }.orEmpty()
                trySend(items)
            }
        awaitClose { reg.remove() }
    }

    suspend fun upsert(tripId: String, item: ItineraryItem): Result<Unit> {
        return try {
            val col = db.collection("trips").document(tripId).collection("itinerary")
            val now = System.currentTimeMillis()

            if (item.id.isBlank()) {
                val doc = col.document()
                val toSave = item.copy(
                    id = doc.id,
                    tripId = tripId,
                    createdByUid = currentUid(),
                    createdByName = currentName(),
                    createdAt = now,
                    updatedAt = now
                )
                doc.set(toSave).await()
            } else {
                val doc = col.document(item.id)
                val toSave = item.copy(
                    tripId = tripId,
                    updatedAt = now
                )
                doc.set(toSave).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(tripId: String, itemId: String): Result<Unit> {
        return try {
            db.collection("trips").document(tripId)
                .collection("itinerary").document(itemId)
                .delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}