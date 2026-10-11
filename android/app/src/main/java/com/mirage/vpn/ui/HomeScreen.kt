package com.mirage.vpn.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirage.vpn.MirageVpnService
import com.mirage.vpn.data.Profile
import java.util.Locale

@Composable
fun HomeScreen(
    status: Int,
    message: String?,
    metrics: LongArray,
    samples: List<Float>,
    protocol: String,
    profile: Profile?,
    onPower: () -> Unit,
    onServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Hero(status, message, protocol, profile, onPower)
        if (status == MirageVpnService.STATUS_CONNECTED) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val m = if (metrics.size >= 5) metrics else LongArray(5)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Uptime", formatUptime(m[4]), Modifier.weight(1f))
                    MetricTile("Down", formatBytes(m[1]), Modifier.weight(1f))
                    MetricTile("Up", formatBytes(m[0]), Modifier.weight(1f))
                }
                Sparkline(samples)
            }
        }
        ServerRow(profile, onServer)
    }
}

@Composable
private fun Hero(status: Int, message: String?, protocol: String, profile: Profile?, onPower: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        MirageVpnService.STATUS_CONNECTED -> scheme.primary to scheme.onPrimary
        MirageVpnService.STATUS_ERROR -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    val busy = status == MirageVpnService.STATUS_CONNECTING
    val title = when (status) {
        MirageVpnService.STATUS_CONNECTING -> "Connecting…"
        MirageVpnService.STATUS_CONNECTED -> "Connected"
        MirageVpnService.STATUS_ERROR -> "Couldn't connect"
        else -> "Not connected"
    }
    val host = profile?.host.orEmpty()
    val detail = when (status) {
        MirageVpnService.STATUS_CONNECTING -> message?.takeIf { it.isNotBlank() } ?: "Reaching $host"
        MirageVpnService.STATUS_CONNECTED ->
            "Protected" + (if (protocol.isNotBlank()) " · ${protocol.uppercase(Locale.ROOT)}" else "") + " to $host"
        MirageVpnService.STATUS_ERROR -> message?.takeIf { it.isNotBlank() } ?: "Check the address and your login."
        else -> if (profile == null) "Add a server to get started" else "Tap to connect"
    }
    val action = if (status == MirageVpnService.STATUS_CONNECTING || status == MirageVpnService.STATUS_CONNECTED) "Disconnect" else "Connect"

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                val spin = rememberInfiniteTransition(label = "spin")
                val angle by spin.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
                    label = "angle",
                )
                val ring = scheme.primary
                Canvas(Modifier.size(148.dp)) {
                    val w = 2.dp.toPx()
                    drawArc(
                        color = ring,
                        startAngle = angle,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                        style = Stroke(width = w, cap = StrokeCap.Round),
                    )
                }
            }
            Box(
                Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(onClickLabel = action, role = Role.Button, onClick = onPower)
                    .semantics { contentDescription = action },
                contentAlignment = Alignment.Center,
            ) {
                PowerGlyph(fg)
            }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = if (status == MirageVpnService.STATUS_ERROR) scheme.error else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Throughput over the last 40 one-second samples. */
@Composable
private fun Sparkline(samples: List<Float>) {
    val color = MaterialTheme.colorScheme.primary
    val padded = List((SAMPLES - samples.size).coerceAtLeast(0)) { 0f } + samples.takeLast(SAMPLES)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics { contentDescription = "Throughput over the last 40 seconds" },
    ) {
        val w = size.width
        val h = size.height
        val max = padded.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val step = w / (padded.size - 1)
        val line = Path()
        padded.forEachIndexed { i, v ->
            val x = i * step
            val y = h - 3.dp.toPx() - v / max * (h - 8.dp.toPx())
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.15f))
        drawPath(line, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = padded.last()
        drawCircle(color, 2.6.dp.toPx(), Offset(w, h - 3.dp.toPx() - last / max * (h - 8.dp.toPx())))
    }
}

private const val SAMPLES = 40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerRow(profile: Profile?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (profile != null) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { GlobeGlyph(scheme.onPrimaryContainer) }
            } else {
                val dash = scheme.outline
                Box(
                    Modifier
                        .size(40.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = dash,
                                cornerRadius = CornerRadius(12.dp.toPx()),
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                                ),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Add, contentDescription = null, tint = scheme.onSurfaceVariant) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    profile?.host ?: "Add a server",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    profile?.summary ?: "Address and login from your provider",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

internal fun formatUptime(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

internal fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.coerceAtLeast(0).toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return if (i == 0) "${value.toLong()} B" else String.format(Locale.US, "%.1f %s", value, units[i])
}
