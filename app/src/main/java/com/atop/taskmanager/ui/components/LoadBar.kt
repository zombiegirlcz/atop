package com.atop.taskmanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Barevný pruh zátěže (jako htop): zelená → žlutá → červená.
 * Bere hodnotu 0..1 a fraction typu "jak moc je plný".
 */
@Composable
fun LoadBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Int = 6
) {
    val target = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, label = "load")
    val color = when {
        target < 0.5f -> Color(0xFF3DD68C)
        target < 0.8f -> Color(0xFFFFC24B)
        else -> Color(0xFFFF6B6B)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .background(color)
        )
    }
}