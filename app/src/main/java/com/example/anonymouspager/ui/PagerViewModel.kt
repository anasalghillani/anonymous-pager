package com.example.anonymouspager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.anonymouspager.audio.RingPlayer
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
    private var firstRingSnapshot = true
    /** Incoming pair id -> createdAt from the last snapshot; null until the first snapshot arrives. */
    private var knownIncomingPairs: Map<String, Long>? = null

    init {
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
            _uiState.update { it.copy(status = if (superRing) "Sending super ring..." else "Sending ring...") }
            when (val result = repository.sendRing(pairId, superRing)) {
                PagerResult.Ok -> _uiState.update { it.copy(status = if (superRing) "Super ring sent" else "Ring sent") }
                is PagerResult.Error -> _uiState.update { it.copy(status = result.message) }
            }
        }
    }

    fun deletePair(pairId: String) {
        viewModelScope.launch {
            when (val result = repository.deletePair(pairId)) {
                PagerResult.Ok -> _uiState.update { it.copy(status = "Connection deleted") }
                is PagerResult.Error -> _uiState.update { it.copy(status = result.message) }
            }
        }
    }

    fun blockPair(pairId: String) {
        viewModelScope.launch {
            when (val result = repository.blockPair(pairId)) {
                PagerResult.Ok -> _uiState.update { it.copy(status = "Connection blocked") }
                is PagerResult.Error -> _uiState.update { it.copy(status = result.message) }
            }
        }
    }

    fun unblockPair(pairId: String) {
        viewModelScope.launch {
            when (val result = repository.unblockPair(pairId)) {
                PagerResult.Ok -> _uiState.update { it.copy(status = "Connection unblocked") }
                is PagerResult.Error -> _uiState.update { it.copy(status = result.message) }
            }
        }
    }

    fun respondToRing(ringId: String, response: String) {
        viewModelScope.launch {
            ringPlayer.stop()
            when (val result = repository.respondToRing(ringId, response)) {
                PagerResult.Ok -> _uiState.update { state ->
                    state.copy(
                        status = if (response == "accepted") "Ring accepted" else "Ring ignored",
                        incomingRings = state.incomingRings.filterNot { it.id == ringId }
                    )
                }
                is PagerResult.Error -> _uiState.update { it.copy(status = result.message) }
            }
        }
    }

    private fun redeemCode(input: String) {
        if (input.isBlank()) {
            _uiState.update { it.copy(status = "Enter a PIN or scan a QR code") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, status = null) }
            runCatching { repository.redeemCode(input) }
                .onSuccess {
                    _uiState.update {
                        it.copy(loading = false, codeInput = "", status = "Connection saved")
                    }
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
                    _uiState.update { it.copy(loading = false, pairs = pairs) }
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
                    _uiState.update { state ->
                        if (newcomer != null && state.code != null) {
                            state.copy(
                                loading = false,
                                incomingPairs = pairs,
                                code = null,
                                justConnectedTag = newcomer.tag,
                                status = null
                            )
                        } else {
                            state.copy(loading = false, incomingPairs = pairs)
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
                    if (firstRingSnapshot) {
                        // Rings that arrived while the app was closed: show them, but don't replay the sound.
                        firstRingSnapshot = false
                        _uiState.update {
                            it.copy(
                                loading = false,
                                incomingRings = rings,
                                status = if (rings.isNotEmpty()) "${rings.size} missed ring(s)" else it.status
                            )
                        }
                        return@collect
                    }
                    if (freshRings.isNotEmpty()) {
                        val strongest = freshRings.maxBy { if (it.superRing) 1 else 0 }
                        ringPlayer.play(strongest.superRing)
                        _uiState.update {
                            it.copy(
                                loading = false,
                                incomingRings = rings,
                                status = if (strongest.superRing) "Super ring received" else "Someone needs you"
                            )
                        }
                    } else {
                        _uiState.update { it.copy(loading = false, incomingRings = rings) }
                    }
                }
        }
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
    }
}
