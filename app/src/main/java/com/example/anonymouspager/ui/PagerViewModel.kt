package com.example.anonymouspager.ui

import android.app.Application
import androidx.core.app.NotificationManagerCompat
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    private var statusClearJob: Job? = null

    init {
        // Drop rings that expire while the app is open (the Firestore listener only fires on changes).
        viewModelScope.launch {
            while (true) {
                delay(EXPIRY_CHECK_MS)
                removeExpiredRings()
            }
        }
        viewModelScope.launch {
            runCatching {
                repository.ensureSignedInAndRegistered()
                listenForPairs()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(loading = false, status = firebaseStatus(error, "Could not start private session"))
                }
            }
        }
    }

    fun goHome() {
        _uiState.update { it.copy(screen = PagerScreen.Home, status = null) }
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
            _uiState.update { it.copy(loading = true, screen = PagerScreen.BeRinged, status = null) }
            runCatching { repository.createCode() }
                .onSuccess { code ->
                    _uiState.update { it.copy(loading = false, code = code) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(loading = false, status = firebaseStatus(error, "Could not create a code"))
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
        val notifications = NotificationManagerCompat.from(getApplication<Application>())
        // Same id the push service uses when it shows a ring notification.
        rings.forEach { notifications.cancel(it.id.hashCode()) }
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
                .catch { error ->
                    _uiState.update {
                        it.copy(loading = false, status = firebaseStatus(error, "Could not load connections"))
                    }
                }
                .collect { pairs ->
                    _uiState.update { it.copy(loading = false, pairs = pairs) }
                }
        }
        incomingPairListener?.cancel()
        incomingPairListener = viewModelScope.launch {
            repository.incomingPairs()
                .catch { error ->
                    _uiState.update {
                        it.copy(loading = false, status = firebaseStatus(error, "Could not load incoming connections"))
                    }
                }
                .collect { pairs ->
                    _uiState.update { it.copy(loading = false, incomingPairs = pairs) }
                }
        }
        ringListener?.cancel()
        ringListener = viewModelScope.launch {
            repository.incomingRings()
                .catch { error ->
                    _uiState.update {
                        it.copy(loading = false, status = firebaseStatus(error, "Could not load rings"))
                    }
                }
                .collect { rings ->
                    val freshRings = rings.filterNot { it.id in seenRingIds }
                    seenRingIds.addAll(rings.map { it.id })
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

    private companion object {
        const val STATUS_VISIBLE_MS = 4_000L
        const val EXPIRY_CHECK_MS = 5_000L
    }

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
}
