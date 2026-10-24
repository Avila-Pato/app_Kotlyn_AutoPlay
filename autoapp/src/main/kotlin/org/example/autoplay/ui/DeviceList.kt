package org.example.autoplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.autoplay.net.DeviceInfo
import org.example.autoplay.net.MAX_DEVICE_NAME

/** Actions only the car screen has: the driver decides the priority. */
class DeviceActions(
    val onMove: (deviceId: String, position: Int) -> Unit,
    val onRemove: (deviceId: String) -> Unit,
    val onMute: (deviceId: String, muted: Boolean) -> Unit,
    val onLockVolume: (deviceId: String, locked: Boolean) -> Unit,
    val onRename: (deviceId: String, name: String) -> Unit,
)

/**
 * Connected phones in priority order. The one playing music is highlighted; [selfId] marks
 * "this phone" on the phone side, and [actions] adds the driver's controls on the car side.
 * Phones that dropped out wait folded at the bottom so they don't crowd the list.
 */
@Composable
fun DeviceList(
    devices: List<DeviceInfo>,
    playingId: String?,
    modifier: Modifier = Modifier,
    selfId: String? = null,
    actions: DeviceActions? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Conectados · ${devices.count { it.online }}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (devices.isEmpty()) {
            Text(
                "Ningún celular por ahora",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val (online, offline) = devices.partition { it.online }
        online.forEachIndexed { index, device ->
            // Moves are positions in the full list, which still holds the phones that dropped out
            DeviceRow(
                device = device,
                label = "${index + 1}",
                playing = device.id == playingId,
                isSelf = device.id == selfId,
                actions = actions,
                moveUp = online.getOrNull(index - 1)?.let { devices.indexOf(it) },
                moveDown = online.getOrNull(index + 1)?.let { devices.indexOf(it) },
            )
        }
        if (offline.isNotEmpty()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Ocultar reconectando" else "Reconectando (${offline.size})")
            }
            if (expanded) {
                offline.forEach { device ->
                    DeviceRow(device, label = "·", playing = false, isSelf = device.id == selfId, actions = actions, moveUp = null, moveDown = null)
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: DeviceInfo,
    label: String,
    playing: Boolean,
    isSelf: Boolean,
    actions: DeviceActions?,
    moveUp: Int?,
    moveDown: Int?,
) {
    val (status, accent) = when {
        playing -> "Sonando" to AppColors.Success
        !device.online -> "Reconectando…" to AppColors.Warning
        device.muted -> "Silenciado" to MaterialTheme.colorScheme.onSurfaceVariant
        !device.sharing -> "Sin audio" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "En espera" to MaterialTheme.colorScheme.primary
    }
    // Every row has the same shape, so the list reads as a table
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (playing) AppColors.Success.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PriorityBadge(label, accent)
            Column(Modifier.weight(1f)) {
                Text(
                    if (isSelf) "${device.name} (tú)" else device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$status · ${if (device.bluetooth) "Bluetooth" else "Wi-Fi"}${if (device.volumeLocked) " · 🔒" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    maxLines = 1,
                )
            }
            if (playing) Icon(AppIcons.MusicNote, contentDescription = null, tint = AppColors.Success, modifier = Modifier.size(20.dp))
        }
        if (actions != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { actions.onMove(device.id, 0) },
                    enabled = !playing && device.online && device.sharing && !device.muted,
                    modifier = Modifier.weight(1f),
                ) { Text("Poner su música", maxLines = 1) }
                DeviceMenu(device, actions, moveUp, moveDown)
            }
        }
    }
}

/** The driver's less frequent controls, out of sight until needed. */
@Composable
private fun DeviceMenu(device: DeviceInfo, actions: DeviceActions, moveUp: Int?, moveDown: Int?) {
    var open by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val close = { open = false }
    if (renaming) {
        RenameDeviceDialog(
            current = device.name,
            onDismiss = { renaming = false },
            onSave = {
                actions.onRename(device.id, it)
                renaming = false
            },
        )
    }
    // Disconnecting also forgets the phone, so it asks first: a stray tap on a head unit is easy
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("¿Desconectar a ${device.name}?") },
            text = { Text("Para volver tendrá que usar un código.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    actions.onRemove(device.id)
                }) { Text("Desconectar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } },
        )
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones") }
        DropdownMenu(expanded = open, onDismissRequest = close) {
            MenuItem(
                if (device.muted) "Quitar silencio" else "Silenciar",
                if (device.muted) AppIcons.MusicNote else AppIcons.MusicOff,
                close,
            ) { actions.onMute(device.id, !device.muted) }
            MenuItem(if (device.volumeLocked) "Devolver el volumen" else "Bloquear volumen", Icons.Filled.Lock, close) {
                actions.onLockVolume(device.id, !device.volumeLocked)
            }
            MenuItem("Cambiar nombre", Icons.Filled.Edit, close) { renaming = true }
            moveUp?.let { MenuItem("Subir prioridad", Icons.Filled.KeyboardArrowUp, close) { actions.onMove(device.id, it) } }
            moveDown?.let { MenuItem("Bajar prioridad", Icons.Filled.KeyboardArrowDown, close) { actions.onMove(device.id, it) } }
            MenuItem("Desconectar", Icons.Filled.Close, close, MaterialTheme.colorScheme.error) { confirmRemove = true }
        }
    }
}

/** The driver's own name for a phone: "Galaxy de Ana" instead of "SM-S918B". */
@Composable
private fun RenameDeviceDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre del celular") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_DEVICE_NAME) },
                singleLine = true,
                placeholder = { Text("Ej: Celular de Ana") },
            )
        },
        confirmButton = { Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, close: () -> Unit, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) LocalContentColor.current else tint) },
        onClick = {
            close()
            onClick()
        },
    )
}

@Composable
private fun PriorityBadge(label: String, color: Color) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}
