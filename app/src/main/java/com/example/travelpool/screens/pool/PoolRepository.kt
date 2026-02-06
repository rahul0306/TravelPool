import com.example.travelpool.data.PoolContribution
import com.example.travelpool.data.PoolExpense
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

class PoolRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun currentUser() = auth.currentUser ?: throw IllegalStateException("Not logged in")

    fun contributionsFlow(tripId: String): Flow<List<PoolContribution>> = callbackFlow {
        val reg = db.collection("trips")
            .document(tripId)
            .collection("contributions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    close(error)
                    return@addSnapshotListener
                }

                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(PoolContribution::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(items)
            }

        awaitClose { reg.remove() }
    }

    fun expensesFlow(tripId: String): Flow<List<PoolExpense>> = callbackFlow {
        val reg = db.collection("trips")
            .document(tripId)
            .collection("expenses")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    close(error)
                    return@addSnapshotListener
                }

                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(PoolExpense::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(items)
            }

        awaitClose { reg.remove() }
    }

    suspend fun addContribution(tripId: String, amountCents: Long, note: String): Result<Unit> {
        return try {
            val user = currentUser()

            val name = user.displayName
                ?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "Traveler"

            val ref = db.collection("trips")
                .document(tripId)
                .collection("contributions")
                .document()

            val item = PoolContribution(
                id = ref.id,
                tripId = tripId,
                uid = user.uid,
                name = name,
                amountCents = amountCents,
                note = note,
                createdAt = System.currentTimeMillis()
            )

            ref.set(item).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addExpense(
        tripId: String,
        title: String,
        amountCents: Long,
        paidByUid: String,
        paidByName: String,
        splitBetweenUids: List<String>,
        splitType: String,
        splitExactCents: Map<String, Long>,
        splitPercentBps: Map<String, Int>
    ): Result<Unit> {
        return try {
            currentUser()

            val ref = db.collection("trips")
                .document(tripId)
                .collection("expenses")
                .document()

            val item = PoolExpense(
                id = ref.id,
                tripId = tripId,
                title = title,
                amountCents = amountCents,
                paidByUid = paidByUid,
                paidByName = paidByName,
                splitBetweenUids = splitBetweenUids,
                splitType = splitType,
                splitExactCents = splitExactCents,
                splitPercentBps = splitPercentBps,
                createdAt = System.currentTimeMillis()
            )

            ref.set(item).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun settlementsFlow(tripId: String): Flow<List<com.example.travelpool.data.Settlement>> = callbackFlow {
        val reg = db.collection("trips")
            .document(tripId)
            .collection("settlements")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    close(error)
                    return@addSnapshotListener
                }

                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(com.example.travelpool.data.Settlement::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(items)
            }

        awaitClose { reg.remove() }
    }

    suspend fun addSettlement(
        tripId: String,
        toUid: String,
        toName: String,
        amountCents: Long,
        note: String
    ): Result<Unit> {
        return try {
            val user = currentUser()
            val fromUid = user.uid
            val fromName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "Traveler"

            val ref = db.collection("trips")
                .document(tripId)
                .collection("settlements")
                .document()

            val item = com.example.travelpool.data.Settlement(
                id = ref.id,
                tripId = tripId,
                fromUid = fromUid,
                fromName = fromName,
                toUid = toUid,
                toName = toName,
                amountCents = amountCents,
                note = note,
                createdAt = System.currentTimeMillis()
            )

            ref.set(item).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteContribution(tripId: String, contributionId: String): Result<Unit> {
        return try {
            db.collection("trips").document(tripId)
                .collection("contributions").document(contributionId)
                .delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateContribution(
        tripId: String,
        contributionId: String,
        amountCents: Long,
        note: String
    ): Result<Unit> {
        return try {
            db.collection("trips").document(tripId)
                .collection("contributions").document(contributionId)
                .update(
                    mapOf(
                        "amountCents" to amountCents,
                        "note" to note
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteExpense(tripId: String, expenseId: String): Result<Unit> {
        return try {
            db.collection("trips").document(tripId)
                .collection("expenses").document(expenseId)
                .delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateExpense(
        tripId: String,
        expenseId: String,
        title: String,
        amountCents: Long
    ): Result<Unit> {
        return try {
            db.collection("trips").document(tripId)
                .collection("expenses").document(expenseId)
                .update(
                    mapOf(
                        "title" to title,
                        "amountCents" to amountCents
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}