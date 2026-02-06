package com.example.travelpool.screens.home

import com.example.travelpool.data.Trip
import com.example.travelpool.data.TripMember
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class HomeRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun currentUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Not logged in")
    fun tripsForCurrentUserFlow(): Flow<List<Trip>> {
        return callbackFlow {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }

            val registration = db.collection("trips")
                .whereArrayContains("members", currentUser.uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        close(error)
                        return@addSnapshotListener
                    }

                    val trips = snapshot?.documents
                        ?.mapNotNull { doc ->
                            doc.toObject(Trip::class.java)?.copy(id = doc.id)
                        }
                        ?: emptyList()

                    trySend(trips)
                }

            awaitClose { registration.remove() }
        }
    }

    suspend fun createTrip(
        name: String,
        destination: String,
        startDate: Long,
        endDate: Long,
        startingAirport: String?
    ): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("User not logged in"))

        return try {
            val tripRef = db.collection("trips").document()
            val tripId = tripRef.id

            val memberName =
                currentUser.displayName?.takeIf { it.isNotBlank() }
                    ?: currentUser.email?.substringBefore("@")
                    ?: "Traveler"


            var attempts = 0
            var lastError: Exception? = null

            while (attempts < 8) {
                attempts++

                val joinCode = com.example.travelpool.utils.CodeUtils.generate6DigitCode()
                val joinCodeRef = db.collection("tripJoinCodes").document(joinCode)

                try {
                    db.runTransaction { transaction ->
                        val codeSnap = transaction.get(joinCodeRef)
                        if (codeSnap.exists()) throw IllegalStateException("Join code collision")

                        val tripData = Trip(
                            id = tripId,
                            joinCode = joinCode,
                            name = name,
                            destination = destination,
                            startDate = startDate,
                            endDate = endDate,
                            startingAirport = startingAirport?.trim()?.takeIf { it.isNotBlank() },
                            ownerId = currentUser.uid,
                            members = listOf(currentUser.uid)
                        )

                        transaction.set(tripRef, tripData)

                        transaction.set(
                            joinCodeRef,
                            mapOf(
                                "tripId" to tripId,
                                "createdBy" to currentUser.uid,
                                "createdAt" to System.currentTimeMillis()
                            )
                        )

                        val memberRef = tripRef.collection("members").document(currentUser.uid)
                        val member = TripMember(
                            uid = currentUser.uid,
                            name = memberName,
                            email = currentUser.email ?: "",
                            role = "organizer",
                            joinedAt = System.currentTimeMillis()
                        )
                        transaction.set(memberRef, member)
                    }.await()

                    val notificationRepo = com.example.travelpool.screens.notification.NotificationRepository()
                    notificationRepo.pushToUsers(
                        recipientUids = listOf(currentUser.uid),
                        tripId = tripId,
                        type = "trip",
                        title = "Trip created",
                        body = "Your trip \"$name\" is ready. Invite friends with the code.",
                        deepLink = "tripDetail/$tripId"
                    )

                    return Result.success(Unit)
                } catch (e: Exception) {
                    lastError = e
                }
            }

            Result.failure(lastError ?: Exception("Failed to create trip"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun joinTripByCode(tripCode: String): Result<String> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("User not logged in"))

        return try {
            val code = tripCode.trim()
            if (code.isEmpty()) return Result.failure(IllegalArgumentException("Enter a trip code"))

            val codeDoc = db.collection("tripJoinCodes").document(code).get().await()
            if (!codeDoc.exists()) {
                return Result.failure(IllegalArgumentException("Invalid trip code"))
            }

            val tripId = codeDoc.getString("tripId")
                ?: return Result.failure(IllegalStateException("Trip link missing for this code"))

            val tripRef = db.collection("trips").document(tripId)

            val displayName = currentUser.displayName?.takeIf { it.isNotBlank() }
                ?: currentUser.email?.substringBefore("@")
                ?: "Traveler"

            val memberRef = tripRef.collection("members").document(currentUser.uid)
            val newMember = TripMember(
                uid = currentUser.uid,
                name = displayName,
                email = currentUser.email ?: "",
                role = "member",
                joinedAt = System.currentTimeMillis()
            )

            db.runBatch { batch ->
                batch.update(tripRef, "members", FieldValue.arrayUnion(currentUser.uid))
                batch.set(memberRef, newMember)
            }.await()

            Result.success(tripId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun getTrip(tripId: String): Result<Trip> {
        return try {
            val snapshot = db.collection("trips")
                .document(tripId)
                .get()
                .await()

            if (!snapshot.exists()) {
                Result.failure(IllegalArgumentException("Trip not found"))
            } else {
                val trip = snapshot.toObject(Trip::class.java)
                    ?.copy(id = snapshot.id)
                    ?: return Result.failure(IllegalStateException("Invalid trip data"))

                Result.success(trip)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun getTripMembers(tripId: String): Result<List<TripMember>> {
        return try {
            val snapshot = db.collection("trips")
                .document(tripId)
                .collection("members")
                .get()
                .await()

            val members = snapshot.documents.mapNotNull { doc ->
                doc.toObject(TripMember::class.java)
            }

            Result.success(members)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getCurrentUserNameFromProfile(userId: String): String {
        return try {
            val doc = db.collection("users")
                .document(userId)
                .get()
                .await()

            val nameFromUsers = doc.getString("name")

            when {
                !nameFromUsers.isNullOrBlank() -> nameFromUsers
                !auth.currentUser?.displayName.isNullOrBlank() -> auth.currentUser!!.displayName!!
                !auth.currentUser?.email.isNullOrBlank() -> auth.currentUser!!.email!!.substringBefore("@")
                else -> "Traveler"
            }
        } catch (e: Exception) {
            auth.currentUser?.displayName
                ?.takeIf { it.isNotBlank() }
                ?: auth.currentUser?.email?.substringBefore("@")
                ?: "Traveler"
        }
    }

    suspend fun markTripCompleted(tripId: String): Result<Unit> {
        return try {
            db.collection("trips")
                .document(tripId)
                .update("status", "completed")
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
