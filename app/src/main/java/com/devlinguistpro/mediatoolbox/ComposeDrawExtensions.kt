package com.devlinguistpro.mediatoolbox

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Compose drawRoundRect overload that also supports a PathEffect.
 * The regular DrawScope.drawRoundRect overloads do not expose pathEffect,
 * so this keeps the scanner's dashed guide rendering type-safe and explicit.
 */
fun DrawScope.drawRoundRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    cornerRadius: CornerRadius,
    alpha: Float = 1f,
    style: Stroke = Stroke(),
    pathEffect: PathEffect? = null
) {
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(offset = topLeft, size = size),
                cornerRadius = cornerRadius
            )
        )
    }
    val resolvedStyle = if (pathEffect == null) {
        style
    } else {
        Stroke(
            width = style.width,
            miter = style.miter,
            cap = style.cap,
            join = style.join,
            pathEffect = pathEffect
        )
    }
    drawPath(
        path = path,
        color = color,
        alpha = alpha,
        style = resolvedStyle
    )
}
