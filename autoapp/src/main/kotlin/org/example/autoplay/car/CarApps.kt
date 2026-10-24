package org.example.autoplay.car

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.example.autoplay.ui.AppIcons
import org.example.autoplay.ui.SectionCard

/** Apps the driver often needs next to the music; each must also be listed under <queries> in the manifest. */
private val SHORTCUTS = listOf(
    Shortcut("com.waze", "Waze", navigation = true),
    Shortcut("com.google.android.apps.maps", "Maps", navigation = true),
    Shortcut("com.spotify.music", "Spotify"),
    Shortcut("com.google.android.apps.youtube.music", "YT Music"),
)

private class Shortcut(val pkg: String, val label: String, val navigation: Boolean = false)

private class InstalledApp(val label: String, val icon: ImageBitmap, val launch: Intent, val navigation: Boolean)

/**
 * One tap opens maps or another app; AutoPlay keeps playing in the background through [CarService].
 * Only apps installed on this head unit show up, and nothing at all if none is.
 */
@Composable
fun CarAppShortcuts(playing: Boolean, modifier: Modifier = Modifier) {
    val apps = rememberInstalledApps()
    if (apps.isEmpty()) return

    val open = rememberAppOpener()
    SectionCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            NavigationButton(apps, playing, showLabel = true)
            apps.forEach { AppButton(it, showLabel = true, open) }
        }
    }
}

/** The same shortcuts as a side bar, for the dashboard on wide head units. */
@Composable
fun CarAppDock(playing: Boolean, modifier: Modifier = Modifier) {
    val apps = rememberInstalledApps()
    if (apps.isEmpty()) return

    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val open = rememberAppOpener()
        NavigationButton(apps, playing, showLabel = false)
        apps.forEach { AppButton(it, showLabel = false, open) }
    }
}

@Composable
private fun rememberInstalledApps(): List<InstalledApp> {
    val context = LocalContext.current
    return remember { installedApps(context) }
}

/**
 * Opens an app. The first time, it asks Android to let AutoPlay run in the background
 * (some radios kill it to "save battery", which would cut the music behind the map),
 * and opens the app as soon as the driver answers.
 */
@Composable
private fun rememberAppOpener(
    launch: (Context, InstalledApp) -> Unit = { context, app -> runCatching { context.startActivity(app.launch) } },
): (InstalledApp) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<InstalledApp?>(null) }
    val battery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pending?.let { app -> launch(context, app) }
        pending = null
    }
    return { app ->
        if (needsBatteryRequest(context)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED_BATTERY, true).apply()
            pending = app
            @SuppressLint("BatteryLife") // the car side is a media player that must survive in the background
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            // Radios without that screen: just open the app
            runCatching { battery.launch(request) }.onFailure {
                pending = null
                launch(context, app)
            }
        } else {
            launch(context, app)
        }
    }
}

private fun needsBatteryRequest(context: Context): Boolean {
    if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED_BATTERY, false)) return false
    val power = context.getSystemService(PowerManager::class.java) ?: return false
    return !power.isIgnoringBatteryOptimizations(context.packageName)
}

private const val PREFS = "car"
private const val KEY_ASKED_BATTERY = "asked_battery"

@Composable
private fun AppButton(app: InstalledApp, showLabel: Boolean, open: (InstalledApp) -> Unit) {
    Column(
        Modifier
            .width(if (showLabel) 72.dp else 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { open(app) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(app.icon, contentDescription = app.label, modifier = Modifier.size(48.dp))
        if (showLabel) Text(app.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * "Navegar": the map full screen with AutoPlay as a floating mini player in a corner.
 * With both Waze and Maps installed the driver picks one.
 */
@Composable
private fun NavigationButton(apps: List<InstalledApp>, playing: Boolean, showLabel: Boolean) {
    val maps = apps.filter { it.navigation }
    if (maps.isEmpty()) return
    val activity = LocalContext.current as? Activity ?: return
    val navigate = rememberAppOpener { _, app -> NavigationMode.start(activity, app.launch, playing) }
    var choosing by remember { mutableStateOf(false) }

    Box {
        Column(
            Modifier
                .width(if (showLabel) 72.dp else 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { if (maps.size == 1) navigate(maps.first()) else choosing = true },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Navigation, contentDescription = "Modo navegación", tint = MaterialTheme.colorScheme.onPrimary)
            }
            if (showLabel) Text("Navegar", style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
            maps.forEach { app ->
                DropdownMenuItem(
                    text = { Text(app.label) },
                    leadingIcon = { Image(app.icon, contentDescription = null, modifier = Modifier.size(28.dp)) },
                    onClick = {
                        choosing = false
                        navigate(app)
                    },
                )
            }
        }
    }
}

private fun installedApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    return SHORTCUTS.mapNotNull { shortcut ->
        val launch = pm.getLaunchIntentForPackage(shortcut.pkg) ?: return@mapNotNull null
        val icon = runCatching { pm.getApplicationIcon(shortcut.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            ?: return@mapNotNull null
        // Radios with split screen open the app beside AutoPlay; the rest simply ignore the flag
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        InstalledApp(shortcut.label, icon, launch, shortcut.navigation)
    }
}
