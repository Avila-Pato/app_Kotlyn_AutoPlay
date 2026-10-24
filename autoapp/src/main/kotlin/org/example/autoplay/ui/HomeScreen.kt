package org.example.autoplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Only shown when the app cannot tell what this device is (see DeviceRoles);
 * the answer is remembered, so it is asked once.
 */
@Composable
fun HomeScreen(onCar: () -> Unit, onPhone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Car, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("AutoPlay", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "¿Qué equipo es este?",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(
                    icon = AppIcons.Car,
                    title = "Auto",
                    description = "Recibe y reproduce la música",
                    accent = colors.primary,
                    onClick = onCar,
                )
                ActionCard(
                    icon = AppIcons.Smartphone,
                    title = "Celular",
                    description = "Envía tu música al auto",
                    accent = colors.primary,
                    onClick = onPhone,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Se recuerda en este equipo",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
