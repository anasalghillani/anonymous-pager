package com.example.anonymouspager.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reads come from Firestore listeners (rules only expose the signed-in user's own data).
 * Every write goes through Cloud Functions, which enforce ownership, blocking and rate limits.
 */
class PagerRepository(context: Context) {
    @Suppress("unused")
    private val appContext = context.applicationContext
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance(REGION) }

    val currentUserId: String
        get() = auth.currentUser?.uid.orEmpty()

    /** Signs in anonymously (no name, email or phone) and registers this device's push token. */
    suspend fun ensureSignedInAndRegistered() {
        ensureSignedIn()
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        if (!token.isNullOrBlank()) {
            registerPushToken(token)
        }
    }

    suspend fun registerPushToken(token: String) {
        if (!isFirebaseConfigured()) return
        runCatching {
            ensureSignedIn()
            call("registerDevice", mapOf("fcmToken" to token))
        }
    }

    suspend fun createCode(): PagerCode {
        ensureSignedIn()
        val data = call("createCode")
        return PagerCode(
            code = data["code"] as String,
            qrPayload = data["qrPayload"] as String,
            expiresAtMillis = (data["expiresAtMillis"] as Number).toLong()
        )
    }

    suspend fun redeemCode(input: String): PagerPair {
        ensureSignedIn()
        val data = call("redeemCode", mapOf("codeOrPayload" to input.trim()))
        return PagerPair(
            id = data["pairId"] as String,
            senderId = data["senderId"] as String,
            receiverId = data["receiverId"] as String,
            createdAtMillis = (data["createdAtMillis"] as Number).toLong()
        )
    }

    suspend fun sendRing(pairId: String, superRing: Boolean = false): PagerResult =
        result("Could not send ring") {
            call("sendRing", mapOf("pairId" to pairId, "superRing" to superRing))
        }

    suspend fun deletePair(pairId: String): PagerResult =
        result("Could not delete connection") { call("deletePair", mapOf("pairId" to pairId)) }

    suspend fun blockPair(pairId: String): PagerResult =
        result("Could not block connection") { call("blockPair", mapOf("pairId" to pairId)) }

    suspend fun unblockPair(pairId: String): PagerResult =
        result("Could not unblock connection") { call("unblockPair", mapOf("pairId" to pairId)) }

    suspend fun respondToRing(receiptId: String, response: String): PagerResult =
        result("Could not respond") {
            call("respondToRing", mapOf("receiptId" to receiptId, "response" to response))
        }

    /** Pairs I made by scanning someone's code. */
    fun savedPairs(): Flow<List<PagerPair>> = pairFlow("senderId")

    /** Pairs made when someone scanned my code. Pairs are two-way, so I can ring these too. */
    fun incomingPairs(): Flow<List<PagerPair>> = pairFlow("receiverId")

    /** Unanswered rings addressed to this device that have not expired yet. */
    fun incomingRings(): Flow<List<PagerRing>> = callbackFlow {
        if (!isFirebaseConfigured() || currentUserId.isBlank()) {
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
                val now = System.currentTimeMillis()
                val rings = snapshot?.documents.orEmpty()
                    .filter { it.getString("status") == "sent" }
                    .map { document ->
                        PagerRing(
                            id = document.id,
                            pairId = document.getString("pairId").orEmpty(),
                            senderId = document.getString("senderId").orEmpty(),
                            receiverId = document.getString("receiverId").orEmpty(),
                            createdAtMillis = document.getTimestamp("createdAt").toMillisOrZero(),
                            superRing = document.getBoolean("superRing") == true,
                            expiresAtMillis = document.getTimestamp("expiresAt").toMillisOrZero()
                        )
                    }
                    .filterNot { it.isExpired(now) }
                    .sortedByDescending { it.createdAtMillis }
                trySend(rings)
            }

        awaitClose {
            registration?.remove()
            registration = null
        }
    }

    private fun pairFlow(field: String): Flow<List<PagerPair>> = callbackFlow {
        if (!isFirebaseConfigured() || currentUserId.isBlank()) {
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
                            blocked = document.getString("status") == "blocked",
                            blockedByMe = document.getString("blockedBy") == currentUserId,
                            myLastRing = lastRingOf(document.get("lastRing.$currentUserId"))
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

    private fun lastRingOf(raw: Any?): LastRing? {
        val map = raw as? Map<*, *> ?: return null
        return LastRing(
            status = map["status"] as? String ?: "sent",
            superRing = map["superRing"] == true,
            sentAtMillis = (map["sentAt"] as? Timestamp).toMillisOrZero(),
            expiresAtMillis = (map["expiresAt"] as? Timestamp).toMillisOrZero()
        )
    }

    private suspend fun ensureSignedIn() {
        requireFirebaseConfigured()
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun call(name: String, data: Map<String, Any> = emptyMap()): Map<String, Any?> {
        val result = try {
            functions.getHttpsCallable(name).call(data).await()
        } catch (error: FirebaseFunctionsException) {
            // Server messages are written for users ("Code has expired.", "Too many rings...").
            throw IllegalStateException(error.message ?: "Something went wrong", error)
        }
        return result.data as? Map<String, Any?> ?: emptyMap()
    }

    private suspend fun result(fallback: String, block: suspend () -> Unit): PagerResult =
        runCatching {
            ensureSignedIn()
            block()
            PagerResult.Ok
        }.getOrElse { error -> PagerResult.Error(error.message ?: fallback) }

    private fun isFirebaseConfigured(): Boolean =
        runCatching { FirebaseApp.getInstance() }.isSuccess

    private fun requireFirebaseConfigured() {
        if (!isFirebaseConfigured()) {
            throw MissingFirebaseConfigException()
        }
    }

    private fun Timestamp?.toMillisOrZero(): Long = this?.toDate()?.time ?: 0L

    private companion object {
        const val REGION = "us-central1"
    }
}

class MissingFirebaseConfigException : IllegalStateException(
    "Firebase setup is missing. Add app/google-services.json from your Firebase project, then rebuild and reinstall."
)
