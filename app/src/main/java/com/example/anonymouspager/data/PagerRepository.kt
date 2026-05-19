package com.example.anonymouspager.data

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.UUID

class PagerRepository(context: Context) {
    private val appContext = context.applicationContext
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val random = SecureRandom()

    val currentUserId: String
        get() = localDeviceId()

    suspend fun ensureSignedInAndRegistered() {
        requireFirebaseConfigured()
        firestore.collection("devices").document(currentUserId).set(
            mapOf(
                "deviceId" to currentUserId,
                "updatedAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    suspend fun registerPushToken(token: String) {
        if (!isFirebaseConfigured()) return
        firestore.collection("devices").document(currentUserId).set(
            mapOf(
                "deviceId" to currentUserId,
                "fcmToken" to token,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    suspend fun createCode(): PagerCode {
        requireFirebaseConfigured()
        val now = System.currentTimeMillis()
        val expiresAtMillis = now + CODE_TTL_MS

        repeat(8) {
            val code = generatePin()
            val codeRef = firestore.collection("codes").document(code)
            val created = firestore.runTransaction { transaction ->
                val existing = transaction.get(codeRef)
                val existingExpiry = existing.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
                if (existing.exists() && existingExpiry > now && existing.getBoolean("consumed") != true) {
                    false
                } else {
                    transaction.set(
                        codeRef,
                        mapOf(
                            "code" to code,
                            "receiverId" to currentUserId,
                            "consumed" to false,
                            "createdAt" to Timestamp(now / 1000, ((now % 1000) * 1_000_000).toInt()),
                            "expiresAt" to Timestamp(
                                expiresAtMillis / 1000,
                                ((expiresAtMillis % 1000) * 1_000_000).toInt()
                            )
                        )
                    )
                    true
                }
            }.await()

            if (created) {
                return PagerCode(
                    code = code,
                    qrPayload = qrPayloadFor(code),
                    expiresAtMillis = expiresAtMillis
                )
            }
        }

        error("Could not create a temporary code")
    }

    suspend fun redeemCode(input: String): PagerPair {
        requireFirebaseConfigured()
        val code = extractCode(input)
        val now = System.currentTimeMillis()
        val codeRef = firestore.collection("codes").document(code)

        return firestore.runTransaction { transaction ->
            val codeSnapshot = transaction.get(codeRef)
            if (!codeSnapshot.exists()) error("Code not found")

            val receiverId = codeSnapshot.getString("receiverId").orEmpty()
            val expiresAtMillis = codeSnapshot.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
            if (receiverId.isBlank()) error("Code is invalid")
            if (receiverId == currentUserId) error("You cannot pair with your own code")
            if (codeSnapshot.getBoolean("consumed") == true || expiresAtMillis <= now) {
                error("Code has expired")
            }

            val pairId = "${currentUserId}_$receiverId"
            val pairRef = firestore.collection("pairs").document(pairId)
            val createdAt = Timestamp(now / 1000, ((now % 1000) * 1_000_000).toInt())
            transaction.set(
                pairRef,
                mapOf(
                    "senderId" to currentUserId,
                    "receiverId" to receiverId,
                    "status" to "active",
                    "createdAt" to createdAt,
                    "updatedAt" to createdAt
                )
            )
            transaction.update(
                codeRef,
                mapOf(
                    "consumed" to true,
                    "consumedBy" to currentUserId,
                    "consumedAt" to createdAt
                )
            )
            PagerPair(pairId, currentUserId, receiverId, now)
        }.await()
    }

    suspend fun sendRing(pairId: String, superRing: Boolean = false): PagerResult = runCatching {
        requireFirebaseConfigured()
        val pairSnapshot = firestore.collection("pairs").document(pairId).get().await()
        if (!pairSnapshot.exists()) error("Connection not found")
        if (pairSnapshot.getString("senderId") != currentUserId) error("Connection cannot be ringed")
        if (pairSnapshot.getString("status") != "active") error("Connection is not active")
        val receiverId = pairSnapshot.getString("receiverId").orEmpty()
        val blockSnapshot = firestore.collection("blocks").document("${receiverId}_$currentUserId").get().await()
        if (blockSnapshot.exists()) error("This connection is blocked")

        firestore.collection("ringReceipts").add(
            mapOf(
                "pairId" to pairId,
                "senderId" to currentUserId,
                "receiverId" to receiverId,
                "status" to "sent",
                "superRing" to superRing,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        PagerResult.Ok
    }.getOrElse { error ->
        PagerResult.Error(error.message ?: "Could not send ring")
    }

    suspend fun deletePair(pairId: String): PagerResult = updatePairStatus(pairId, "deleted")

    suspend fun unblockPair(pairId: String): PagerResult = runCatching {
        requireFirebaseConfigured()
        val pairRef = firestore.collection("pairs").document(pairId)
        val pair = pairRef.get().await()
        if (!pair.exists()) error("Connection not found")
        val senderId = pair.getString("senderId").orEmpty()
        val receiverId = pair.getString("receiverId").orEmpty()
        if (currentUserId != senderId && currentUserId != receiverId) error("Connection cannot be unblocked")
        val blockedId = if (currentUserId == senderId) receiverId else senderId

        firestore.runBatch { batch ->
            batch.update(
                pairRef,
                mapOf(
                    "status" to "active",
                    "blockedBy" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.delete(firestore.collection("blocks").document("${currentUserId}_$blockedId"))
        }.await()
        PagerResult.Ok
    }.getOrElse { error ->
        PagerResult.Error(error.message ?: "Could not unblock connection")
    }

    suspend fun blockPair(pairId: String): PagerResult = runCatching {
        requireFirebaseConfigured()
        val pairRef = firestore.collection("pairs").document(pairId)
        val pair = pairRef.get().await()
        if (!pair.exists()) error("Connection not found")
        val senderId = pair.getString("senderId").orEmpty()
        val receiverId = pair.getString("receiverId").orEmpty()
        if (currentUserId != senderId && currentUserId != receiverId) error("Connection cannot be blocked")
        val blockedId = if (currentUserId == senderId) receiverId else senderId

        firestore.runBatch { batch ->
            batch.set(
                firestore.collection("blocks").document("${currentUserId}_$blockedId"),
                mapOf(
                    "blockerId" to currentUserId,
                    "blockedId" to blockedId,
                    "pairId" to pairId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                pairRef,
                mapOf(
                    "status" to "blocked",
                    "blockedBy" to currentUserId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        PagerResult.Ok
    }.getOrElse { error ->
        PagerResult.Error(error.message ?: "Could not block connection")
    }

    suspend fun respondToRing(receiptId: String, response: String): PagerResult = runCatching {
        requireFirebaseConfigured()
        firestore.collection("ringReceipts").document(receiptId).update(
            mapOf(
                "status" to response,
                "respondedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        PagerResult.Ok
    }.getOrElse { error ->
        PagerResult.Error(error.message ?: "Could not respond")
    }

    fun savedPairs(): Flow<List<PagerPair>> = pairFlow("senderId")

    fun incomingPairs(): Flow<List<PagerPair>> = pairFlow("receiverId")

    fun incomingRings(): Flow<List<PagerRing>> = callbackFlow {
        if (!isFirebaseConfigured()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        var registration: ListenerRegistration? = firestore.collection("ringReceipts")
            .whereEqualTo("receiverId", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val rings = snapshot?.documents.orEmpty()
                    .filter { it.getString("status") == "sent" }
                    .map { document ->
                        PagerRing(
                            id = document.id,
                            pairId = document.getString("pairId").orEmpty(),
                            senderId = document.getString("senderId").orEmpty(),
                            receiverId = document.getString("receiverId").orEmpty(),
                            createdAtMillis = document.getTimestamp("createdAt").toMillisOrZero(),
                            superRing = document.getBoolean("superRing") == true
                        )
                    }
                    .sortedByDescending { it.createdAtMillis }
                trySend(rings)
            }

        awaitClose {
            registration?.remove()
            registration = null
        }
    }

    private fun pairFlow(field: String): Flow<List<PagerPair>> = callbackFlow {
        if (!isFirebaseConfigured()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        var registration: ListenerRegistration? = firestore.collection("pairs")
            .whereEqualTo(field, currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val pairs = snapshot?.documents.orEmpty()
                    .filter { it.getString("status") == "active" || it.getString("status") == "blocked" }
                    .map { document ->
                        PagerPair(
                            id = document.id,
                            senderId = document.getString("senderId").orEmpty(),
                            receiverId = document.getString("receiverId").orEmpty(),
                            createdAtMillis = document.getTimestamp("createdAt").toMillisOrZero(),
                            blocked = document.getString("status") == "blocked"
                        )
                    }
                    .sortedBy { it.createdAtMillis }
                trySend(pairs)
            }

        awaitClose {
            registration?.remove()
            registration = null
        }
    }

    private suspend fun updatePairStatus(pairId: String, status: String): PagerResult = runCatching {
        requireFirebaseConfigured()
        firestore.collection("pairs").document(pairId).update(
            mapOf(
                "status" to status,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        PagerResult.Ok
    }.getOrElse { error ->
        PagerResult.Error(error.message ?: "Could not update connection")
    }

    private fun localDeviceId(): String {
        val prefs = appContext.getSharedPreferences("anonymous_pager", Context.MODE_PRIVATE)
        val existing = prefs.getString("device_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = "device_${UUID.randomUUID().toString().replace("-", "")}"
        prefs.edit().putString("device_id", created).apply()
        return created
    }

    private fun generatePin(): String = (100000 + random.nextInt(900000)).toString()

    private fun qrPayloadFor(code: String): String = "anonpager://redeem?code=$code"

    private fun extractCode(input: String): String {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("^\\d{6}$"))) return trimmed
        val marker = "code="
        val markerIndex = trimmed.indexOf(marker)
        if (markerIndex >= 0) {
            val code = trimmed.substring(markerIndex + marker.length).take(6)
            if (code.matches(Regex("^\\d{6}$"))) return code
        }
        error("Enter a valid six digit code or QR payload")
    }

    private fun isFirebaseConfigured(): Boolean =
        runCatching { FirebaseApp.getInstance() }.isSuccess

    private fun requireFirebaseConfigured() {
        if (!isFirebaseConfigured()) {
            throw MissingFirebaseConfigException()
        }
    }

    private fun Timestamp?.toMillisOrZero(): Long = this?.toDate()?.time ?: 0L

    private companion object {
        const val CODE_TTL_MS = 10 * 60 * 1000L
    }
}

class MissingFirebaseConfigException : IllegalStateException(
    "Firebase setup is missing. Add app/google-services.json from your Firebase project, then rebuild and reinstall."
)
