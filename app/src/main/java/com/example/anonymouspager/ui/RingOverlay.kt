package com.example.anonymouspager.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.anonymouspager.R
import com.example.anonymouspager.data.PagerRing
import com.example.anonymouspager.data.pairTag
import com.example.anonymouspager.ui.theme.Space
import com.example.anonymouspager.ui.theme.WenColors
import com.example.anonymouspager.ui.theme.WenType

/**
 * Full-screen incoming ring. Rings are grouped by connection: one Accept / Ignore answers
 * every waiting ring from that person. If several people rang, the newest one is shown first.
 */
@Composable
fun IncomingRingOverlay(rings: List<PagerRing>, onRespond: (String, String) -> Unit) {
    val latest = rings.firstOrNull() ?: return
    val fromSamePerson = rings.filter { it.pairId == latest.pairId }
    val superRing = fromSamePerson.any { it.superRing }
    val otherPeople = rings.map { it.pairId }.distinct().size - 1
    val tag = pairTag(latest.pairId)
    val accent = if (superRing) WenColors.Ember else WenColors.Lcd

    val transition = rememberInfiniteTransition(label = "ring")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (superRing) 900 else 1600, easing = LinearEasing)),
        label = "pulse"
    )
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(if (superRing) 220 else 480), RepeatMode.Reverse),
        label = "blink"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(WenColors.Night)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = if (superRing) 0.28f else 0.14f), WenColors.Night, WenColors.Night)
                )
            )
            // Swallow taps so nothing behind the overlay can be pressed by accident.
            .consumeTaps()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.xl, vertical = Space.xl)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Space.lg))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
                    .padding(horizontal = Space.md, vertical = 6.dp)
            ) {
                Text(
                    stringResource(if (superRing) R.string.incoming_super else R.string.incoming_ring).uppercase(),
                    style = WenType.Readout,
                    color = accent
                )
            }

            Spacer(Modifier.weight(1f))

            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(320.dp)) {
                    val maxR = size.minDimension / 2f
                    for (i in 0 until 3) {
                        val p = (pulse + i / 3f) % 1f
                        drawCircle(
                            color = accent.copy(alpha = (1f - p) * 0.35f),
                            radius = maxR * (0.45f + 0.55f * p),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                PagerDevice(width = 220.dp, lightColor = WenColors.Ember.copy(alpha = blink)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tag, style = WenType.Code, color = WenColors.LcdInk)
                        Text(
                            if (superRing) "!! SUPER !!" else "RING",
                            style = WenType.Readout,
                            color = WenColors.LcdInk.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.xl))
            Text(
                stringResource(R.string.incoming_from, stringResource(R.string.connection_name, tag)),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Assertive }
            )
            if (fromSamePerson.size > 1) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    stringResource(R.string.rings_count, fromSamePerson.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = WenColors.TextMuted
                )
            }

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                WenButton(
                    stringResource(R.string.ignore),
                    onClick = { onRespond(latest.pairId, "ignored") },
                    style = WenButtonStyle.Outline,
                    icon = Icons.Filled.Close,
                    height = 60.dp,
                    modifier = Modifier.weight(1f)
                )
                WenButton(
                    stringResource(R.string.accept),
                    onClick = { onRespond(latest.pairId, "accepted") },
                    style = WenButtonStyle.Primary,
                    icon = Icons.Filled.Check,
                    height = 60.dp,
                    modifier = Modifier.weight(1f)
                )
            }
            if (otherPeople > 0) {
                Spacer(Modifier.height(Space.md))
                Text(
                    stringResource(R.string.more_waiting, otherPeople),
                    style = MaterialTheme.typography.bodyMedium,
                    color = WenColors.TextMuted
                )
            }
        }
    }
}
