package com.example.anonymouspager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.anonymouspager.R
import com.example.anonymouspager.ui.theme.Space
import com.example.anonymouspager.ui.theme.WenColors
import com.example.anonymouspager.ui.theme.WenType

/* ---------- Brand ---------- */

/**
 * The pager from the Wen logo, drawn in Compose so it can show live content on its LCD.
 * Proportions follow branding/svg/A-fg.svg (400 x 276 body, 328 x 150 screen).
 */
@Composable
fun PagerDevice(
    modifier: Modifier = Modifier,
    width: Dp = 240.dp,
    lightColor: Color = WenColors.Ember,
    lcdContent: @Composable BoxScope.() -> Unit
) {
    val s = width / 400f
    Box(modifier.width(width).height(290 * s)) {
        // Belt clip on the top edge (kept on the right in both directions, like the logo).
        Box(
            Modifier
                .absoluteOffset(x = 296 * s)
                .size(width = 56 * s, height = 28 * s)
                .clip(RoundedCornerShape(8 * s))
                .background(WenColors.Shell)
        )
        Column(
            Modifier
                .absoluteOffset(y = 14 * s)
                .fillMaxWidth()
                .height(276 * s)
                .clip(RoundedCornerShape(56 * s))
                .background(WenColors.Shell)
                .border(4 * s, WenColors.Outline, RoundedCornerShape(56 * s))
                .padding(start = 36 * s, end = 36 * s, top = 32 * s),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LcdScreen(
                Modifier
                    .fillMaxWidth()
                    .height(150 * s),
                cornerRadius = 22 * s,
                content = lcdContent
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16 * s)) {
                PagerLight(WenColors.Outline, 24 * s)
                PagerLight(lightColor, 24 * s)
                PagerLight(WenColors.Outline, 24 * s)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PagerLight(color: Color, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/** Green LCD panel with faint scan lines, the app's signature surface. */
@Composable
fun LcdScreen(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(WenColors.Lcd)
            .drawBehind {
                val gap = 3.dp.toPx()
                var y = gap
                while (y < size.height) {
                    drawRect(WenColors.LcdInk.copy(alpha = 0.06f), Offset(0f, y), Size(size.width, 1.dp.toPx()))
                    y += gap
                }
            },
        contentAlignment = contentAlignment,
        content = content
    )
}

/** Small QR glyph (Material core icons have no QR icon). */
@Composable
fun QrGlyph(modifier: Modifier = Modifier.size(20.dp), color: Color = Color.Unspecified) {
    val tint = if (color == Color.Unspecified) LocalContentColor.current else color
    Canvas(modifier) {
        val u = size.minDimension / 7f
        val stroke = u * 0.9f
        fun finder(x: Float, y: Float) {
            drawRoundRect(
                tint, Offset(x + stroke / 2, y + stroke / 2), Size(3 * u - stroke, 3 * u - stroke),
                CornerRadius(u * 0.6f), style = Stroke(stroke)
            )
        }
        finder(0f, 0f)
        finder(4 * u, 0f)
        finder(0f, 4 * u)
        drawRect(tint, Offset(4 * u, 4 * u), Size(u, u))
        drawRect(tint, Offset(6 * u, 5 * u), Size(u, u))
        drawRect(tint, Offset(5 * u, 6 * u), Size(u, u))
    }
}

/* ---------- Layout ---------- */

@Composable
fun WenTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Spacer(Modifier.width(Space.xs))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/** Standard screen: top bar plus a scrolling column with the shared side gutter. */
@Composable
fun WenScreen(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        WenTopBar(title, onBack)
        val scroll = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(
            Modifier
                .fillMaxSize()
                .then(scroll)
                .padding(start = Space.screen, end = Space.screen, top = Space.sm, bottom = 96.dp),
            content = content
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, count: Int? = null, topPadding: Dp = Space.xl) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = WenColors.TextMuted,
            modifier = Modifier.semantics { heading() }
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(Space.sm))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(WenColors.SurfaceHigh)
                    .padding(horizontal = Space.sm, vertical = 2.dp)
            ) {
                Text(count.toString(), style = WenType.Tag, color = WenColors.TextMuted)
            }
        }
    }
}

@Composable
fun WenCard(
    modifier: Modifier = Modifier,
    border: Color = WenColors.OutlineSoft,
    background: Color = WenColors.Surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = background,
        border = BorderStroke(1.dp, border)
    ) {
        Column(Modifier.padding(Space.lg), content = content)
    }
}

