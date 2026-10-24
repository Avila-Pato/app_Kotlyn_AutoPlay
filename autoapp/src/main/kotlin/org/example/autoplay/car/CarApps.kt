package org.example.autoplay.car

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.example.autoplay.ui.SectionCard

/** Apps the driver often needs next to the music; each must also be listed under <queries> in the manifest. */
private val SHORTCUTS = listOf(
    "com.waze" to "Waze",
    "com.google.android.apps.maps" to "Maps",
    "com.spotify.music" to "Spotify",
    "com.google.android.apps.youtube.music" to "YT Music",
)

private class InstalledApp(val label: String, val icon: ImageBitmap, val launch: Intent)

/**
 * One tap opens maps or another app; AutoPlay keeps playing in the background through [CarService].
 * Only apps installed on this head unit show up, and nothing at all if none is.
 */
@Composable
fun CarAppShortcuts(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apps = remember { installedApps(context) }
    if (apps.isEmpty()) return

    SectionCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            apps.forEach { app ->
                Column(
                    Modifier
                        .width(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { runCatching { context.startActivity(app.launch) } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Image(app.icon, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text(app.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun installedApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    return SHORTCUTS.mapNotNull { (pkg, label) ->
        val launch = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
        val icon = runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() ?: return@mapNotNull null
        // Radios with split screen open the map beside AutoPlay; the rest simply ignore the flag
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        InstalledApp(label, icon, launch)
    }
}
