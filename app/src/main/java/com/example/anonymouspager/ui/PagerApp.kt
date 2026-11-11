package com.example.anonymouspager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.anonymouspager.R
import com.example.anonymouspager.data.LastRing
import com.example.anonymouspager.data.PagerCode
import com.example.anonymouspager.data.PagerPair
import com.example.anonymouspager.ui.theme.Space
import com.example.anonymouspager.ui.theme.WenColors
import com.example.anonymouspager.ui.theme.WenTheme
import com.example.anonymouspager.ui.theme.WenType
import kotlinx.coroutines.delay
import java.util.Date

/** A destructive action waiting for the user to confirm it in a dialog. */
private data class PendingConfirm(val pairId: String, val tag: String, val block: Boolean)

private const val CODE_LIFETIME_SECONDS = 10 * 60

@Composable
fun PagerApp(
    viewModel: PagerViewModel,
    bottomBanner: @Composable () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var pendingConfirm by remember { mutableStateOf<PendingConfirm?>(null) }

    // Phone Back button moves back one screen instead of closing the app.
    BackHandler(enabled = state.screen != PagerScreen.Home) { viewModel.goBack() }

    val tagFor = { pairId: String ->
        (state.pairs + state.incomingPairs).firstOrNull { it.id == pairId }?.tag.orEmpty()
    }
    val confirmDelete = { pairId: String -> pendingConfirm = PendingConfirm(pairId, tagFor(pairId), block = false) }
    val confirmBlock = { pairId: String -> pendingConfirm = PendingConfirm(pairId, tagFor(pairId), block = true) }

    WenTheme {
        Surface(Modifier.fillMaxSize(), color = WenColors.Night) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    AnimatedContent(
                        targetState = state.screen,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                        label = "screen"
                    ) { screen ->
                        when (screen) {
                            PagerScreen.Home -> HomeScreen(
                                state = state,
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

                            PagerScreen.Scanner -> ScannerScreen(
                                onBack = viewModel::openRing,
                                onScanned = viewModel::redeemScannedCode
                            )
                        }
                    }

                    Column(Modifier.align(Alignment.BottomCenter)) {
                        StatusToast(state.status)
                        bottomBanner()
                    }

                    if (state.loading) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(WenColors.Night.copy(alpha = 0.72f))
                                .consumeTaps(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = WenColors.Lcd, strokeWidth = 3.dp)
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

                // Covers the whole window on every screen, so a ring can be answered wherever the user is.
                IncomingRingOverlay(
                    rings = state.incomingRings,
                    onRespond = viewModel::respondToPair
                )
            }
        }
    }
}

/* ---------- Home ---------- */

@Composable
private fun HomeScreen(state: PagerUiState, onRing: () -> Unit, onBeRinged: () -> Unit) {
    val links = state.pairs.count { !it.blocked }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen)
            .padding(top = Space.xxl, bottom = 120.dp)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PagerDevice(width = 248.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "وين؟",
                        color = WenColors.LcdInk,
                        fontSize = 44.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (links == 0) stringResource(R.string.lcd_ready) else stringResource(R.string.lcd_links, links),
                        style = WenType.Readout,
                        color = WenColors.LcdInk.copy(alpha = 0.65f)
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.xxl))
        Text(stringResource(R.string.home_headline), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.sm))
        Text(
            stringResource(R.string.home_subline),
            style = MaterialTheme.typography.bodyLarge,
            color = WenColors.TextMuted
        )

        Spacer(Modifier.height(Space.xl))
        ActionTile(
            title = stringResource(R.string.home_ring_title),
            body = stringResource(R.string.home_ring_body),
            onClick = onRing,
            highlighted = true,
            leading = { Icon(Icons.Filled.Notifications, contentDescription = null) }
        )
        Spacer(Modifier.height(Space.md))
        ActionTile(
            title = stringResource(R.string.home_receive_title),
            body = stringResource(R.string.home_receive_body),
            onClick = onBeRinged,
            highlighted = false,
            leading = { QrGlyph(Modifier.size(22.dp), color = WenColors.Lcd) }
        )

        Spacer(Modifier.height(Space.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = WenColors.TextFaint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                stringResource(R.string.home_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = WenColors.TextFaint
            )
        }
    }
}

