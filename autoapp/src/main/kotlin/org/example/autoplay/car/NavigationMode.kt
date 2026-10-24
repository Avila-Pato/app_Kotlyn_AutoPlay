package org.example.autoplay.car

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.util.Rational
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.autoplay.R
import org.example.autoplay.ui.AppColors
import org.example.autoplay.ui.AppIcons
import org.example.autoplay.ui.IconBadge

/**
 * Navigation mode: the map app fills the screen and AutoPlay shrinks to a floating mini player
 * (Android's picture-in-picture) that the driver can drag to any corner. Head units without
 * floating windows get split screen instead.
 */
object NavigationMode {
    private val _floating = MutableStateFlow(false)

    /** True while AutoPlay is the small floating window; set by MainActivity. */
    val floating: StateFlow<Boolean> = _floating.asStateFlow()

    fun onFloatingChanged(floating: Boolean) {
        _floating.value = floating
    }

    fun isSupported(context: Context) = context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Shrinks AutoPlay to the corner, then opens the map full screen behind it. */
    fun start(activity: Activity, map: Intent, playing: Boolean) {
        val floated = isSupported(activity) && runCatching { activity.enterPictureInPictureMode(params(activity, playing)) }.getOrDefault(false)
        val launch = Intent(map).apply {
            // Floating: the map takes the whole screen; otherwise it shares it with AutoPlay
            flags = if (floated) flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT.inv() else flags or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        }
        runCatching { activity.startActivity(launch) }
    }

    /** The mini player's buttons; the play button follows whether the song is playing. */
    fun params(context: Context, playing: Boolean): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .setActions(
            listOf(
                action(context, R.drawable.ic_pip_previous, "Anterior", CarService.ACTION_PREVIOUS),
                if (playing) {
                    action(context, R.drawable.ic_pip_pause, "Pausar", CarService.ACTION_PLAY_PAUSE)
                } else {
                    action(context, R.drawable.ic_pip_play, "Reproducir", CarService.ACTION_PLAY_PAUSE)
                },
                action(context, R.drawable.ic_pip_next, "Siguiente", CarService.ACTION_NEXT),
            ),
        )
        .build()

    private fun action(context: Context, icon: Int, title: String, serviceAction: String): RemoteAction {
        val intent = Intent(context, CarService::class.java).setAction(serviceAction)
        val pending = PendingIntent.getService(context, serviceAction.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
        return RemoteAction(Icon.createWithResource(context, icon), title, title, pending)
    }
}

/** What fits in the floating window: cover and song; Android draws the buttons on top when touched. */
@Composable
fun MiniPlayer(state: CarSessionState) {
    val track = state.track
    Row(
        Modifier.fillMaxSize().padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val art = track?.art
        Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
            if (art != null) {
                val image = remember(art) { art.asImageBitmap() }
                Image(image, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)))
            } else {
                IconBadge(AppIcons.MusicNote, if (state.playing != null) AppColors.Success else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                track?.title ?: state.playing?.name ?: "Sin música",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            track?.artist?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
