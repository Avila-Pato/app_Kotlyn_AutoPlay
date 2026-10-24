package org.example.autoplay.car

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
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
fun CarScreen(onBack: () -> Unit, viewModel: CarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Back turns the car side off; Home leaves it running in the background
    val stopAndLeave = {
        viewModel.stop()
        onBack()
    }
    BackHandler(onBack = stopAndLeave)

    // Turned off from the notification while this screen is open
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(state.running) {
        if (state.running) wasRunning = true else if (wasRunning) onBack()
    }

    state.request?.let { request ->
        AlertDialog(
            onDismissRequest = {},
            icon = { IconBadge(AppIcons.Smartphone, MaterialTheme.colorScheme.primary) },
            title = { Text("¿Conectar este celular?", textAlign = TextAlign.Center) },
            text = {
                Text(
                    "${request.deviceName} quiere conectarse para poner música.\n${request.address ?: ""}",
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = { Button(onClick = viewModel::allow) { Text("Permitir") } },
            dismissButton = { TextButton(onClick = viewModel::deny) { Text("Rechazar") } },
        )
    }

    val online = state.devices.count { it.online }
    val reconnecting = state.devices.size - online
    val (label, tone) = when {
        !state.running -> "Iniciando" to Tone.Neutral
        state.devices.isEmpty() -> "Esperando celulares" to Tone.Progress
        online == 0 -> "Reconectando $reconnecting" to Tone.Warning
        reconnecting > 0 -> "$online conectado${if (online == 1) "" else "s"} · $reconnecting reconectando" to Tone.Success
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

    // Read at arm's length while driving: everything on the car side is a size larger
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * CAR_FONT_SCALE)) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Modo Auto",
            subtitle = "Sigue activo aunque salgas de la app",
            onBack = stopAndLeave,
        ) {
            StatusChip(label, tone, Modifier.padding(end = 8.dp))
            TextButton(onClick = stopAndLeave) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            val wide = maxWidth > maxHeight && maxWidth >= 600.dp
            // Car screens are usually landscape and short: QR and code side by side so both are always visible
            val wideQrSize = (maxHeight - 150.dp).coerceIn(150.dp, 260.dp)
            val sideQrSize = (maxHeight - 230.dp).coerceIn(120.dp, 200.dp)
            val actions = DeviceActions(onMove = viewModel::move, onRemove = viewModel::remove)

            if (state.devices.isEmpty()) {
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
                }
            } else if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.weight(1.5f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        NowPlayingCard(state, viewModel::setVolume, Modifier.fillMaxWidth())
                        SectionCard(Modifier.fillMaxWidth()) {
                            DeviceList(state.devices, state.playingId, actions = actions)
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AddPhoneCard(state, sideQrSize, viewModel::newCode, Modifier.fillMaxWidth())
                        ConnectionMethods(state, connectionActions, Modifier.fillMaxWidth())
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NowPlayingCard(state, viewModel::setVolume, Modifier.fillMaxWidth())
                    SectionCard(Modifier.fillMaxWidth()) {
                        DeviceList(state.devices, state.playingId, actions = actions)
                    }
                    AddPhoneCard(state, 180.dp, viewModel::newCode, Modifier.fillMaxWidth())
                    ConnectionMethods(state, connectionActions, Modifier.fillMaxWidth())
                }
            }
        }
    }
    }
}

private const val CAR_FONT_SCALE = 1.2f

private class ConnectionActions(val onNewCode: () -> Unit, val onBluetoothPermission: () -> Unit)

@Composable
private fun NowPlayingCard(state: CarSessionState, onVolume: (Int) -> Unit, modifier: Modifier) {
    val playing = state.playing
    SectionCard(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                if (playing != null) AppIcons.MusicNote else AppIcons.MusicOff,
                if (playing != null) AppColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 56.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (playing != null) "Suena la música de ${playing.name}" else "Nadie está poniendo música",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (playing != null) {
                        "Tú decides quién pone la música: usa las flechas o «Poner su música»."
                    } else {
                        "Los celulares deben tocar «Activar audio» en AutoPlay para poder sonar aquí."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (playing != null) VolumeSlider(state.volume, onVolume)
    }
    }
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
            Step(1, "Abre AutoPlay en el celular y elige «Soy el Celular»")
            Step(2, "Elige Wi-Fi o Bluetooth y escanea el QR o escribe el código")
            Step(3, "Pulsa «Permitir» cuando aparezca la solicitud aquí")
            Step(4, "Pueden conectarse varios celulares; tú eliges quién pone la música")
        }
    }
}

/** How phones can reach this car, with a short explanation of each option. */
@Composable
private fun ConnectionMethods(state: CarSessionState, actions: ConnectionActions, modifier: Modifier) {
    val context = LocalContext.current
    SectionCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Formas de conectarse", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            val wifiReady = state.host != null
            MethodRow(
                icon = AppIcons.Wifi,
                title = "Wi-Fi",
                ready = wifiReady,
                status = if (wifiReady) "Listo · IP ${state.host}:${state.port}" else "Sin red Wi-Fi",
                description = "Mejor sonido. El celular y el auto en la misma red, o el auto conectado al hotspot del celular.",
            )

            MethodRow(
                icon = AppIcons.Bluetooth,
                title = "Bluetooth",
                ready = state.bluetooth == CarBluetooth.Ready,
                status = when (state.bluetooth) {
                    CarBluetooth.Ready -> "Listo · aparece como «${state.bluetoothName ?: "este auto"}»"
                    CarBluetooth.Off -> "Bluetooth apagado"
                    CarBluetooth.NeedsPermission -> "Falta el permiso de Bluetooth"
                    CarBluetooth.Unavailable -> "Esta radio no deja usar su Bluetooth a otras apps"
                },
                description = "Para cuando no hay Wi-Fi. Empareja el celular con esta radio una vez; el sonido viaja comprimido.",
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
