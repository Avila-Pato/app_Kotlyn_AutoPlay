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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
fun PhoneScreen(onBack: () -> Unit, viewModel: PhoneViewModel = hiltViewModel()) {
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
            SessionView(s.carName, reconnectAttempt = null, room, sharing, requestAudio, viewModel::setCarVolume, viewModel::disconnect)
        is PhoneStatus.Reconnecting ->
            SessionView(s.carName, reconnectAttempt = s.attempt, room, sharing, requestAudio, viewModel::setCarVolume, viewModel::disconnect)
        else -> JoinView(status, viewModel, onBack, onScan = { scanning = true })
    }
}

@Composable
private fun JoinView(status: PhoneStatus, viewModel: PhoneViewModel, onBack: () -> Unit, onScan: () -> Unit) {
    val code by viewModel.code.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val busy = status == PhoneStatus.Connecting || status == PhoneStatus.WaitingApproval
    val canJoin = code.length == 6 && !busy
    var method by rememberSaveable { mutableStateOf(JoinMethod.Wifi) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Unirse al auto", subtitle = "Conecta este celular para poner tu música", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StatusFor(status)
            MethodSelector(method, enabled = !busy, onSelect = { method = it })
            when (method) {
                JoinMethod.Wifi -> {
                    ScanCard(enabled = !busy, onClick = onScan)
                    DividerWithText("o ingresa el código")
                    CodeInput(code, viewModel::onCodeChange, enabled = !busy)
                    NearbyCars(cars, codeComplete = code.length == 6, enabled = canJoin, onSelect = viewModel::connectTo)
                    ManualConnection(viewModel, enabled = canJoin)
                }
                JoinMethod.Bluetooth -> {
                    Text(
                        "Código que aparece en la pantalla del auto",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CodeInput(code, viewModel::onCodeChange, enabled = !busy)
                    BluetoothDevices(viewModel, codeComplete = code.length == 6, enabled = canJoin)
                }
            }
        }
    }
}

private enum class JoinMethod(val title: String, val description: String) {
    Wifi(
        "Wi-Fi",
        "Mejor sonido. El celular y el auto en la misma red Wi-Fi, o el auto conectado al hotspot del celular (no gasta datos).",
    ),
    Bluetooth(
        "Bluetooth",
        "Para cuando no hay Wi-Fi. La radio del auto debe estar emparejada con este celular. El sonido viaja comprimido.",
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
private fun BluetoothDevices(viewModel: PhoneViewModel, codeComplete: Boolean, enabled: Boolean) {
    val context = LocalContext.current
    val devices by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.refreshPairedDevices()
    }
    LaunchedEffect(Unit) {
        if (viewModel.hasBluetoothPermission()) {
            viewModel.refreshPairedDevices()
        } else if (Build.VERSION.SDK_INT >= 31) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Equipos emparejados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (codeComplete) "Toca la radio del auto en la lista de equipos emparejados."
            else "Escribe el código de 6 dígitos y luego toca la radio del auto.",
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
        devices.orEmpty().forEach { device ->
            Card(
                onClick = { viewModel.connectBluetooth(device) },
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
                        Text(device.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (device.looksLikeCar) "Radio de auto · Bluetooth" else "Bluetooth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Conectar")
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
private fun ManualConnection(viewModel: PhoneViewModel, enabled: Boolean) {
    val address by viewModel.manualAddress.collectAsStateWithLifecycle()
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
    onCarVolume: (Int) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(carName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (reconnectAttempt == null) {
                    StatusChip("Conectado", Tone.Success)
                } else {
                    StatusChip("Reconectando · intento $reconnectAttempt", Tone.Warning)
                }
            }
            OutlinedButton(onClick = onDisconnect) {
                Text("Desconectar", color = MaterialTheme.colorScheme.error)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectedCard(reconnectAttempt != null, room, sharing, onCarVolume)
            if (!sharing) AudioCard(onShareAudio)
            BackgroundHint()
            SectionCard(Modifier.fillMaxWidth()) {
                DeviceList(room?.devices.orEmpty(), room?.playingId, selfId = room?.selfId)
            }
        }
    }
}

@Composable
private fun ConnectedCard(reconnecting: Boolean, room: CarRoom?, sharing: Boolean, onCarVolume: (Int) -> Unit) {
    val playingOther = room?.playing?.takeIf { !room.isPlaying }
    val (title, text) = when {
        reconnecting -> "Se cortó la señal" to "Reconectando con el auto…"
        room?.isPlaying == true -> "Tu música suena en el auto" to
            "Abre YouTube, Spotify o cualquier app y dale play. El celular queda en silencio y sus teclas de volumen controlan el auto."
        !sharing -> "Estás conectado" to "Activa el audio para que tu música pueda sonar en el auto."
        playingOther != null -> "Estás conectado" to
            "Ahora suena la música de ${playingOther.name}. El auto decide quién pone la música; cuando sea tu turno sonará la tuya."
        else -> "Estás conectado" to "Esperando a que el auto te dé el turno para poner música."
    }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (reconnecting) {
                CircularProgressIndicator(Modifier.size(56.dp), color = AppColors.Warning)
            } else {
                IconBadge(if (room?.isPlaying == true) AppIcons.MusicNote else Icons.Filled.Check, AppColors.Success, size = 72.dp)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (room?.isPlaying == true && !reconnecting) {
                Text("Volumen en el auto", style = MaterialTheme.typography.labelLarge)
                VolumeSlider(room.volume, onCarVolume)
            }
        }
    }
}

@Composable
private fun AudioCard(onShareAudio: () -> Unit) {
    val supported = Build.VERSION.SDK_INT >= 29
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(AppIcons.MusicOff, MaterialTheme.colorScheme.primary, size = 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Audio desactivado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (supported) {
                            "Android te pedirá permiso para «compartir la pantalla»: AutoPlay solo usa el sonido, nunca la imagen."
                        } else {
                            "Tu versión de Android no permite enviar el audio de otras apps (necesita Android 10 o más reciente)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (supported) {
                Button(onClick = onShareAudio, modifier = Modifier.fillMaxWidth()) { Text("Activar audio") }
            }
        }
    }
}

@Composable
private fun BackgroundHint() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            "Ya puedes salir de la app: la conexión sigue activa y la verás en la barra de notificaciones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
