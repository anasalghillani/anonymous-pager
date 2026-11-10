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
    val blockedByMe: Boolean = false
) {
    /** Short tag derived from the pair id, so it never changes when other connections are removed. */
    val tag: String
        get() = Integer.toHexString(id.hashCode() and 0xFFFF).uppercase().padStart(4, '0')
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
