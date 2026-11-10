package com.example.anonymouspager.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.anonymouspager.data.PagerPair
import com.example.anonymouspager.data.PagerRing
import com.example.anonymouspager.data.pairTag
import kotlinx.coroutines.delay

private val PagerBlack = Color(0xFF12110E)
private val PagerBody = Color(0xFF1F1D18)
private val PagerEdge = Color(0xFF090806)
private val PagerGold = Color(0xFFC8B36A)
private val PagerRed = Color(0xFFB6312C)
private val PagerGreenButton = Color(0xFFCDD34D)
private val LcdGreen = Color(0xFF9FBE9F)
private val LcdDark = Color(0xFF172015)
private val LcdMuted = Color(0xFF40523A)
private val Mono = FontFamily.Monospace

/** A destructive action waiting for the user to confirm it in a dialog. */
private data class PendingConfirm(val pairId: String, val tag: String, val block: Boolean)

@Composable
fun PagerApp(viewModel: PagerViewModel) {
    val state by viewModel.uiState.collectAsState()
    var pendingConfirm by remember { mutableStateOf<PendingConfirm?>(null) }

    // Phone Back button moves back one screen instead of closing the app.
    BackHandler(enabled = state.screen != PagerScreen.Home) { viewModel.goBack() }

    val tagFor = { pairId: String ->
        (state.pairs + state.incomingPairs).firstOrNull { it.id == pairId }?.tag.orEmpty()
    }
    val confirmDelete = { pairId: String -> pendingConfirm = PendingConfirm(pairId, tagFor(pairId), block = false) }
    val confirmBlock = { pairId: String -> pendingConfirm = PendingConfirm(pairId, tagFor(pairId), block = true) }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = PagerGold,
            secondary = PagerGreenButton,
            background = PagerBlack,
            surface = PagerBody
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(PagerBlack),
            color = PagerBlack
        ) {
            Box(Modifier.fillMaxSize()) {
                PagerDeviceFrame {
                    Box(Modifier.fillMaxSize()) {
                        when (state.screen) {
                            PagerScreen.Home -> HomeScreen(
                                status = state.status,
                                onRing = viewModel::openRing,
                                onBeRinged = viewModel::openBeRinged
                            )

                            PagerScreen.BeRinged -> BeRingedScreen(
                                state = state,
                                onBack = viewModel::goHome,
                                onRefresh = viewModel::createCode,
                                onCodeExpired = viewModel::onCodeExpired,
                                onDelete = confirmDelete,
                                onBlock = confirmBlock,
                                onUnblock = viewModel::unblockPair
                            )

                            PagerScreen.Ring -> RingScreen(
                                state = state,
                                onBack = viewModel::goHome,
                                onCodeChange = viewModel::updateCodeInput,
                                onRedeem = viewModel::redeemTypedCode,
                                onScan = viewModel::openScanner,
                                onRing = viewModel::ring,
                                onSuperRing = viewModel::superRing,
                                onDelete = confirmDelete,
                                onBlock = confirmBlock,
                                onUnblock = viewModel::unblockPair
                            )

                            PagerScreen.Scanner -> ScannerPermissionScreen(
                                onBack = viewModel::openRing,
                                onScanned = viewModel::redeemScannedCode
                            )
                        }

                        if (state.loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x99000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PagerGold)
                            }
                        }
                    }

                    pendingConfirm?.let { pending ->
                        ConfirmDialog(
                            pending = pending,
                            onConfirm = {
                                pendingConfirm = null
                                if (pending.block) viewModel.blockPair(pending.pairId) else viewModel.deletePair(pending.pairId)
                            },
                            onDismiss = { pendingConfirm = null }
                        )
                    }
                }

                // Covers the whole window on every screen, so a ring can be answered wherever the user is.
                IncomingRingOverlay(
                    rings = state.incomingRings,
                    onRespond = viewModel::respondToPair
                )
            }
        }
    }
}

@Composable
private fun PagerDeviceFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PagerBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2B2923), PagerBody, PagerEdge)
                    )
                )
                .border(2.dp, Color(0xFF3B372F), RoundedCornerShape(22.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LcdGreen)
                    .border(3.dp, Color(0xFF050605), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                content()
            }
            Spacer(Modifier.height(18.dp))
            PagerControls()
        }
    }
}

