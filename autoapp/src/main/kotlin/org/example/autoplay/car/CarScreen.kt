package org.example.autoplay.car

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.example.autoplay.net.MediaAction
import org.example.autoplay.session.SessionCode
import org.example.autoplay.ui.AppColors
import org.example.autoplay.ui.AppIcons
import org.example.autoplay.ui.CodeDigits
import org.example.autoplay.ui.DeviceActions
import org.example.autoplay.ui.DeviceList
import org.example.autoplay.ui.IconBadge
import org.example.autoplay.ui.QrCode
import org.example.autoplay.ui.ScreenHeader
import org.example.autoplay.ui.SectionCard
import org.example.autoplay.ui.StatusChip
import org.example.autoplay.ui.Tone
import org.example.autoplay.ui.VolumeSlider

@Composable
fun CarScreen(onBack: () -> Unit, onUseAsPhone: () -> Unit, viewModel: CarViewModel = hiltViewModel()) {
    val liveState by viewModel.state.collectAsStateWithLifecycle()
    // Demo: the dashboard with made-up phones and a song, to see it without connecting anything
    var demo by rememberSaveable { mutableStateOf(false) }
    val state = if (demo) remember(liveState) { demoState(liveState) } else liveState
    val floating by NavigationMode.floating.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    // The play/pause button of the floating window follows the song
    val songPlaying = state.track?.playing != false
    LaunchedEffect(floating, songPlaying) {
        if (floating) activity?.setPictureInPictureParams(NavigationMode.params(activity, songPlaying))
    }
    if (floating) {
        MiniPlayer(state)
        return
    }

    // Only "Apagar" turns the car side off; Back just hides it so music and phones keep going
    val context = LocalContext.current
    val stopAndLeave = {
        viewModel.stop()
        onBack()
    }
    val minimize: () -> Unit = { (context as? Activity)?.moveTaskToBack(true) }
    BackHandler(onBack = minimize)
    var renaming by remember { mutableStateOf(false) }
    if (renaming) RenameDialog(state.carName, onDismiss = { renaming = false }, onSave = {
        viewModel.rename(it)
        renaming = false
    })

    // Turned off from the notification while this screen is open
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(state.running) {
        if (state.running) wasRunning = true else if (wasRunning) onBack()
    }

    state.request?.let { request ->
        AlertDialog(
            onDismissRequest = {},
            icon = { IconBadge(AppIcons.Smartphone, MaterialTheme.colorScheme.primary) },
            title = { Text(request.deviceName, textAlign = TextAlign.Center) },
            text = { Text("Quiere conectarse a este auto", textAlign = TextAlign.Center) },
            confirmButton = { Button(onClick = viewModel::allow) { Text("Permitir") } },
            dismissButton = { TextButton(onClick = viewModel::deny) { Text("Rechazar") } },
        )
    }

    val online = state.devices.count { it.online }
    val reconnecting = state.devices.size - online
    val (label, tone) = when {
        demo -> "Demo" to Tone.Warning
        !state.running -> "Iniciando" to Tone.Neutral
        state.devices.isEmpty() -> "Esperando" to Tone.Progress
        online == 0 -> "Reconectando" to Tone.Warning
        else -> "$online conectado${if (online == 1) "" else "s"}" to Tone.Success
    }
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.startBluetooth()
    }
    val connectionActions = ConnectionActions(
        onNewCode = viewModel::newCode,
        onBluetoothPermission = {
            if (Build.VERSION.SDK_INT >= 31) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        },
    )

    // Read at arm's length on a head unit, everything is a size larger; a phone opening
    // the car side keeps its normal size and moves the status under the name
    val compact = LocalConfiguration.current.screenWidthDp < COMPACT_WIDTH_DP
    val density = LocalDensity.current
    val fontScale = if (compact) density.fontScale else density.fontScale * CAR_FONT_SCALE
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = state.carName.ifEmpty { "Mi auto" }, subtitle = label.takeIf { compact }) {
            IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = "Cambiar nombre") }
            if (!compact) StatusChip(label, tone, Modifier.padding(horizontal = 8.dp))
            if (demo) TextButton(onClick = { demo = false }) { Text("Salir de la demo") }
            TextButton(onClick = stopAndLeave) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            val wide = maxWidth > maxHeight && maxWidth >= 600.dp
            // Car screens are usually landscape and short: QR and code side by side so both are always visible
            val wideQrSize = (maxHeight - 150.dp).coerceIn(150.dp, 260.dp)
            val actions = DeviceActions(
                onMove = viewModel::move,
                onRemove = viewModel::remove,
                onMute = viewModel::setMuted,
                onLockVolume = viewModel::setVolumeLocked,
            )
            val player = PlayerActions(viewModel::setVolume, viewModel::mediaCommand, viewModel::answerVolumeRequest)

            var addingPhone by remember { mutableStateOf(false) }
            if (addingPhone) AddPhoneDialog(state, viewModel::newCode, onDismiss = { addingPhone = false })

            if (state.devices.isEmpty()) {
                // Waiting for the first phone: only what it takes to connect one
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (wide) {
                        JoinCard(state, wideQrSize, Modifier.fillMaxWidth(), horizontal = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StepsCard(Modifier.weight(1f))
                            ConnectionMethods(state, connectionActions, Modifier.weight(1f))
                        }
                    } else {
                        JoinCard(state, 220.dp, Modifier.fillMaxWidth(), horizontal = false)
                        StepsCard(Modifier.fillMaxWidth())
                        ConnectionMethods(state, connectionActions, Modifier.fillMaxWidth())
                    }
                    // The device was taken for the car; a tablet used as a phone can switch here
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { demo = true }) {
                            Text("Ver demo del panel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onUseAsPhone) {
                            Text("Usar como celular", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (wide) {
                // Dashboard like CarPlay: apps in a slim bar, the music big, the phones to the side
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CarAppDock(playing = state.track?.playing != false, Modifier.fillMaxHeight())
                    NowPlayingCard(
                        state,
                        player,
                        large = true,
                        modifier = Modifier.weight(1.7f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    )
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeviceList(state.devices, state.playingId, actions = actions)
                        OutlinedButton(onClick = { addingPhone = true }, modifier = Modifier.fillMaxWidth()) { Text("Agregar celular") }
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NowPlayingCard(state, player, large = false, modifier = Modifier.fillMaxWidth())
                    CarAppShortcuts(playing = state.track?.playing != false, Modifier.fillMaxWidth())
                    DeviceList(state.devices, state.playingId, actions = actions)
                    OutlinedButton(onClick = { addingPhone = true }, modifier = Modifier.fillMaxWidth()) { Text("Agregar celular") }
                }
            }
        }
    }
    }
}

