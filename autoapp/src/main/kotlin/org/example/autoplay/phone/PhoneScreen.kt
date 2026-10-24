package org.example.autoplay.phone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.example.autoplay.net.DiscoveredCar
import org.example.autoplay.ui.AppColors
import org.example.autoplay.ui.AppIcons
import org.example.autoplay.ui.CodeInput
import org.example.autoplay.ui.DeviceList
import org.example.autoplay.ui.DividerWithText
import org.example.autoplay.ui.IconBadge
import org.example.autoplay.ui.ScreenHeader
import org.example.autoplay.ui.SectionCard
import org.example.autoplay.ui.StatusBanner
import org.example.autoplay.ui.StatusChip
import org.example.autoplay.ui.Tone
import org.example.autoplay.ui.VolumeSlider

@Composable
fun PhoneScreen(onBack: () -> Unit, onUseAsCar: () -> Unit, viewModel: PhoneViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var scanning by rememberSaveable { mutableStateOf(false) }

    if (scanning) {
        QrScannerScreen(
            onClose = { scanning = false },
            onLink = { link ->
                scanning = false
                viewModel.connectWithLink(link)
            },
        )
        return
    }

    val room by viewModel.room.collectAsStateWithLifecycle()
    val sharing by viewModel.sharingAudio.collectAsStateWithLifecycle()
    val requestAudio = rememberAudioRequest(viewModel::shareAudio)
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MILLIS)
            viewModel.clearNotice()
        }
    }
    val volume = CarVolume(viewModel::setCarVolume, viewModel::askForMoreVolume, notice)

    // Asked for only once the user is in the car; null once granted
    val context = LocalContext.current
    var mediaAccess by remember { mutableStateOf(viewModel.hasMediaAccess()) }
    LifecycleResumeEffect(Unit) {
        mediaAccess = viewModel.hasMediaAccess()
        if (mediaAccess) viewModel.onMediaAccessChanged()
        onPauseOrDispose {}
    }
    val enableSong = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.takeIf { !mediaAccess }

    // Right after joining, ask once for audio so the user does not have to find the button
    val connected = status is PhoneStatus.Connected
    var askedForAudio by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(connected) {
        if (connected && !sharing && !askedForAudio && Build.VERSION.SDK_INT >= 29) {
            askedForAudio = true
            requestAudio()
        }
    }

    when (val s = status) {
        is PhoneStatus.Connected ->
            SessionView(s.carName, reconnectAttempt = null, room, sharing, requestAudio, volume, enableSong, viewModel::disconnect)
        is PhoneStatus.Reconnecting ->
            SessionView(s.carName, reconnectAttempt = s.attempt, room, sharing, requestAudio, volume, enableSong, viewModel::disconnect)
        else -> JoinView(status, viewModel, onBack, onUseAsCar, onScan = { scanning = true })
    }
}