@Composable
private fun PagerControls() {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IndicatorButton(PagerGreenButton)
                IndicatorButton(PagerRed)
            }
            Text(
                "WEN\nوين",
                color = PagerGold,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Right
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundNavButton("<")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RoundNavButton("^", wide = true)
                Spacer(Modifier.height(6.dp))
                RoundNavButton("v", wide = true)
            }
            RoundNavButton(">")
        }
    }
}

@Composable
private fun IndicatorButton(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 74.dp, height = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF12110E))
            .border(1.dp, Color(0xFF302D25), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 8.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color)
        )
    }
}

@Composable
private fun RoundNavButton(label: String, wide: Boolean = false) {
    Box(
        modifier = Modifier
            .size(width = if (wide) 96.dp else 58.dp, height = 44.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF171511))
            .border(1.dp, Color(0xFF322E25), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = PagerGold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HomeScreen(status: String?, onRing: () -> Unit, onBeRinged: () -> Unit) {
    LcdColumn(scrollable = false, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        LcdTitle("WEN · وين")
        Spacer(Modifier.height(10.dp))
        LcdText("NO NAMES / NO NUMBERS\nPRIVATE RING ONLY", textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        PagerActionButton("01: RING SOMEONE", onClick = onRing)
        Spacer(Modifier.height(12.dp))
        PagerSecondaryButton("02: BE RINGED", onClick = onBeRinged)
        StatusText(status)
    }
}

@Composable
private fun BeRingedScreen(
    state: PagerUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCodeExpired: () -> Unit,
    onDelete: (String) -> Unit,
    onBlock: (String) -> Unit,
    onUnblock: (String) -> Unit
) {
    LcdColumn {
        TopBar(title = "BE RINGED", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        val code = state.code
        val connectedTag = state.justConnectedTag
        if (connectedTag != null) {
            LcdCard {
                LcdTitle("CONNECTED!", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                LcdText(
                    "CONN $connectedTag\nYOU CAN NOW RING\nEACH OTHER",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else if (code != null) {
            PagerQrCode(payload = code.qrPayload, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            LcdTitle(code.code, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            CodeCountdown(expiresAtMillis = code.expiresAtMillis, onExpired = onCodeExpired)
        }
        Spacer(Modifier.height(12.dp))
        PagerSecondaryButton("NEW CODE", onClick = onRefresh)
        StatusText(state.status)
        Spacer(Modifier.height(14.dp))
        val activeSenders = state.incomingPairs.filterNot { it.blocked }
        val blockedSenders = state.incomingPairs.filter { it.blocked }
        LcdText("ALLOWED SENDERS")
        if (activeSenders.isEmpty()) {
            LcdText("NONE")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                activeSenders.forEach { pair ->
                    ReceiverPairCard(pair = pair, onDelete = onDelete, onBlock = onBlock)
                }
            }
        }
        if (blockedSenders.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LcdText("BLOCKED SENDERS")
            blockedSenders.forEach { pair ->
                ReceiverPairCard(pair = pair, onDelete = onDelete, onBlock = onBlock, onUnblock = onUnblock)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RingScreen(
    state: PagerUiState,
    onBack: () -> Unit,
    onCodeChange: (String) -> Unit,
    onRedeem: () -> Unit,
    onScan: () -> Unit,
    onRing: (String) -> Unit,
    onSuperRing: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBlock: (String) -> Unit,
    onUnblock: (String) -> Unit
) {
    LcdColumn {
        TopBar(title = "RING SOMEONE", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.codeInput,
            onValueChange = onCodeChange,
            label = { Text("PIN / QR", fontFamily = Mono) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Mono, color = LcdDark, fontSize = 18.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = LcdDark,
                unfocusedTextColor = LcdDark,
                focusedBorderColor = LcdDark,
                unfocusedBorderColor = LcdMuted,
                focusedLabelColor = LcdDark,
                unfocusedLabelColor = LcdMuted,
                cursorColor = LcdDark
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PagerActionButton("SAVE", onClick = onRedeem, modifier = Modifier.weight(1f))
            PagerSecondaryButton("SCAN", onClick = onScan, modifier = Modifier.weight(1f))
        }
        StatusText(state.status)
        Spacer(Modifier.height(14.dp))
        val activePairs = state.pairs.filterNot { it.blocked }
        val blockedPairs = state.pairs.filter { it.blocked }
        LcdText("SAVED CONNECTIONS")
        if (activePairs.isEmpty()) {
            LcdText("NONE")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                activePairs.forEach { pair ->
                    PairCard(pair = pair, onRing = onRing, onSuperRing = onSuperRing, onDelete = onDelete, onBlock = onBlock, onUnblock = onUnblock)
                }
            }
        }
        if (blockedPairs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LcdText("BLOCKED CONNECTIONS")
            blockedPairs.forEach { pair ->
                PairCard(pair = pair, onRing = onRing, onSuperRing = onSuperRing, onDelete = onDelete, onBlock = onBlock, onUnblock = onUnblock)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PairCard(
    pair: PagerPair,
    onRing: (String) -> Unit,
    onSuperRing: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBlock: (String) -> Unit,
    onUnblock: (String) -> Unit
) {
    LcdCard(blocked = pair.blocked) {
        LcdText(if (pair.blocked) "CONN ${pair.tag} [BLOCKED]" else "CONN ${pair.tag}")
        pair.myLastRing?.let { last ->
            // Live: updates as soon as the other person taps Accept/Ignore.
            LcdText(last.label())
        }
        Spacer(Modifier.height(8.dp))
        if (pair.blocked) {
            BlockedPairActions(
                onUnblock = if (pair.blockedByMe) ({ onUnblock(pair.id) }) else null,
                onDelete = { onDelete(pair.id) }
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PagerActionButton("RING", onClick = { onRing(pair.id) }, modifier = Modifier.weight(1f))
                PagerSecondaryButton("SUPER", onClick = { onSuperRing(pair.id) }, modifier = Modifier.weight(1f))
                PagerDangerButton("BLK", onClick = { onBlock(pair.id) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReceiverPairCard(
    pair: PagerPair,
    onDelete: (String) -> Unit,
    onBlock: (String) -> Unit,
    onUnblock: ((String) -> Unit)? = null
) {
    LcdCard(blocked = pair.blocked) {
        LcdText(if (pair.blocked) "SENDER ${pair.tag} [BLOCKED]" else "SENDER ${pair.tag}")
        Spacer(Modifier.height(8.dp))
        if (pair.blocked && onUnblock != null) {
            BlockedPairActions(
                onUnblock = if (pair.blockedByMe) ({ onUnblock(pair.id) }) else null,
                onDelete = { onDelete(pair.id) }
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PagerSecondaryButton("DEL", onClick = { onDelete(pair.id) }, modifier = Modifier.weight(1f))
                PagerDangerButton("BLOCK", onClick = { onBlock(pair.id) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BlockedPairActions(onUnblock: (() -> Unit)?, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onUnblock != null) {
            PagerActionButton("UNBLOCK", onClick = onUnblock, modifier = Modifier.fillMaxWidth())
        }
        PagerSecondaryButton("DELETE", onClick = onDelete, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Full-screen incoming ring. Rings are grouped by connection: one ACCEPT / IGNORE answers
 * every waiting ring from that person. If several people rang, the newest one is shown first.
 */
@Composable
private fun IncomingRingOverlay(rings: List<PagerRing>, onRespond: (String, String) -> Unit) {
    val latest = rings.firstOrNull() ?: return
    val fromSamePerson = rings.filter { it.pairId == latest.pairId }
    val superRing = fromSamePerson.any { it.superRing }
    val otherPeople = rings.map { it.pairId }.distinct().size - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (superRing) PagerRed else LcdGreen)
            // Swallow taps so nothing behind the overlay can be pressed by accident.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LcdTitle(
                if (superRing) "!!! SUPER RING !!!" else "INCOMING RING",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            LcdText("CONN ${pairTag(latest.pairId)}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            if (fromSamePerson.size > 1) {
                LcdText("${fromSamePerson.size} RINGS", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            PagerActionButton(
                "ACCEPT",
                onClick = { onRespond(latest.pairId, "accepted") },
                modifier = Modifier.fillMaxWidth()
            )
            PagerDangerButton(
                "IGNORE",
                onClick = { onRespond(latest.pairId, "ignored") },
                modifier = Modifier.fillMaxWidth()
            )
            if (otherPeople > 0) {
                LcdText("+$otherPeople MORE WAITING", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Live "EXPIRES IN m:ss" line; calls [onExpired] when it reaches zero. Only ticks while the
 * app is visible, so a phone left on this screen in the background doesn't keep making codes.
 */
@Composable
private fun CodeCountdown(expiresAtMillis: Long, onExpired: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(expiresAtMillis, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                if (now >= expiresAtMillis) {
                    onExpired()
                    break
                }
                delay(1_000L - now % 1_000L)
            }
        }
    }
    val secondsLeft = ((expiresAtMillis - now + 999L) / 1_000L).coerceAtLeast(0L)
    LcdText(
        if (secondsLeft > 0) "EXPIRES IN %d:%02d".format(secondsLeft / 60, secondsLeft % 60) else "CODE EXPIRED",
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ConfirmDialog(pending: PendingConfirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val who = pending.tag.ifBlank { "THIS CONNECTION" }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LcdGreen,
        titleContentColor = LcdDark,
        textContentColor = LcdDark,
        title = {
            Text(
                if (pending.block) "BLOCK $who?" else "DELETE $who?",
                fontFamily = Mono,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                if (pending.block) {
                    "Rings on this connection stop. You can unblock it later."
                } else {
                    "This removes the connection. You'll need a new code to pair again."
                },
                fontFamily = Mono
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    if (pending.block) "BLOCK" else "DELETE",
                    color = PagerRed,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = LcdDark, fontFamily = Mono, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun PagerQrCode(payload: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(178.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFB7C9AA))
            .border(3.dp, LcdDark, RoundedCornerShape(4.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        QrCodeImage(payload = payload, modifier = Modifier.size(154.dp))
    }
}

@Composable
private fun ScannerPermissionScreen(onBack: () -> Unit, onScanned: (String) -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraStatus by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        cameraStatus = if (granted) null else "Camera permission denied"
    }

    LcdColumn(scrollable = false) {
        TopBar(title = "SCAN QR", onBack = onBack)
        Spacer(Modifier.height(16.dp))
        if (hasPermission) {
            QrScanner(
                onResult = onScanned,
                modifier = Modifier.fillMaxSize(),
                onError = { cameraStatus = it }
            )
            StatusText(cameraStatus)
        } else {
            LcdText("CAMERA ACCESS ONLY\nFOR QR SCANNING")
            Spacer(Modifier.height(16.dp))
            PagerActionButton("ALLOW CAMERA", onClick = { launcher.launch(Manifest.permission.CAMERA) })
            StatusText(cameraStatus)
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text("BACK", color = LcdDark, fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
        LcdTitle(title, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Spacer(Modifier.size(58.dp))
    }
}

@Composable
private fun LcdColumn(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        modifier = modifier.fillMaxSize().then(scroll),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}

@Composable
private fun LcdTitle(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text,
        modifier = modifier,
        color = LcdDark,
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 30.sp,
        textAlign = textAlign
    )
}

@Composable
private fun LcdText(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text,
        modifier = modifier,
        color = LcdDark,
        fontFamily = Mono,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        textAlign = textAlign
    )
}

@Composable
private fun LcdCard(blocked: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (blocked) Color(0x33B6312C) else Color(0x339FBE9F)),
        border = BorderStroke(if (blocked) 2.dp else 1.dp, if (blocked) PagerRed else LcdMuted),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), content = content)
    }
}

@Composable
private fun PagerActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LcdDark, contentColor = LcdGreen)
    ) {
        Text(text, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PagerSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(2.dp, LcdDark),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LcdDark)
    ) {
        Text(text, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PagerDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(2.dp, PagerRed),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PagerRed)
    ) {
        Text(text, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatusText(status: String?) {
    if (status == null) return
    Spacer(Modifier.height(16.dp))
    LcdText(status.uppercase(), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}
