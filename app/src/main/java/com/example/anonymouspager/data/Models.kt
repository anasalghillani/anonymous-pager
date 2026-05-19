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
    val blocked: Boolean = false
) {
    fun displayName(index: Int): String = "Connection ${index + 1}"
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