/** Friendly placeholder for empty lists and missing states. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    lcdText: String = "- - -",
    action: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PagerDevice(width = 120.dp, lightColor = WenColors.Outline) {
            Text(lcdText, style = WenType.Readout, color = WenColors.LcdInk)
        }
        Spacer(Modifier.height(Space.lg))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = WenColors.TextMuted,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(Space.lg))
            action()
        }
    }
}

/** Short id of a connection, in a monospace chip so it reads like a pager number. */
@Composable
fun TagChip(tag: String, modifier: Modifier = Modifier, blocked: Boolean = false) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (blocked) WenColors.SurfaceHigh else WenColors.Lcd),
        contentAlignment = Alignment.Center
    ) {
        Text(
            tag.take(4),
            style = WenType.Tag,
            color = if (blocked) WenColors.TextFaint else WenColors.LcdInk
        )
    }
}

/** Dot plus label, e.g. "● Accepted". */
@Composable
fun StatusDot(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = WenColors.TextMuted)
    }
}

/* ---------- Buttons ---------- */

enum class WenButtonStyle { Primary, Accent, Tonal, Outline, Danger }

@Composable
fun WenButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: WenButtonStyle = WenButtonStyle.Primary,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    height: Dp = 52.dp
) {
    val shape = RoundedCornerShape(14.dp)
    val inner: @Composable RowScope.() -> Unit = {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(Space.sm))
            }
            icon != null -> {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.sm))
            }
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
    when (style) {
        WenButtonStyle.Outline, WenButtonStyle.Danger -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(height),
            shape = shape,
            border = BorderStroke(1.5.dp, if (style == WenButtonStyle.Danger) WenColors.Danger else WenColors.Outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (style == WenButtonStyle.Danger) WenColors.Danger else WenColors.Text
            ),
            content = inner
        )
        else -> Button(
            onClick = onClick,
            modifier = modifier.height(height),
            shape = shape,
            colors = when (style) {
                WenButtonStyle.Accent -> ButtonDefaults.buttonColors(
                    containerColor = WenColors.EmberSoft,
                    contentColor = WenColors.Ember
                )
                WenButtonStyle.Tonal -> ButtonDefaults.buttonColors(
                    containerColor = WenColors.SurfaceHigh,
                    contentColor = WenColors.Text
                )
                else -> ButtonDefaults.buttonColors(
                    containerColor = WenColors.Lcd,
                    contentColor = WenColors.LcdInk
                )
            },
            content = inner
        )
    }
}

/** Big tappable card used for the two main choices on the home screen. */
@Composable
fun ActionTile(
    title: String,
    body: String,
    onClick: () -> Unit,
    highlighted: Boolean,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (highlighted) WenColors.Lcd else WenColors.Surface
    val fg = if (highlighted) WenColors.LcdInk else WenColors.Text
    val sub = if (highlighted) WenColors.LcdInk.copy(alpha = 0.72f) else WenColors.TextMuted
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = bg,
        contentColor = fg,
        border = if (highlighted) null else BorderStroke(1.dp, WenColors.OutlineSoft),
        onClick = onClick
    ) {
        Row(
            Modifier.padding(horizontal = Space.lg, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (highlighted) WenColors.LcdInk.copy(alpha = 0.1f) else WenColors.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) { leading() }
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = sub)
            }
            Spacer(Modifier.width(Space.sm))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = sub
            )
        }
    }
}

/* ---------- Feedback ---------- */

/** Floating message at the bottom of the screen (replaces the old inline LCD status text). */
@Composable
fun StatusToast(status: String?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(status) }
    if (status != null) last = status
    AnimatedVisibility(
        visible = status != null,
        modifier = modifier,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut()
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = WenColors.SurfaceHigh,
            contentColor = WenColors.Text,
            border = BorderStroke(1.dp, WenColors.Outline),
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = Space.screen, vertical = Space.lg)
                .fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = Space.lg, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(WenColors.Lcd)
                )
                Spacer(Modifier.width(Space.md))
                Text(last.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Numbered step row for "How it works". */
@Composable
fun StepRow(number: Int, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(1.5.dp, WenColors.Outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), style = WenType.Tag, color = WenColors.Lcd)
        }
        Spacer(Modifier.width(Space.md))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = WenColors.TextMuted)
    }
}

/** Clickable without ripple, used by full-screen overlays to swallow taps. */
fun Modifier.consumeTaps(): Modifier = this.then(
    Modifier.clickable(
        interactionSource = null,
        indication = null,
        onClick = {}
    )
)