/* ---------- Be ringed (share my code) ---------- */

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
    WenScreen(title = stringResource(R.string.be_ringed_title), onBack = onBack) {
        val code = state.code
        val connectedTag = state.justConnectedTag
        when {
            connectedTag != null -> ConnectedCard(tag = connectedTag, onDone = onBack, onNewCode = onRefresh)
            code != null -> CodeCard(code = code, onExpired = onCodeExpired, onRefresh = onRefresh)
            !state.loading -> EmptyState(
                title = stringResource(R.string.no_code_title),
                body = stringResource(R.string.no_code_body),
                lcdText = "----"
            ) {
                WenButton(stringResource(R.string.get_code), onClick = onRefresh, icon = Icons.Filled.Refresh)
            }
        }

        if (connectedTag == null) {
            SectionHeader(stringResource(R.string.how_title))
            StepRow(1, stringResource(R.string.how_1))
            StepRow(2, stringResource(R.string.how_2))
            StepRow(3, stringResource(R.string.how_3))
        }

        val active = state.incomingPairs.filterNot { it.blocked }
        val blocked = state.incomingPairs.filter { it.blocked }
        SectionHeader(stringResource(R.string.section_senders), count = active.size)
        if (active.isEmpty()) {
            Text(
                stringResource(R.string.empty_senders),
                style = MaterialTheme.typography.bodyMedium,
                color = WenColors.TextFaint
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                active.forEach { pair ->
                    ConnectionCard(
                        pair = pair,
                        subtitle = { StatusDot(stringResource(R.string.can_ring_you), WenColors.Lcd) },
                        onDelete = onDelete,
                        onBlock = onBlock,
                        onUnblock = onUnblock
                    )
                }
            }
        }
        BlockedSection(blocked, onDelete = onDelete, onBlock = onBlock, onUnblock = onUnblock)
    }
}

