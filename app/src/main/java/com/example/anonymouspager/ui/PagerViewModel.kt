package com.example.anonymouspager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymouspager.audio.RingPlayer
import com.example.anonymouspager.push.NotificationHelper
import com.example.anonymouspager.data.PagerCode
import com.example.anonymouspager.data.PagerPair
import com.example.anonymouspager.data.PagerRing
import com.example.anonymouspager.data.PagerRepository
import com.example.anonymouspager.data.PagerResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PagerUiState(
    val loading: Boolean = true,
    val screen: PagerScreen = PagerScreen.Home,
    val code: PagerCode? = null,
    /** Every connection I can ring: ones I scanned plus ones that scanned me (two-way). */
    val pairs: List<PagerPair> = emptyList(),
    val incomingPairs: List<PagerPair> = emptyList(),
    val incomingRings: List<PagerRing> = emptyList(),
    val codeInput: String = "",
    /** Tag of the sender who just scanned our code (shows "CONNECTED!" on Be Ringed). */
    val justConnectedTag: String? = null,
    val status: String? = null
)

enum class PagerScreen {
    Home,
    BeRinged,
    Ring,
    Scanner
}

class PagerViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository: PagerRepository = PagerRepository(application)
    private val ringPlayer = RingPlayer(application)
    private val _uiState = MutableStateFlow(PagerUiState())
    val uiState: StateFlow<PagerUiState> = _uiState.asStateFlow()
    private var pairListener: Job? = null
    private var incomingPairListener: Job? = null
    private var ringListener: Job? = null
    private val seenRingIds = mutableSetOf<String>()
    private var outgoingPairs: List<PagerPair> = emptyList()
    private var incomingPairsCache: List<PagerPair> = emptyList()
    private var firstRingSnapshot = true
    private var statusClearJob: Job? = null
    /** Incoming pair id -> createdAt from the last snapshot; null until the first snapshot arrives. */
    private var knownIncomingPairs: Map<String, Long>? = null

    init {
        // Drop rings that expire while the app is open (the Firestore listener only fires on changes).
        viewModelScope.launch {
            while (true) {
                delay(EXPIRY_CHECK_MS)
                removeExpiredRings()
            }
        }
        viewModelScope.launch {
            // Keep retrying sign-in (e.g. app opened offline) instead of giving up until restart.
            var attempt = 0
            while (true) {
                val started = runCatching { repository.ensureSignedInAndRegistered() }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(loading = false, status = firebaseStatus(error, "Could not start private session"))
                        }
                    }
                    .isSuccess
                if (started) break
                delay(backoffMillis(attempt++))
            }
            listenForPairs()
        }
    }

    /** Phone Back button: Scanner goes back to Ring, everything else back to Home. */
    fun goBack() {
        when (_uiState.value.screen) {
            PagerScreen.Scanner -> openRing()
            PagerScreen.Home -> Unit
            else -> goHome()
        }
    }

    fun goHome() {
        _uiState.update { it.copy(screen = PagerScreen.Home, status = null, justConnectedTag = null) }
    }

    /** Called by the Be Ringed countdown when the code runs out: fetch a fresh one. */
    fun onCodeExpired() {
        val state = _uiState.value
        val code = state.code ?: return
        if (code.expiresAtMillis > System.currentTimeMillis()) return
        if (state.screen == PagerScreen.BeRinged && !state.loading) {
            createCode()
        } else {
            _uiState.update { it.copy(code = null) }
        }
    }

    /** Opens "Be Ringed" and only makes a new code when there is none or it has expired. */
    fun openBeRinged() {
        val code = _uiState.value.code
        if (code != null && code.expiresAtMillis > System.currentTimeMillis()) {
            _uiState.update { it.copy(screen = PagerScreen.BeRinged, status = null) }
        } else {
            createCode()
        }
    }

    fun openRing() {
        _uiState.update { it.copy(screen = PagerScreen.Ring, status = null) }
    }

    fun openScanner() {
        _uiState.update { it.copy(screen = PagerScreen.Scanner, status = null) }
    }

    fun updateCodeInput(value: String) {
        _uiState.update { it.copy(codeInput = value.take(80), status = null) }
    }

    fun createCode() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(loading = true, screen = PagerScreen.BeRinged, status = null, justConnectedTag = null)
            }
            runCatching { repository.createCode() }
                .onSuccess { code ->
                    // Countdown runs on the device clock; cap it at the code lifetime so a
                    // phone clock that is behind the server never shows more than 10 minutes.
                    val now = System.currentTimeMillis()
                    val remaining = (code.expiresAtMillis - now).coerceIn(0L, CODE_TTL_MILLIS)
                    _uiState.update {
                        it.copy(loading = false, code = code.copy(expiresAtMillis = now + remaining))
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loading = false, code = null, status = firebaseStatus(error, "Could not create a code"))
                    }
                }
        }
    }

    fun redeemTypedCode() {
        redeemCode(_uiState.value.codeInput)
    }

    fun redeemScannedCode(payload: String) {
        _uiState.update { it.copy(screen = PagerScreen.Ring, codeInput = payload) }
        redeemCode(payload)
    }

    fun ring(pairId: String) {
        sendRing(pairId, superRing = false)
    }

    fun superRing(pairId: String) {
        sendRing(pairId, superRing = true)
    }

    private fun sendRing(pairId: String, superRing: Boolean) {
        viewModelScope.launch {
            showStatus(if (superRing) "Sending super ring..." else "Sending ring...", autoClear = false)
            when (val result = repository.sendRing(pairId, superRing)) {
                PagerResult.Ok -> showStatus(if (superRing) "Super ring sent" else "Ring sent")
                is PagerResult.Error -> showStatus(result.message)
            }
        }
    }

    fun deletePair(pairId: String) {
        viewModelScope.launch {
            when (val result = repository.deletePair(pairId)) {
                PagerResult.Ok -> showStatus("Connection deleted")
                is PagerResult.Error -> showStatus(result.message)
            }
        }
    }

    fun blockPair(pairId: String) {
        viewModelScope.launch {
            when (val result = repository.blockPair(pairId)) {
                PagerResult.Ok -> showStatus("Connection blocked")
                is PagerResult.Error -> showStatus(result.message)
            }
        }
    }

    fun unblockPair(pairId: String) {
        viewModelScope.launch {
            when (val result = repository.unblockPair(pairId)) {
                PagerResult.Ok -> showStatus("Connection unblocked")
                is PagerResult.Error -> showStatus(result.message)
            }
        }
    }

    /**
     * Answers every waiting ring from one connection at once.
     * The server marks all of that person's unanswered rings with the same response.
     */
    fun respondToPair(pairId: String, response: String) {
        val answered = _uiState.value.incomingRings.filter { it.pairId == pairId }
        val latest = answered.firstOrNull() ?: return
        ringPlayer.stop()
        cancelRingNotifications(answered)
        _uiState.update { state ->
            state.copy(incomingRings = state.incomingRings.filterNot { it.pairId == pairId })
        }
        viewModelScope.launch {
            when (val result = repository.respondToRing(latest.id, response)) {
                PagerResult.Ok -> showStatus(if (response == "accepted") "Ring accepted" else "Ring ignored")
                is PagerResult.Error -> {
                    // Put the rings back so the user can try again.
                    _uiState.update { state ->
                        state.copy(
                            incomingRings = (state.incomingRings + answered)
                                .distinctBy { it.id }
                                .filterNot { it.isExpired(System.currentTimeMillis()) }
                                .sortedByDescending { it.createdAtMillis }
                        )
                    }
                    showStatus(result.message)
                }
            }
        }
    }

    private fun removeExpiredRings() {
        val now = System.currentTimeMillis()
        val rings = _uiState.value.incomingRings
        val expired = rings.filter { it.isExpired(now) }
        if (expired.isEmpty()) return
        cancelRingNotifications(expired)
        _uiState.update { state -> state.copy(incomingRings = state.incomingRings.filterNot { it.isExpired(now) }) }
        if (_uiState.value.incomingRings.isEmpty()) ringPlayer.stop()
    }

    private fun cancelRingNotifications(rings: List<PagerRing>) {
        // Same id the push service uses when it shows a ring notification.
        rings.forEach { NotificationHelper.cancelRing(getApplication<Application>(), it.id) }
    }

    /** Shows a short message; it disappears by itself after a few seconds unless [autoClear] is false. */
    private fun showStatus(message: String?, autoClear: Boolean = true) {
        statusClearJob?.cancel()
        _uiState.update { it.copy(status = message) }
        if (message != null && autoClear) {
            statusClearJob = viewModelScope.launch {
                delay(STATUS_VISIBLE_MS)
                _uiState.update { if (it.status == message) it.copy(status = null) else it }
            }
        }
    }

    private fun redeemCode(input: String) {
        if (input.isBlank()) {
            showStatus("Enter a PIN or scan a QR code")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, status = null) }
            runCatching { repository.redeemCode(input) }
                .onSuccess {
                    _uiState.update { it.copy(loading = false, codeInput = "") }
                    showStatus("Connection saved")
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loading = false, status = firebaseStatus(error, "Could not save connection"))
                    }
                }
        }
    }

    private fun listenForPairs() {
        pairListener?.cancel()
        pairListener = viewModelScope.launch {
            repository.savedPairs()
                .resilient("Could not load connections")
                .collect { pairs ->
                    outgoingPairs = pairs
                    publishPairs()
                }
        }
        incomingPairListener?.cancel()
        incomingPairListener = viewModelScope.launch {
            repository.incomingPairs()
                .resilient("Could not load incoming connections")
                .collect { pairs ->
                    val previous = knownIncomingPairs
                    knownIncomingPairs = pairs.associate { it.id to it.createdAtMillis }
                    // A pair that is new (or re-created) since the last snapshot means someone
                    // just used our code. Codes are single-use, so drop the shown QR.
                    val newcomer = previous?.let { known ->
                        pairs.filter { !it.blocked && known[it.id] != it.createdAtMillis }
                            .maxByOrNull { it.createdAtMillis }
                    }
                    incomingPairsCache = pairs
                    publishPairs()
                    if (newcomer != null) {
                        _uiState.update { state ->
                            if (state.code != null) {
                                state.copy(code = null, justConnectedTag = newcomer.tag, status = null)
                            } else {
                                state
                            }
                        }
                    }
                }
        }
        ringListener?.cancel()
        ringListener = viewModelScope.launch {
            repository.incomingRings()
                .resilient("Could not load rings")
                .collect { rings ->
                    val freshRings = rings.filterNot { it.id in seenRingIds }
                    seenRingIds.addAll(rings.map { it.id })
                    // Rings answered elsewhere (one answer clears all stacked rings from a person):
                    // drop their leftover notifications too.
                    val currentIds = rings.map { it.id }.toSet()
                    cancelRingNotifications(_uiState.value.incomingRings.filterNot { it.id in currentIds })
                    // The full-screen ring overlay shows these, so no status text is needed here.
                    _uiState.update { it.copy(loading = false, incomingRings = rings) }
                    if (firstRingSnapshot) {
                        // Rings that arrived while the app was closed: show them, but don't replay the sound.
                        firstRingSnapshot = false
                        return@collect
                    }
                    if (freshRings.isNotEmpty()) {
                        ringPlayer.play(freshRings.any { it.superRing })
                    } else if (rings.isEmpty()) {
                        // Answered elsewhere (e.g. from the notification): stop the sound.
                        ringPlayer.stop()
                    }
                }
        }
    }

    private fun publishPairs() {
        val all = (outgoingPairs + incomingPairsCache).distinctBy { it.id }.sortedBy { it.createdAtMillis }
        _uiState.update { it.copy(loading = false, pairs = all, incomingPairs = incomingPairsCache) }
    }

    /**
     * Live lists used to stop for good after one listener error (e.g. a dropped connection
     * or an expired session). Now they show the error, wait, re-check sign-in and re-attach.
     */
    private fun <T> Flow<T>.resilient(fallback: String): Flow<T> {
        var shownError: String? = null
        var failures = 0
        return onEach {
            failures = 0 // a fresh snapshot arrived, so the next drop retries quickly again
            val error = shownError ?: return@onEach
            shownError = null
            // Back online: clear our error message unless something newer replaced it.
            _uiState.update { state -> if (state.status == error) state.copy(status = null) else state }
        }.retryWhen { error, _ ->
            val message = firebaseStatus(error, fallback)
            shownError = message
            _uiState.update { it.copy(loading = false, status = message) }
            delay(backoffMillis(failures++))
            runCatching { repository.ensureSignedInAndRegistered() }
            true
        }
    }

    private fun backoffMillis(attempt: Int): Long =
        (2_000L shl attempt.coerceAtMost(4)).coerceAtMost(30_000L)

    private fun firebaseStatus(error: Throwable, fallback: String): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                "Anonymous sign-in is not enabled in Firebase Auth."
            message.contains("UNAVAILABLE", ignoreCase = true) ||
                message.contains("network", ignoreCase = true) ->
                "No connection. Check your internet and try again."
            message.isNotBlank() -> message
            else -> fallback
        }
    }

    private companion object {
        const val CODE_TTL_MILLIS = 10 * 60 * 1000L
        const val STATUS_VISIBLE_MS = 4_000L
        const val EXPIRY_CHECK_MS = 5_000L
    }
}