private const val CAR_FONT_SCALE = 1.2f
private const val COMPACT_WIDTH_DP = 600

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre del auto") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                singleLine = true,
                placeholder = { Text("Mi auto") },
            )
        },
        confirmButton = { Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private class ConnectionActions(val onNewCode: () -> Unit, val onBluetoothPermission: () -> Unit)

/** Callbacks of the player card. */
private class PlayerActions(
    val onVolume: (Int) -> Unit,
    val onMedia: (MediaAction) -> Unit,
    val onVolumeRequest: (accept: Boolean) -> Unit,
)

/** Song on the left, big cover on the right, controls below; the song shows once its phone shares it. */
@Composable
private fun NowPlayingCard(state: CarSessionState, actions: PlayerActions, large: Boolean, modifier: Modifier) {
    val playing = state.playing
    val track = state.track
    SectionCard(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (playing != null) "Sonando · ${playing.name}" else "Sin música",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (playing != null) AppColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track?.title ?: if (playing != null) "Música del celular" else "Activa el audio en un celular",
                    style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                track?.artist?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Apps like Snapchat or TikTok don't say what plays; nor does a phone without notification access
                if (playing != null && track == null) {
                    Text(
                        "Sin nombre de canción · en el celular toca «Mostrar la canción en el auto»",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Cover(track?.art, active = playing != null, size = if (large) 168.dp else 88.dp)
        }
        if (playing != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { actions.onMedia(MediaAction.Previous) }, modifier = Modifier.size(56.dp)) {
                    Icon(AppIcons.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { actions.onMedia(MediaAction.PlayPause) }, modifier = Modifier.size(72.dp)) {
                    // Without the song info the state is unknown; pause is the likelier wish
                    Icon(
                        if (track?.playing == false) AppIcons.Play else AppIcons.Pause,
                        contentDescription = "Reproducir o pausar",
                        modifier = Modifier.size(48.dp),
                    )
                }
                IconButton(onClick = { actions.onMedia(MediaAction.Next) }, modifier = Modifier.size(56.dp)) {
                    Icon(AppIcons.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(36.dp))
                }
            }
            VolumeSlider(state.volume, actions.onVolume)
        }
        state.volumeRequest?.let { request ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🙏 ${request.deviceName} pide más volumen",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { actions.onVolumeRequest(false) }) { Text("Ni lo sueñes") }
                Button(onClick = { actions.onVolumeRequest(true) }) { Text("Subir") }
            }
        }
    }
    }
}

