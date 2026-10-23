package com.garud.security.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garud.security.ui.theme.GarudForestGreen
import com.garud.security.ui.theme.GarudAmber

@Composable
fun GarudShieldLogo(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    shieldColor: Color = GarudForestGreen,
    accentColor: Color = GarudAmber
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height

            // Outer shield path
            val shieldPath = Path().apply {
                moveTo(w * 0.5f, h * 0.05f)
                // Top right crest
                cubicTo(w * 0.8f, h * 0.05f, w * 0.95f, h * 0.15f, w * 0.95f, h * 0.35f)
                // Down to bottom tip
                cubicTo(w * 0.95f, h * 0.65f, w * 0.75f, h * 0.85f, w * 0.5f, h * 0.95f)
                // Up through left
                cubicTo(w * 0.25f, h * 0.85f, w * 0.05f, h * 0.65f, w * 0.05f, h * 0.35f)
                cubicTo(w * 0.05f, h * 0.15f, w * 0.2f, h * 0.05f, w * 0.5f, h * 0.05f)
                close()
            }

            // Draw shield background
            drawPath(
                path = shieldPath,
                color = shieldColor,
                style = Fill
            )

            // Inner eagle wing / protection crest
            val innerWingPath = Path().apply {
                moveTo(w * 0.5f, h * 0.22f)
                lineTo(w * 0.78f, h * 0.38f)
                lineTo(w * 0.68f, h * 0.48f)
                lineTo(w * 0.5f, h * 0.38f)
                lineTo(w * 0.32f, h * 0.48f)
                lineTo(w * 0.22f, h * 0.38f)
                close()
            }

            drawPath(
                path = innerWingPath,
                color = accentColor,
                style = Fill
            )

            // Core safety vertical spine
            val spinePath = Path().apply {
                moveTo(w * 0.5f, h * 0.25f)
                lineTo(w * 0.5f, h * 0.82f)
            }

            drawPath(
                path = spinePath,
                color = Color.White.copy(alpha = 0.85f),
                style = Stroke(width = w * 0.04f)
            )

            // Outer metallic rim
            drawPath(
                path = shieldPath,
                color = Color.White.copy(alpha = 0.25f),
                style = Stroke(width = w * 0.03f)
            )
        }
    }
}
