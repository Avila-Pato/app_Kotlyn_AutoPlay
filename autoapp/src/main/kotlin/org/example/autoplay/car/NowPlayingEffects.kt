package org.example.autoplay.car

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import org.example.autoplay.ui.coverTint

private const val BARS = 4

/**
 * The cover's color, eased in over 0.7 s so a song change fades instead of jumping. Without
 * [enabled] or a cover it settles on [base]. Read the state while drawing, not while composing.
 */
@Composable
fun rememberCoverTint(art: Bitmap?, enabled: Boolean, base: Color): State<Color> {
    val tint = remember(art, enabled) { if (enabled && art != null) coverTint(art) else null }
    return animateColorAsState(tint ?: base, tween(700), label = "coverTint")
}

/** Player card whose background leans from the cover's color into the usual surface. */
@Composable
fun AmbientCard(tint: State<Color>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme.surfaceContainer
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = base),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        // The gradient sits inside the card, so it follows the card's rounded corners
        Box(Modifier.drawBehind { drawRect(Brush.verticalGradient(listOf(tint.value, base))) }.padding(20.dp)) {
            content()
        }
    }
}

/** Little equalizer: it only moves while a song plays; paused, it rests low and costs nothing. */
@Composable
fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier.size(width = 24.dp, height = 18.dp)) {
    if (playing) {
        val transition = rememberInfiniteTransition(label = "equalizer")
        val heights = List(BARS) { index ->
            transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(450 + index * 170, easing = LinearEasing), RepeatMode.Reverse),
                label = "bar$index",
            )
        }
        Canvas(modifier) { drawBars(color, heights.map { it.value }) }
    } else {
        Canvas(modifier) { drawBars(color, List(BARS) { RESTING_HEIGHT }) }
    }
}

private const val RESTING_HEIGHT = 0.25f

private fun DrawScope.drawBars(color: Color, heights: List<Float>) {
    val barWidth = size.width / (BARS * 2 - 1)
    heights.forEachIndexed { index, fraction ->
        val height = size.height * fraction
        drawRoundRect(
            color = color,
            topLeft = Offset(index * 2 * barWidth, size.height - height),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(barWidth / 2),
        )
    }
}