@Composable
private fun Cover(art: Bitmap?, active: Boolean, size: Dp) {
    if (art != null) {
        val image = remember(art) { art.asImageBitmap() }
        Image(image, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(16.dp)))
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (active) AppIcons.MusicNote else AppIcons.MusicOff,
                contentDescription = null,
                tint = if (active) AppColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2.5f),
            )
        }
    }
}

/** QR and code for one more phone, only when the driver asks for it. */
@Composable
private fun AddPhoneDialog(state: CarSessionState, onNewCode: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { AddPhoneCard(state, 200.dp, onNewCode, Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

/** Compact join card shown next to the device list, so more phones can join at any time. */
@Composable
private fun AddPhoneCard(state: CarSessionState, qrSize: Dp, onNewCode: () -> Unit, modifier: Modifier) {
    SectionCard(modifier) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Agregar otro celular", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            state.joinLink?.let { link ->
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                    QrCode(link.toString(), size = qrSize)
                }
            }
            if (state.code.isNotEmpty()) CodeDigits(state.code, digitHeight = 40.dp)
            Countdown(state.codeExpiresAt)
            TextButton(onClick = onNewCode) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Nuevo código")
            }
        }
    }
}

@Composable
private fun JoinCard(state: CarSessionState, qrSize: Dp, modifier: Modifier, horizontal: Boolean) {
    val qr = @Composable {
        state.joinLink?.let { link ->
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp)) {
                QrCode(link.toString(), size = qrSize)
            }
        }
    }
    val code = @Composable {
        Text("o ingresa el código", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.code.isNotEmpty()) CodeDigits(state.code, digitHeight = 56.dp)
        Countdown(state.codeExpiresAt)
    }
    val title = @Composable {
        Text("Escanea con AutoPlay en tu celular", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }

    SectionCard(modifier) {
        if (horizontal) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                qr()
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    title()
                    code()
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                title()
                qr()
                code()
            }
        }
    }
}

@Composable
private fun Countdown(expiresAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remaining = (expiresAt - now).coerceAtLeast(0)
    val seconds = remaining / 1000
    Column(Modifier.widthIn(max = 320.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            progress = { remaining.toFloat() / SessionCode.TTL_MILLIS },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Vence en %d:%02d · un solo uso".format(seconds / 60, seconds % 60),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepsCard(modifier: Modifier) {
    SectionCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Cómo conectar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Step(1, "En el celular: AutoPlay → Soy el Celular")
            Step(2, "Escanea el QR o escribe el código")
            Step(3, "Pulsa Permitir aquí")
        }
    }
}

/** How phones can reach this car, with a short explanation of each option. */
@Composable
private fun ConnectionMethods(state: CarSessionState, actions: ConnectionActions, modifier: Modifier) {
    val context = LocalContext.current
    SectionCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Conexión", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            val wifiReady = state.host != null
            MethodRow(
                icon = AppIcons.Wifi,
                title = "Wi-Fi",
                ready = wifiReady,
                status = if (wifiReady) "Listo · ${state.host}" else "Sin red",
                description = "Misma red o hotspot del celular",
            )

            MethodRow(
                icon = AppIcons.Bluetooth,
                title = "Bluetooth",
                ready = state.bluetooth == CarBluetooth.Ready,
                status = when (state.bluetooth) {
                    CarBluetooth.Ready -> "Listo · ${state.bluetoothName ?: "visible"}"
                    CarBluetooth.Off -> "Apagado"
                    CarBluetooth.NeedsPermission -> "Falta permiso"
                    CarBluetooth.Unavailable -> "No disponible en esta radio"
                },
                description = "Sin Wi-Fi · celular emparejado",
            )
            when (state.bluetooth) {
                CarBluetooth.NeedsPermission -> OutlinedButton(onClick = actions.onBluetoothPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("Permitir Bluetooth")
                }
                CarBluetooth.Off, CarBluetooth.Ready -> OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.bluetooth == CarBluetooth.Off) "Activar Bluetooth" else "Emparejar un celular") }
                CarBluetooth.Unavailable -> Unit
            }
            OutlinedButton(onClick = actions.onNewCode, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Generar nuevo código")
            }
        }
    }
}

@Composable
private fun MethodRow(icon: ImageVector, title: String, ready: Boolean, status: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBadge(icon, if (ready) AppColors.Success else MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.bodySmall, color = if (ready) AppColors.Success else AppColors.Warning)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