@Composable
private fun CodeCard(code: PagerCode, onExpired: () -> Unit, onRefresh: () -> Unit) {
    WenCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            LcdScreen(Modifier.size(236.dp), cornerRadius = 24.dp) {
                QrCodeImage(
                    payload = code.qrPayload,
                    contentDescription = stringResource(R.string.qr_description),
                    modifier = Modifier.size(204.dp)
                )
            }
            Spacer(Modifier.height(Space.lg))
            Text(code.code, style = WenType.Code, color = WenColors.Text)
            Spacer(Modifier.height(Space.xs))
            Text(
                stringResource(R.string.code_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = WenColors.TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Space.lg))
            CodeCountdown(expiresAtMillis = code.expiresAtMillis, onExpired = onExpired)
            Spacer(Modifier.height(Space.md))
            WenButton(
                stringResource(R.string.new_code),
                onClick = onRefresh,
                style = WenButtonStyle.Tonal,
                icon = Icons.Filled.Refresh,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConnectedCard(tag: String, onDone: () -> Unit, onNewCode: () -> Unit) {
    WenCard(border = WenColors.Lcd.copy(alpha = 0.4f)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(Space.sm))
            LcdScreen(Modifier.size(76.dp), cornerRadius = 38.dp) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = WenColors.LcdInk, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(Space.lg))
            Text(stringResource(R.string.connected_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Space.xs))
            Text(
                stringResource(R.string.connected_body, stringResource(R.string.connection_name, tag)),
                style = MaterialTheme.typography.bodyMedium,
                color = WenColors.TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                WenButton(
                    stringResource(R.string.new_code),
                    onClick = onNewCode,
                    style = WenButtonStyle.Tonal,
                    modifier = Modifier.weight(1f)
                )
                WenButton(stringResource(R.string.done), onClick = onDone, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Live "Expires in m:ss" line with a draining bar; calls [onExpired] when it reaches zero. Only
 * ticks while the app is visible, so a phone left on this screen in the background doesn't keep making codes.
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
    val urgent = secondsLeft < 60
    val barColor = if (urgent) WenColors.Ember else WenColors.Lcd
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                if (secondsLeft > 0) {
                    stringResource(R.string.code_expires_in, "%d:%02d".format(secondsLeft / 60, secondsLeft % 60))
                } else {
                    stringResource(R.string.code_expired)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (urgent) WenColors.Ember else WenColors.TextMuted
            )
        }
        Spacer(Modifier.height(Space.sm))
        LinearProgressIndicator(
            progress = { (secondsLeft.toFloat() / CODE_LIFETIME_SECONDS).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = WenColors.SurfaceHigh,
            strokeCap = StrokeCap.Round
        )
    }
}

/* ---------- Ring someone ---------- */

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
    WenScreen(title = stringResource(R.string.ring_title), onBack = onBack) {
        val active = state.pairs.filterNot { it.blocked }
        val blocked = state.pairs.filter { it.blocked }

        if (active.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_connections_title),
                body = stringResource(R.string.empty_connections_body)
            )
        } else {
            SectionHeader(stringResource(R.string.section_connections), count = active.size, topPadding = 0.dp)
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                active.forEach { pair ->
                    ConnectionCard(
                        pair = pair,
                        subtitle = { LastRingLine(pair.myLastRing) },
                        onDelete = onDelete,
                        onBlock = onBlock,
                        onUnblock = onUnblock
                    ) {
                        Spacer(Modifier.height(Space.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            WenButton(
                                stringResource(R.string.ring),
                                onClick = { onRing(pair.id) },
                                icon = Icons.Filled.Notifications,
                                height = 46.dp,
                                modifier = Modifier.weight(1f)
                            )
                            WenButton(
                                stringResource(R.string.super_ring),
                                onClick = { onSuperRing(pair.id) },
                                style = WenButtonStyle.Accent,
                                height = 46.dp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        SectionHeader(stringResource(R.string.add_title))
        AddConnectionCard(
            codeInput = state.codeInput,
            onCodeChange = onCodeChange,
            onRedeem = onRedeem,
            onScan = onScan
        )

        BlockedSection(blocked, onDelete = onDelete, onBlock = onBlock, onUnblock = onUnblock)
    }
}

@Composable
private fun AddConnectionCard(
    codeInput: String,
    onCodeChange: (String) -> Unit,
    onRedeem: () -> Unit,
    onScan: () -> Unit
) {
    WenCard {
        WenButton(
            stringResource(R.string.scan_qr),
            onClick = onScan,
            leading = { QrGlyph(Modifier.size(20.dp)) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(Modifier.weight(1f), color = WenColors.OutlineSoft)
            Text(
                stringResource(R.string.or_enter_pin),
                style = MaterialTheme.typography.bodyMedium,
                color = WenColors.TextFaint,
                modifier = Modifier.padding(horizontal = Space.md)
            )
            HorizontalDivider(Modifier.weight(1f), color = WenColors.OutlineSoft)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = codeInput,
                onValueChange = onCodeChange,
                placeholder = { Text(stringResource(R.string.pin_label), color = WenColors.TextFaint) },
                singleLine = true,
                textStyle = WenType.Code.copy(fontSize = 18.sp, letterSpacing = 3.sp, color = WenColors.Text),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onRedeem() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WenColors.Lcd,
                    unfocusedBorderColor = WenColors.Outline,
                    cursorColor = WenColors.Lcd,
                    focusedContainerColor = WenColors.Night,
                    unfocusedContainerColor = WenColors.Night
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Space.sm))
            WenButton(
                stringResource(R.string.add),
                onClick = onRedeem,
                style = WenButtonStyle.Tonal,
                icon = Icons.Filled.Add,
                height = 56.dp
            )
        }
    }
}

@Composable
private fun LastRingLine(last: LastRing?) {
    if (last == null) {
        StatusDot(stringResource(R.string.never_rung), WenColors.TextFaint)
        return
    }
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val (stateRes, color) = when {
        last.status == "accepted" -> R.string.ring_status_accepted to WenColors.Lcd
        last.status == "ignored" -> R.string.ring_status_ignored to WenColors.Danger
        last.expiresAtMillis in 1..now -> R.string.ring_status_no_answer to WenColors.TextFaint
        else -> R.string.ring_status_waiting to WenColors.Ember
    }
    val time = DateFormat.getTimeFormat(context).format(Date(last.sentAtMillis))
    val kind = stringResource(if (last.superRing) R.string.kind_super else R.string.kind_ring)
    StatusDot(stringResource(R.string.last_ring, kind, time, stringResource(stateRes)), color)
}

/* ---------- Connection cards (shared) ---------- */

@Composable
private fun ConnectionCard(
    pair: PagerPair,
    subtitle: @Composable () -> Unit,
    onDelete: (String) -> Unit,
    onBlock: (String) -> Unit,
    onUnblock: (String) -> Unit,
    actions: (@Composable () -> Unit)? = null
) {
    val unblockLabel = stringResource(R.string.unblock)
    WenCard(background = if (pair.blocked) WenColors.Night else WenColors.Surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TagChip(pair.tag, blocked = pair.blocked)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.connection_name, pair.tag),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (pair.blocked) WenColors.TextMuted else WenColors.Text
                )
                Spacer(Modifier.height(2.dp))
                if (pair.blocked) {
                    StatusDot(
                        stringResource(if (pair.blockedByMe) R.string.blocked_by_me else R.string.blocked_by_them),
                        WenColors.Danger
                    )
                } else {
                    subtitle()
                }
            }
            val blockLabel = stringResource(R.string.block)
            val deleteLabel = stringResource(R.string.delete)
            OverflowMenu(
                items = buildList {
                    if (!pair.blocked) {
                        add(MenuAction(blockLabel, Icons.Filled.Close, danger = true) { onBlock(pair.id) })
                    }
                    add(MenuAction(deleteLabel, Icons.Filled.Delete, danger = true) { onDelete(pair.id) })
                }
            )
        }
        if (!pair.blocked) actions?.invoke()
        if (pair.blocked && pair.blockedByMe) {
            Spacer(Modifier.height(Space.md))
            WenButton(
                unblockLabel,
                onClick = { onUnblock(pair.id) },
                style = WenButtonStyle.Tonal,
                icon = Icons.Filled.Check,
                height = 44.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BlockedSection(
    blocked: List<PagerPair>,
    onDelete: (String) -> Unit,
    onBlock: (String) -> Unit,
    onUnblock: (String) -> Unit
) {
    if (blocked.isEmpty()) return
    SectionHeader(stringResource(R.string.section_blocked), count = blocked.size)
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        blocked.forEach { pair ->
            ConnectionCard(pair = pair, subtitle = {}, onDelete = onDelete, onBlock = onBlock, onUnblock = onUnblock)
        }
    }
}

private class MenuAction(
    val label: String,
    val icon: ImageVector,
    val danger: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun OverflowMenu(items: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options), tint = WenColors.TextMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                val color = if (item.danger) WenColors.Danger else WenColors.Text
                DropdownMenuItem(
                    text = { Text(item.label, color = color) },
                    leadingIcon = { Icon(item.icon, contentDescription = null, tint = color) },
                    onClick = {
                        open = false
                        item.onClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(pending: PendingConfirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val who = if (pending.tag.isBlank()) {
        stringResource(R.string.this_connection)
    } else {
        stringResource(R.string.connection_name, pending.tag)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WenColors.SurfaceHigh,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                stringResource(if (pending.block) R.string.confirm_block_title else R.string.confirm_delete_title, who),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                stringResource(if (pending.block) R.string.confirm_block_body else R.string.confirm_delete_body),
                style = MaterialTheme.typography.bodyMedium,
                color = WenColors.TextMuted
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(if (pending.block) R.string.block else R.string.delete),
                    color = WenColors.Danger,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = WenColors.Text, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

/* ---------- Scanner ---------- */

@Composable
private fun ScannerScreen(onBack: () -> Unit, onScanned: (String) -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraStatus by remember { mutableStateOf<String?>(null) }
    val deniedText = stringResource(R.string.camera_denied)
    val errorText = stringResource(R.string.camera_error)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        cameraStatus = if (granted) null else deniedText
    }

    WenScreen(title = stringResource(R.string.scan_title), onBack = onBack, scrollable = false) {
        if (hasPermission) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                QrScanner(
                    onResult = onScanned,
                    modifier = Modifier.fillMaxSize(),
                    onError = { cameraStatus = errorText }
                )
                ScanFrame(Modifier.size(240.dp))
            }
            Spacer(Modifier.height(Space.lg))
            Text(
                stringResource(R.string.scan_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = WenColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            EmptyState(
                title = stringResource(R.string.camera_title),
                body = stringResource(R.string.camera_body),
                lcdText = "QR"
            ) {
                WenButton(
                    stringResource(R.string.allow_camera),
                    onClick = { launcher.launch(Manifest.permission.CAMERA) }
                )
            }
        }
        cameraStatus?.let {
            Spacer(Modifier.height(Space.sm))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = WenColors.Danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Four LCD-green corner brackets that frame the QR target area. */
@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val len = size.minDimension * 0.18f
        val stroke = 4.dp.toPx()
        val w = size.width
        val h = size.height
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(WenColors.Lcd, Offset(x, y), Offset(x + dx * len, y), stroke, StrokeCap.Round)
            drawLine(WenColors.Lcd, Offset(x, y), Offset(x, y + dy * len), stroke, StrokeCap.Round)
        }
        corner(0f, 0f, 1f, 1f)
        corner(w, 0f, -1f, 1f)
        corner(0f, h, 1f, -1f)
        corner(w, h, -1f, -1f)
    }
}