@Composable
private fun JoinView(status: PhoneStatus, viewModel: PhoneViewModel, onBack: () -> Unit, onUseAsCar: () -> Unit, onScan: () -> Unit) {
    val code = viewModel.code
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val busy = status == PhoneStatus.Connecting || status == PhoneStatus.WaitingApproval
    val canJoin = code.length == 6 && !busy
    var method by rememberSaveable { mutableStateOf(JoinMethod.Wifi) }
    val myCars by viewModel.myCars.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Unirse al auto", subtitle = "Conecta este celular para poner tu música")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StatusFor(status)
            if (myCars.isNotEmpty()) MyCars(myCars, enabled = !busy, onConnect = viewModel::connectKnown, onForget = viewModel::forgetCar)
            MethodSelector(method, enabled = !busy, onSelect = { method = it })
            when (method) {
                JoinMethod.Wifi -> {
                    ScanCard(enabled = !busy, onClick = onScan)
                    DividerWithText("o ingresa el código")
                    CodeInput(code, viewModel::onCodeChange, enabled = !busy)
                    NearbyCars(cars, codeComplete = code.length == 6, enabled = canJoin, onSelect = viewModel::connectTo)
                    ManualConnection(viewModel, enabled = canJoin)
                }
                JoinMethod.Bluetooth -> BluetoothDevices(viewModel, enabled = !busy)
            }
            // The device was taken for a phone; a tablet in the car can switch here
            TextButton(onClick = onUseAsCar, modifier = Modifier.fillMaxWidth()) {
                Text("Usar este equipo como auto", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Cars that approved this phone before: one tap, no code. */
@Composable
private fun MyCars(cars: List<KnownCar>, enabled: Boolean, onConnect: (KnownCar) -> Unit, onForget: (KnownCar) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mis autos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        cars.forEach { car ->
            Card(
                onClick = { onConnect(car) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(AppIcons.Car, MaterialTheme.colorScheme.primary, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(car.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (car.target is CarTarget.Bluetooth) "Bluetooth · sin código" else "Wi-Fi · sin código",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onForget(car) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Olvidar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private enum class JoinMethod(val title: String, val description: String) {
    Wifi(
        "Wi-Fi",
        "Mejor sonido. Misma red o hotspot del celular.",
    ),
    Bluetooth(
        "Bluetooth",
        "Sin Wi-Fi ni código. La radio debe estar emparejada.",
    ),
}

@Composable
private fun MethodSelector(selected: JoinMethod, enabled: Boolean, onSelect: (JoinMethod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("¿Cómo quieres conectarte?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
            JoinMethod.entries.forEach { method ->
                val active = method == selected
                Card(
                    onClick = { onSelect(method) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(
                        if (active) 2.dp else 1.dp,
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconBadge(
                            if (method == JoinMethod.Wifi) AppIcons.Wifi else AppIcons.Bluetooth,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 40.dp,
                        )
                        Text(method.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            method.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFor(status: PhoneStatus) {
    when (status) {
        PhoneStatus.Connecting -> StatusBanner("Conectando con el auto…", Tone.Progress, showProgress = true)
        PhoneStatus.WaitingApproval -> StatusBanner("Esperando que el auto pulse «Permitir»…", Tone.Warning, showProgress = true)
        is PhoneStatus.Failed -> StatusBanner(status.reason, Tone.Error)
        else -> Unit
    }
}

@Composable
private fun ScanCard(enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(AppIcons.QrScanner, MaterialTheme.colorScheme.primary, size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Escanear código QR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "La forma más rápida: apunta la cámara al QR de la pantalla del auto",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun NearbyCars(cars: List<DiscoveredCar>, codeComplete: Boolean, enabled: Boolean, onSelect: (DiscoveredCar) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Autos cercanos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (cars.isEmpty()) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        if (cars.isEmpty()) {
            Text(
                "Buscando en la red… El auto y el celular deben estar en la misma red Wi-Fi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (!codeComplete) {
            Text(
                "Escribe el código de 6 dígitos y luego toca tu auto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        cars.forEach { car ->
            Card(
                onClick = { onSelect(car) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(AppIcons.Car, MaterialTheme.colorScheme.primary, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(car.name, style = MaterialTheme.typography.titleSmall)
                        Text(car.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Conectar")
                }
            }
        }
    }
}

@Composable
private fun BluetoothDevices(viewModel: PhoneViewModel, enabled: Boolean) {
    val context = LocalContext.current
    val devices by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.refreshPairedDevices()
    }
    LaunchedEffect(Unit) {
        if (!viewModel.hasBluetoothPermission() && Build.VERSION.SDK_INT >= 31) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    // Coming back from the Bluetooth settings after pairing shows the new device right away
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPairedDevices()
        onPauseOrDispose {}
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Equipos emparejados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Toca la radio del auto. Sin código: el auto solo te pedirá Permitir la primera vez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            devices == null -> Text(
                "AutoPlay necesita el permiso de «Dispositivos cercanos» para ver tus equipos Bluetooth.",
                style = MaterialTheme.typography.bodyMedium,
            )
            devices.orEmpty().isEmpty() -> Text(
                "No hay equipos emparejados. Empareja la radio del auto desde los ajustes de Bluetooth.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Only what is likely the car stays visible; the rest is one tap away
        val (main, others) = devices.orEmpty().partition { it.relevant }
        var showOthers by rememberSaveable { mutableStateOf(false) }
        main.forEach { PairedDeviceCard(it, enabled) { viewModel.connectBluetooth(it) } }
        if (others.isNotEmpty()) {
            if (main.isEmpty() || showOthers) {
                others.forEach { PairedDeviceCard(it, enabled) { viewModel.connectBluetooth(it) } }
            }
            if (main.isNotEmpty()) {
                TextButton(onClick = { showOthers = !showOthers }) {
                    Text(if (showOthers) "Ocultar otros equipos" else "Otros equipos (${others.size})")
                }
            }
        }
        OutlinedButton(
            onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Emparejar un equipo nuevo") }
    }
}

@Composable
private fun PairedDeviceCard(device: PairedDevice, enabled: Boolean, onClick: () -> Unit) {
    val (detail, accent) = when {
        device.connected -> "Conectado ahora" to AppColors.Success
        device.lastUsed > 0 -> "Usado recientemente" to MaterialTheme.colorScheme.primary
        device.looksLikeCar -> "Radio de auto" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "Bluetooth" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                if (device.looksLikeCar) AppIcons.Car else AppIcons.Bluetooth,
                MaterialTheme.colorScheme.primary,
                size = 40.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = accent)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Conectar")
        }
    }
}

@Composable
private fun ManualConnection(viewModel: PhoneViewModel, enabled: Boolean) {
    val address = viewModel.manualAddress
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Ocultar conexión por IP" else "¿No aparece tu auto? Conectar por IP")
        }
        AnimatedVisibility(expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = address,
                    onValueChange = viewModel::onManualAddressChange,
                    label = { Text("IP del auto") },
                    placeholder = { Text("192.168.43.1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = viewModel::connectManually, enabled = enabled && address.isNotEmpty()) { Text("Conectar") }
            }
        }
    }
}

@Composable
private fun SessionView(
    carName: String,
    reconnectAttempt: Int?,
    room: CarRoom?,
    sharing: Boolean,
    onShareAudio: () -> Unit,
    volume: CarVolume,
    onEnableSong: (() -> Unit)?,
    onDisconnect: () -> Unit,
) {
    val reconnecting = reconnectAttempt != null
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (reconnecting) "Reconectando…" else "Conectado a",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (reconnecting) AppColors.Warning else AppColors.Success,
                )
                Text(
                    carName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onDisconnect) { Text("Salir", color = MaterialTheme.colorScheme.error) }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            NowPlaying(reconnecting, room, sharing, onShareAudio, volume)
            onEnableSong?.let {
                TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Mostrar la canción en el auto") }
            }
            DeviceList(room?.devices.orEmpty(), room?.playingId, selfId = room?.selfId)
            Text(
                "Puedes salir de la app: la conexión sigue activa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** What a passenger can do with the car's volume; [notice] is the driver's latest reply. */
private class CarVolume(val onChange: (Int) -> Unit, val onAskForMore: () -> Unit, val notice: String?)

/** The one thing that matters right now: is my music playing, and what can I do about it. */
@Composable
private fun NowPlaying(
    reconnecting: Boolean,
    room: CarRoom?,
    sharing: Boolean,
    onShareAudio: () -> Unit,
    volume: CarVolume,
) {
    val playingHere = room?.isPlaying == true
    val muted = room?.self?.muted == true
    val locked = room?.self?.volumeLocked == true
    val other = room?.playing?.takeIf { !playingHere }
    val supported = Build.VERSION.SDK_INT >= 29
    val (title, subtitle) = when {
        reconnecting -> "Se cortó la señal" to "Volviendo a conectar…"
        !sharing -> "Audio desactivado" to if (supported) "Actívalo para sonar en el auto" else "Requiere Android 10 o más reciente"
        muted -> "Te pusieron en mute 🤐" to "El conductor eligió otra música"
        playingHere -> "Sonando en el auto" to "Dale play en cualquier app"
        other != null -> "En espera" to "Ahora suena ${other.name}"
        else -> "En espera" to "El auto elige quién pone la música"
    }
    val accent = when {
        reconnecting -> AppColors.Warning
        playingHere -> AppColors.Success
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    SectionCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (reconnecting) {
                CircularProgressIndicator(Modifier.size(56.dp), color = accent)
            } else {
                IconBadge(if (sharing) AppIcons.MusicNote else AppIcons.MusicOff, accent, size = 64.dp)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                reconnecting -> Unit
                !sharing && supported -> Button(onClick = onShareAudio, modifier = Modifier.fillMaxWidth()) { Text("Activar audio") }
                playingHere && locked -> AskForVolume(volume.onAskForMore)
                playingHere -> VolumeSlider(room.volume, volume.onChange, Modifier.padding(top = 6.dp))
            }
            volume.notice?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The driver holds the volume; the passenger can ask, but not too often. */
@Composable
private fun AskForVolume(onAsk: () -> Unit) {
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(waiting) {
        if (waiting) {
            delay(ASK_COOLDOWN_MILLIS)
            waiting = false
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("🔒 El volumen lo maneja el conductor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = {
                onAsk()
                waiting = true
            },
            enabled = !waiting,
        ) { Text(if (waiting) "Pedido enviado…" else "Pedir más volumen 🙏") }
    }
}

private const val NOTICE_MILLIS = 6_000L
private const val ASK_COOLDOWN_MILLIS = 30_000L

/** Asks for the microphone permission (Android requires it for audio capture) and then for capture consent. */
@Composable
private fun rememberAudioRequest(onGranted: (resultCode: Int, data: Intent) -> Unit): () -> Unit {
    val context = LocalContext.current
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) onGranted(result.resultCode, data)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            captureLauncher.launch(
                if (Build.VERSION.SDK_INT >= 34) {
                    // Whole screen: sharing a single app would capture only that app's sound
                    manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                } else {
                    manager.createScreenCaptureIntent()
                },
            )
        }
    }
    return {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
