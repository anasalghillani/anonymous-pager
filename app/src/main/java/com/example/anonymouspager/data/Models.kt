package com.example.anonymouspager.data

data class PagerCode(
    val code: String,
    val qrPayload: String,
    val expiresAtMillis: Long
)

data class PagerPair(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val createdAtMillis: Long,
    val blocked: Boolean = false,
    val blockedByMe: Boolean = false,
    /** The last ring *I* sent on this connection (either side can ring). */
    val myLastRing: LastRing? = null
) {
    /** Short tag derived from the pair id, so it never changes when other connections are removed. */
    val tag: String
        get() = Integer.toHexString(id.hashCode() and 0xFFFF).uppercase().padStart(4, '0')
}

data class LastRing(
    val status: String,
    val superRing: Boolean,
    val sentAtMillis: Long,
    val expiresAtMillis: Long
) {
    /** Short LCD label, e.g. "LAST RING 21:40 ACCEPTED". */
    fun label(nowMillis: Long = System.currentTimeMillis()): String {
        val state = when {
            status == "accepted" -> "ACCEPTED"
            status == "ignored" -> "IGNORED"
            expiresAtMillis in 1..nowMillis -> "NO ANSWER"
            else -> "WAITING..."
        }
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(sentAtMillis))
        val kind = if (superRing) "SUPER" else "RING"
        return "LAST $kind $time $state"
    }
}

data class PagerRing(
    val id: String,
    val pairId: String,
    val senderId: String,
    val receiverId: String,
    val createdAtMillis: Long,
    val superRing: Boolean = false
)

sealed interface PagerResult {
    data object Ok : PagerResult
    data class Error(val message: String) : PagerResult
}
