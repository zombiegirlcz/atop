package com.atop.taskmanager.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.atop.taskmanager.domain.ProcessInfo
import com.atop.taskmanager.domain.SystemStats

/** Jedna řádka seznamu. Long-press → callback pro menu akcí. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProcessRow(
    process: ProcessInfo,
    maxRssKb: Long,
    coreCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cpuFraction = (process.cpuPercent / (100.0 * coreCount)).toFloat()
    val rssFraction = if (maxRssKb <= 0) 0f else (process.rssKb.toFloat() / maxRssKb)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ikona appky (pokud UID patří userspace appce)
            val bmp = remember(process.icon) {
                process.icon?.let { runCatching { it.toBitmap(72, 72).asImageBitmap() }.getOrNull() }
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 0.dp)
                )
            } else {
                // barevný čtvereček podle stavu procesu
                Surface(
                    color = stateColor(process.state),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.size(28.dp)
                ) {}
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = process.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = process.pid.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "CPU ${formatPercent(process.cpuPercent)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "RSS ${formatKb(process.rssKb)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = process.state.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontFamily = FontFamily.Monospace,
                        color = stateColor(process.state)
                    )
                }
            }

            Column(
                modifier = Modifier.width(74.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LoadBar(fraction = cpuFraction)
                LoadBar(fraction = rssFraction)
            }
        }
    }
}

fun stateColor(state: Char) = when (state) {
    'R' -> androidx.compose.ui.graphics.Color(0xFF3DD68C) // running
    'D' -> androidx.compose.ui.graphics.Color(0xFFFFC24B) // uninterruptible
    'Z' -> androidx.compose.ui.graphics.Color(0xFFFF6B6B) // zombie
    'T' -> androidx.compose.ui.graphics.Color(0xFF9AA4C0) // stopped
    else -> androidx.compose.ui.graphics.Color(0xFF4CC2FF) // sleeping
}

fun formatPercent(v: Double): String =
    if (v >= 100) String.format("%.0f%%", v) else String.format("%.1f%%", v)

fun formatKb(kb: Long): String = when {
    kb >= 1024 * 1024 -> String.format("%.1f GB", kb / 1024.0 / 1024.0)
    kb >= 1024 -> String.format("%.0f MB", kb / 1024.0)
    else -> "$kb kB"
}

/** Pomocná pro hlavičku: použije se v ProcessListScreen. */
fun memFraction(stats: SystemStats): Float = stats.memPercent