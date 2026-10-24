package org.example.autoplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.autoplay.net.DeviceInfo

/** Actions only the car screen has: the driver decides the priority. */
class DeviceActions(
    val onMove: (deviceId: String, position: Int) -> Unit,
    val onRemove: (deviceId: String) -> Unit,
)

/**
 * Connected phones in priority order. The one playing music is highlighted; [selfId] marks
 * "this phone" on the phone side, and [actions] adds the priority controls on the car side.
 */
@Composable
fun DeviceList(
    devices: List<DeviceInfo>,
    playingId: String?,
    modifier: Modifier = Modifier,
    selfId: String? = null,
    actions: DeviceActions? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Dispositivos conectados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${devices.count { it.online }} de ${devices.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (devices.isEmpty()) {
            Text(
                "Todavía no hay celulares conectados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        devices.forEachIndexed { index, device ->
            DeviceRow(
                device = device,
                position = index,
                count = devices.size,
                playing = device.id == playingId,
                isSelf = device.id == selfId,
                actions = actions,
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: DeviceInfo,
    position: Int,
    count: Int,
    playing: Boolean,
    isSelf: Boolean,
    actions: DeviceActions?,
) {
    val accent = when {
        playing -> AppColors.Success
        !device.online -> AppColors.Warning
        else -> MaterialTheme.colorScheme.primary
    }
    val (label, tone) = when {
        playing -> "Sonando en el auto" to Tone.Success
        !device.online -> "Reconectando…" to Tone.Warning
        !device.sharing -> "Sin audio activado" to Tone.Neutral
        else -> "En espera" to Tone.Progress
    }
    // Every row has the same two lines and the same controls, so the list reads as a table
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (playing) AppColors.Success.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, if (playing) AppColors.Success.copy(alpha = 0.5f) else Color.Transparent, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PriorityBadge(position + 1, accent)
            IconBadge(if (playing) AppIcons.MusicNote else AppIcons.Smartphone, accent, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (isSelf) "${device.name} (tú)" else device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (device.bluetooth) "Por Bluetooth" else "Por Wi-Fi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            StatusChip(label, tone)
        }
        if (actions != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { actions.onMove(device.id, 0) },
                    enabled = !playing && device.online && device.sharing,
                    modifier = Modifier.weight(1f),
                ) { Text(if (playing) "Está sonando" else "Poner su música", maxLines = 1) }
                IconButton(onClick = { actions.onMove(device.id, position - 1) }, enabled = position > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir prioridad")
                }
                IconButton(onClick = { actions.onMove(device.id, position + 1) }, enabled = position < count - 1) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Bajar prioridad")
                }
                IconButton(onClick = { actions.onRemove(device.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Desconectar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PriorityBadge(number: Int, color: Color) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", style = MaterialTheme.typography.labelLarge, color = color)
    }
}
