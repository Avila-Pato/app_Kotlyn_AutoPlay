package org.example.autoplay.mycar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.autoplay.car.CAR_FONT_SCALE
import org.example.autoplay.car.COMPACT_WIDTH_DP
import org.example.autoplay.ui.ActionCard
import org.example.autoplay.ui.AppIcons
import org.example.autoplay.ui.IconBadge
import org.example.autoplay.ui.ScreenHeader
import org.example.autoplay.ui.SectionCard
import org.example.autoplay.ui.StatusChip
import org.example.autoplay.ui.Tone
import org.example.autoplay.ui.color
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** Callbacks of the cards, so the cards themselves stay free of the view model. */
private class MyCarActions(
    val canManageImages: Boolean,
    val onRemoveCar: (() -> Unit)?,
    val onEditField: (String) -> Unit,
    val onOilDetail: (OilChange) -> Unit,
    val onClearOil: () -> Unit,
    val onAddDocument: () -> Unit,
    val onEditKm: () -> Unit,
    val onAddOil: () -> Unit,
    val onEditDocument: (String) -> Unit,
    val onToggleTask: (String) -> Unit,
    val onAddTask: () -> Unit,
    val onRemoveTask: (String) -> Unit,
)

@Composable
fun MyCarScreen(onBack: () -> Unit, viewModel: MyCarViewModel = hiltViewModel()) {
    val garage by viewModel.garage.collectAsStateWithLifecycle()
    val current = garage
    if (current == null) {
        // The radio keeps no car of its own: it shows the one the phone sends
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Mi auto", null, onBack)
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconBadge(AppIcons.Car, MaterialTheme.colorScheme.onSurfaceVariant, size = 72.dp)
                Text(
                    "Conecta tu celular para ver tu auto",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "Tus autos viven en el celular: al conectarte, esta pantalla muestra el que tengas elegido.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        return
    }
    MyCarContent(current, viewModel, onBack)
}

@Composable
private fun MyCarContent(garage: Garage, viewModel: MyCarViewModel, onBack: () -> Unit) {
    val car = garage.selected
    var addingCar by rememberSaveable { mutableStateOf(false) }
    var removingCar by rememberSaveable { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    var editingKm by rememberSaveable { mutableStateOf(false) }
    var addingOil by rememberSaveable { mutableStateOf(false) }
    var addingTask by rememberSaveable { mutableStateOf(false) }
    var editingDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingFieldLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var addingDocument by rememberSaveable { mutableStateOf(false) }
    var clearingOil by rememberSaveable { mutableStateOf(false) }
    var viewingOil by remember { mutableStateOf<OilChange?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    if (editingKm) {
        KmDialog(
            current = car.km,
            minimum = lastKnownKm(car),
            onDismiss = { editingKm = false },
            onSave = { km ->
                if (viewModel.updateKm(km)) editingKm = false
            },
        )
    }
    if (addingOil) {
        OilDialog(
            car = car,
            today = today,
            onDismiss = { addingOil = false },
            onSave = { change ->
                viewModel.addOilChange(change)
                addingOil = false
            },
        )
    }
    if (addingTask) {
        TaskDialog(
            onDismiss = { addingTask = false },
            onSave = { text ->
                viewModel.addTask(text)
                addingTask = false
            },
        )
    }
    car.documents.firstOrNull { it.id == editingDocumentId }?.let { document ->
        DocumentDialog(
            document = document,
            images = viewModel.images.takeIf { viewModel.canManageGarage },
            onDismiss = { editingDocumentId = null },
            onSave = { due, note ->
                viewModel.updateDocument(document.id, due?.date, due?.text.orEmpty(), note)
                editingDocumentId = null
            },
            onImageAdded = { name -> viewModel.addImage(document.id, name) },
            onImageRemoved = { name -> viewModel.removeImage(document.id, name) },
            onDelete = {
                viewModel.removeDocument(document.id)
                editingDocumentId = null
            },
        )
    }
    if (addingDocument) {
        NewDocumentDialog(
            onDismiss = { addingDocument = false },
            onSave = { name, due, note ->
                viewModel.addDocument(name, due?.date, due?.text.orEmpty(), note)
                addingDocument = false
            },
        )
    }
    viewingOil?.let { change ->
        val position = car.oilChanges.indexOf(change)
        OilDetailDialog(
            change = change,
            previous = car.oilChanges.getOrNull(position + 1),
            onDismiss = { viewingOil = null },
            onDelete = {
                viewModel.removeOilChange(change)
                viewingOil = null
            },
        )
    }
    if (clearingOil) {
        ConfirmDialog(
            title = "¿Borrar todo el historial de aceite?",
            text = "Se borran los ${car.oilChanges.size} cambios registrados. El kilometraje actual se queda como está.",
            confirmLabel = "Borrar todo",
            onConfirm = {
                viewModel.clearOilHistory()
                clearingOil = false
            },
            onDismiss = { clearingOil = false },
        )
    }

    PROFILE_FIELDS.firstOrNull { it.label == editingFieldLabel }?.let { field ->
        FieldDialog(
            label = field.label,
            current = field.value(car.profile),
            numeric = field.label == "Año",
            onDismiss = { editingFieldLabel = null },
            onSave = { value ->
                viewModel.updateProfile(field.update(car.profile, value))
                editingFieldLabel = null
            },
        )
    }

    if (addingCar) {
        NewCarDialog(
            onDismiss = { addingCar = false },
            onSave = { name ->
                viewModel.addCar(name)
                tab = 3 // the new car is empty: the data sheet is where it starts
                addingCar = false
            },
        )
    }
    if (removingCar) {
        ConfirmDialog(
            title = "¿Borrar ${car.profile.name.ifBlank { "este auto" }}?",
            text = "Se pierden sus datos: aceite, papeles, pendientes y ficha.",
            confirmLabel = "Borrar auto",
            onConfirm = {
                viewModel.removeCar(car.id)
                removingCar = false
                tab = 0
            },
            onDismiss = { removingCar = false },
        )
    }

    val actions = MyCarActions(
        canManageImages = viewModel.canManageGarage,
        onRemoveCar = { removingCar = true }.takeIf { viewModel.canManageGarage && garage.cars.size > 1 },
        onEditField = { editingFieldLabel = it },
        onOilDetail = { viewingOil = it },
        onClearOil = { clearingOil = true },
        onAddDocument = { addingDocument = true },
        onEditKm = { editingKm = true },
        onAddOil = { addingOil = true },
        onEditDocument = { editingDocumentId = it },
        onToggleTask = viewModel::toggleTask,
        onAddTask = { addingTask = true },
        onRemoveTask = viewModel::removeTask,
    )

    // Same sizing rule as the car screen: a head unit is read from afar, a phone keeps its normal size
    val compact = LocalConfiguration.current.screenWidthDp < COMPACT_WIDTH_DP
    val density = LocalDensity.current
    val fontScale = if (compact) density.fontScale else density.fontScale * CAR_FONT_SCALE
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Mi auto", listOf(car.profile.name, car.profile.plate).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }, onBack)
            if (viewModel.canManageGarage) {
                GaragePicker(garage, onSelect = viewModel::selectCar, onAdd = { addingCar = true })
            }
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                val wide = maxWidth > maxHeight && maxWidth >= 600.dp
                if (wide) {
                    // Radio: the state of the car stays in view on the left, one section at a time on the right
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(0.85f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                            HeroCard(car, today, actions, tab, { tab = it }, wide = true)
                        }
                        Column(Modifier.weight(1.15f).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TabPills(car, tab) { tab = it }
                            Column(
                                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                TabContent(tab, car, today, actions)
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HeroCard(car, today, actions, tab, { tab = it }, wide = false)
                        TabPills(car, tab) { tab = it }
                        TabContent(tab, car, today, actions)
                    }
                }
            }
        }
    }
}

/** Entry point shown on the phone: opens "Mi auto" and previews the most urgent item. */
@Composable
fun MyCarEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    viewModel: MyCarViewModel = hiltViewModel(),
) {
    val car by viewModel.car.collectAsStateWithLifecycle()
    val garage by viewModel.garage.collectAsStateWithLifecycle()
    val urgent = remember(car) { car?.let { mostUrgent(it, LocalDate.now()) } }
    val tone = urgent?.tone ?: Tone.Progress
    val accent = if (tone == Tone.Success) MaterialTheme.colorScheme.primary else tone.color()
    val severalCars = (garage?.cars?.size ?: 0) > 1
    val description = when {
        car == null -> "Conecta tu celular para ver tu auto"
        severalCars -> listOfNotNull(car?.profile?.name?.takeIf { it.isNotBlank() }, urgent?.text).joinToString(" · ")
        else -> urgent?.text ?: "Papeles, aceite y pendientes"
    }
    if (compact) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(AppIcons.Car, accent, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text("Mi auto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tone == Tone.Warning || tone == Tone.Error) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        ActionCard(
            icon = AppIcons.Car,
            title = "Mi auto",
            description = description,
            accent = accent,
            onClick = onClick,
            modifier = modifier,
        )
    }
}

private enum class MyCarTab(val label: String) { Oil("Aceite"), Papers("Papeles"), Tasks("Pendientes"), Profile("Ficha") }

/** One ring of the summary: how much is left of the oil interval or of a paper's validity. */
private class Gauge(val label: String, val value: String, val unit: String, val fraction: Float, val tone: Tone, val tab: MyCarTab)

private fun gaugesOf(car: MyCar, today: LocalDate): List<Gauge> {
    val left = kmToNextOil(car)
    val oil = Gauge(
        "Aceite",
        left?.let { formatKm(maxOf(it, 0)) } ?: "—",
        "km",
        oilRemainingFraction(car),
        left?.let(::oilTone) ?: Tone.Neutral,
        MyCarTab.Oil,
    )
    val papers = car.documents.filter { it.expiresOn != null }.map { document ->
        val days = documentDaysLeft(document, today)
        Gauge(
            document.shortName().substringBefore(' '),
            days?.let { maxOf(it, 0L).toString() } ?: "—",
            "días",
            documentRemainingFraction(days),
            documentTone(days),
            MyCarTab.Papers,
        )
    }
    return listOf(oil) + papers
}

/** The state of the car at a glance: the odometer and a ring for the oil and for each paper. */
@Composable
private fun HeroCard(car: MyCar, today: LocalDate, actions: MyCarActions, tab: Int, onSelect: (Int) -> Unit, wide: Boolean) {
    val gauges = gaugesOf(car, today)
    SectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kilometraje actual", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${formatKm(car.km)} km", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Al ${formatDate(car.kmDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = actions.onEditKm) { Text("Actualizar km") }
            }
            mostUrgent(car, today)?.let { StatusChip(it.text, it.tone) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Up to four rings share the width; with more papers the row scrolls
            val scrolls = gauges.size > 4
            Row(
                if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                gauges.forEach { gauge ->
                    GaugeTile(
                        gauge,
                        selected = MyCarTab.entries[tab] == gauge.tab,
                        ring = if (wide) 64.dp else 56.dp,
                        modifier = if (scrolls) Modifier.width(88.dp) else Modifier.weight(1f),
                    ) {
                        onSelect(gauge.tab.ordinal)
                    }
                }
            }
        }
    }
}

@Composable
private fun GaugeTile(gauge: Gauge, selected: Boolean, ring: Dp, modifier: Modifier, onClick: () -> Unit) {
    val color = gauge.tone.color()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) color.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(ring)) {
                val stroke = 8.dp.toPx()
                val arc = Size(size.width - stroke, size.height - stroke)
                val corner = Offset(stroke / 2, stroke / 2)
                drawArc(track, 0f, 360f, false, corner, arc, style = Stroke(stroke))
                if (gauge.fraction > 0f) {
                    drawArc(color, -90f, 360f * gauge.fraction, false, corner, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Text(
                gauge.value,
                style = if (gauge.value.length > 4) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Text(gauge.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(gauge.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The sections as big touch targets; "Pendientes" shows how many are still open. */
@Composable
private fun TabPills(car: MyCar, selected: Int, onSelect: (Int) -> Unit) {
    val open = car.tasks.count { !it.done }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MyCarTab.entries.forEach { tab ->
            val active = tab.ordinal == selected
            val label = if (tab == MyCarTab.Tasks && open > 0) "${tab.label} · $open" else tab.label
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSelect(tab.ordinal) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TabContent(tab: Int, car: MyCar, today: LocalDate, actions: MyCarActions) {
    when (MyCarTab.entries[tab]) {
        MyCarTab.Oil -> OilCard(car, actions)
        MyCarTab.Papers -> DocumentsCard(car, today, actions)
        MyCarTab.Tasks -> TasksCard(car, actions)
        MyCarTab.Profile -> {
            ProfileCard(car.profile, actions.onEditField)
            if (car.notes.isNotEmpty()) NotesCard(car.notes)
            actions.onRemoveCar?.let { remove ->
                TextButton(onClick = remove, modifier = Modifier.fillMaxWidth()) {
                    Text("Borrar este auto", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun OilCard(car: MyCar, actions: MyCarActions) {
    val last = lastOilChange(car)
    val left = kmToNextOil(car)
    var showAll by rememberSaveable { mutableStateOf(false) }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aceite", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = actions.onAddOil) { Text("Registrar cambio") }
            }
            if (last == null || left == null) {
                Text(
                    "Aún no hay cambios registrados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val tone = oilTone(left)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (left > 0) "Faltan ${formatKm(left)} km" else "Pasado por ${formatKm(-left)} km",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(
                        when (tone) {
                            Tone.Success -> "Al día"
                            Tone.Warning -> "Pronto"
                            else -> "Cambiar"
                        },
                        tone,
                    )
                }
                estimatedNextOilDate(car)?.let {
                    Text("Próximo cambio estimado: ${formatDate(it)}", style = MaterialTheme.typography.bodyMedium)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { oilProgress(car) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = tone.color(),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${formatKm(last.km)} km", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatKm(last.nextKm)} km", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (car.oilChanges.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Historial", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (if (showAll) car.oilChanges else car.oilChanges.take(OIL_HISTORY_PREVIEW)).forEach { change ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { actions.onOilDetail(change) }
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDate(change.date), style = MaterialTheme.typography.titleSmall)
                            Text("${formatKm(change.km)} km", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            "${change.oil} · próximo a los ${formatKm(change.nextKm)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (car.oilChanges.size > OIL_HISTORY_PREVIEW) {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAll) "Ver menos" else "Ver los ${car.oilChanges.size - OIL_HISTORY_PREVIEW} anteriores")
                }
            }
            if (car.oilChanges.isNotEmpty()) {
                TextButton(onClick = actions.onClearOil, modifier = Modifier.fillMaxWidth()) {
                    Text("Borrar historial", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DocumentsCard(car: MyCar, today: LocalDate, actions: MyCarActions) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Papeles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            car.documents.forEach { document ->
                val days = documentDaysLeft(document, today)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { actions.onEditDocument(document.id) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(document.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (document.expiresOn == null) "Sin vencimiento" else "Vence: ${documentExpiryText(document)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (document.images.isNotEmpty()) {
                            val count = document.images.size
                            Text(
                                (if (count == 1) "1 imagen" else "$count imágenes") + if (actions.canManageImages) "" else " (verlas en el celular)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (document.note.isNotBlank()) {
                            Text(document.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    StatusChip(days?.let { dueText(it).replaceFirstChar(Char::uppercase) } ?: "Sin vencimiento", documentTone(days))
                }
            }
            Text(
                if (actions.canManageImages) "Toca un papel para cambiar su fecha, agregar fotos o borrarlo" else "Toca un papel para cambiar su fecha o borrarlo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = actions.onAddDocument, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Agregar papel")
            }
        }
    }
}

@Composable
private fun TasksCard(car: MyCar, actions: MyCarActions) {
    var showDone by rememberSaveable { mutableStateOf(false) }
    val done = car.tasks.count { it.done }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pendientes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (car.tasks.isNotEmpty()) {
                    Text(
                        "${car.tasks.count { it.done }} de ${car.tasks.size} listos",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // What is still to do comes first; the done ones wait behind a button
            car.tasks.sortedBy { it.done }.filter { showDone || !it.done }.forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.done, onCheckedChange = { actions.onToggleTask(task.id) })
                    Text(
                        task.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { actions.onRemoveTask(task.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Borrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (done > 0) {
                TextButton(onClick = { showDone = !showDone }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showDone) "Ocultar los hechos" else "Ver los $done hechos")
                }
            }
            if (car.tasks.isEmpty()) {
                Text("Nada pendiente", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = actions.onAddTask, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Agregar pendiente")
            }
        }
    }
}

/** A line of the data sheet and how to change it. */
private class ProfileField(val label: String, val value: (CarProfile) -> String, val update: (CarProfile, String) -> CarProfile)

private val PROFILE_FIELDS = listOf(
    ProfileField("Nombre del auto", { it.name }, { p, v -> p.copy(name = v) }),
    ProfileField("Marca", { it.brand }, { p, v -> p.copy(brand = v) }),
    ProfileField("Modelo", { it.model }, { p, v -> p.copy(model = v) }),
    ProfileField("Versión", { it.trim }, { p, v -> p.copy(trim = v) }),
    ProfileField("Año", { it.year.toString() }, { p, v -> v.toIntOrNull()?.takeIf { it in 1900..2100 }?.let { p.copy(year = it) } ?: p }),
    ProfileField("Patente", { it.plate }, { p, v -> p.copy(plate = v) }),
    ProfileField("Motor", { it.engine }, { p, v -> p.copy(engine = v) }),
    ProfileField("Chasis / VIN", { it.vin }, { p, v -> p.copy(vin = v) }),
    ProfileField("Color", { it.color }, { p, v -> p.copy(color = v) }),
    ProfileField("Combustible", { it.fuel }, { p, v -> p.copy(fuel = v) }),
    ProfileField("Transmisión", { it.transmission }, { p, v -> p.copy(transmission = v) }),
)

@Composable
private fun ProfileCard(profile: CarProfile, onEdit: (String) -> Unit) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Ficha", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Toca un dato para cambiarlo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            PROFILE_FIELDS.forEach { field ->
                ProfileRow(field.label, field.value(profile)) { onEdit(field.label) }
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun NotesCard(notes: List<String>) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Notas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            notes.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FieldDialog(label: String, current: String, numeric: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = if (numeric) it.filter(Char::isDigit).take(4) else it.take(80) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            )
        },
        confirmButton = { Button(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun KmDialog(current: Int, minimum: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current.toString()) }
    val km = text.toIntOrNull()
    val tooLow = km != null && km < minimum
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Actualizar kilometraje") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(7) },
                label = { Text("Kilómetros") },
                singleLine = true,
                isError = tooLow,
                supportingText = { if (tooLow) Text("El kilometraje no puede bajar") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { Button(onClick = { km?.let(onSave) }, enabled = km != null && !tooLow) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun OilDialog(car: MyCar, today: LocalDate, onDismiss: () -> Unit, onSave: (OilChange) -> Unit) {
    var date by rememberSaveable { mutableStateOf(formatDate(today)) }
    var kmText by rememberSaveable { mutableStateOf(car.km.toString()) }
    var oil by rememberSaveable { mutableStateOf("5W-30") }
    // The next change defaults to 10.000 km later until the user types their own
    var nextText by rememberSaveable { mutableStateOf((car.km + OIL_INTERVAL_KM).toString()) }
    var nextEdited by rememberSaveable { mutableStateOf(false) }

    val parsedDate = parseDate(date)
    val km = kmText.toIntOrNull()
    val next = nextText.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar cambio de aceite") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    label = { Text("Fecha (día/mes/año)") },
                    singleLine = true,
                    isError = date.isNotEmpty() && parsedDate == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = kmText,
                    onValueChange = {
                        kmText = it.filter(Char::isDigit).take(7)
                        if (!nextEdited) nextText = kmText.toIntOrNull()?.let { value -> (value + OIL_INTERVAL_KM).toString() }.orEmpty()
                    },
                    label = { Text("Kilometraje") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = oil,
                    onValueChange = { oil = it.take(30) },
                    label = { Text("Aceite") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = nextText,
                    onValueChange = {
                        nextText = it.filter(Char::isDigit).take(7)
                        nextEdited = true
                    },
                    label = { Text("Próximo cambio (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(OilChange(parsedDate!!, km!!, oil.trim(), next!!)) },
                enabled = parsedDate != null && km != null && next != null && oil.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DocumentDialog(
    document: CarDocument,
    images: DocumentImages?,
    onDismiss: () -> Unit,
    onSave: (ParsedDue?, String) -> Unit,
    onDelete: () -> Unit,
    onImageAdded: (String) -> Unit,
    onImageRemoved: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    // A month-only expiry is shown as month/year so saving it unchanged keeps its label
    var date by rememberSaveable(document.id) {
        mutableStateOf(
            document.expiresOn?.let { if (document.expiresText.isBlank()) formatDate(it) else "%02d/%d".format(it.monthValue, it.year) }.orEmpty(),
        )
    }
    var note by rememberSaveable(document.id) { mutableStateOf(document.note) }
    val due = parseDue(date)
    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Borrar ${document.name}?",
            text = "Deja de aparecer en Mi auto y se pierden su fecha y sus imágenes.",
            confirmLabel = "Borrar",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(document.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    label = { Text("Vence el (opcional)") },
                    singleLine = true,
                    isError = date.isNotEmpty() && due == null,
                    supportingText = { Text("Día/mes/año o mes/año. Vacío si no vence.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("Nota") },
                )
                if (images != null) {
                    ImagesSection(document.images, images, onImageAdded, onImageRemoved)
                } else if (document.images.isNotEmpty()) {
                    Text(
                        "${document.images.size} imágenes (verlas en el celular)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Borrar este papel", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(due, note.trim()) }, enabled = date.isBlank() || due != null) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun TaskDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo pendiente") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(150) },
                singleLine = true,
                placeholder = { Text("¿Qué falta hacer?") },
            )
        },
        confirmButton = { Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Agregar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** The driver's cars as chips, plus the one that adds another: the radio shows whichever is in view. */
@Composable
private fun GaragePicker(garage: Garage, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        garage.cars.forEach { car ->
            val active = car.id == garage.selectedId
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onSelect(car.id) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Car, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        car.profile.name.ifBlank { "Auto sin nombre" },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onAdd)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("+ Agregar otro auto", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** A brand new car: only its name is asked, the rest is filled in on its data sheet. */
@Composable
private fun NewCarDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar otro auto") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Nombre del auto") },
                placeholder = { Text("Ej: Auto de mi papá") },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Agregar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private val DOCUMENT_SUGGESTIONS = listOf(
    "Licencia de conducir",
    "Certificado de inscripción",
    "Certificado de revisión técnica",
    "Permiso de circulación",
    "SOAP",
    "Seguro",
    "Certificado de homologación",
    "Padrón",
)

/** The pictures of a paper: photos taken now or chosen from the gallery, small ones to tap and a big one to read. */
@Composable
private fun ImagesSection(names: List<String>, store: DocumentImages, onAdded: (String) -> Unit, onRemoved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var viewing by remember { mutableStateOf<String?>(null) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { store.import(uri)?.let(onAdded) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = pendingPhoto
        pendingPhoto = null
        if (file != null) {
            scope.launch {
                if (taken) store.importFile(file)?.let(onAdded)
                file.delete()
            }
        }
    }
    val full = names.size >= MAX_IMAGES_PER_DOCUMENT
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Imágenes · ${names.size} de $MAX_IMAGES_PER_DOCUMENT", style = MaterialTheme.typography.labelLarge)
        if (names.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEach { name -> Thumbnail(name, store) { viewing = name } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val (file, uri) = store.newCameraTarget()
                    pendingPhoto = file
                    camera.launch(uri)
                },
                enabled = !full,
            ) { Text("Tomar foto") }
            OutlinedButton(
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !full,
            ) { Text("Galería") }
        }
        if (full) Text("Llegaste al máximo de imágenes de este papel.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    viewing?.let { name ->
        ImageViewerDialog(
            name = name,
            store = store,
            onDismiss = { viewing = null },
            onDelete = {
                onRemoved(name)
                viewing = null
            },
        )
    }
}

@Composable
private fun Thumbnail(name: String, store: DocumentImages, onClick: () -> Unit) {
    val image by produceState<ImageBitmap?>(null, name) { value = store.load(name, THUMBNAIL_SIDE) }
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        image?.let { Image(it, contentDescription = "Imagen del papel", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** A picture full screen: pinch to zoom, drag to move. It can only be deleted from here, never edited. */
@Composable
private fun ImageViewerDialog(name: String, store: DocumentImages, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val image by produceState<ImageBitmap?>(null, name) { value = store.load(name, MAX_IMAGE_SIDE) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
            image?.let {
                Image(
                    it,
                    contentDescription = "Imagen del papel",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                offset = if (scale <= 1f) Offset.Zero else offset + pan
                            }
                        },
                )
            }
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { confirmDelete = true }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                Button(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Eliminar esta imagen?",
            text = "Se borra del celular y no se puede recuperar.",
            confirmLabel = "Eliminar",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}

/** A paper of the driver's own: a name, when it expires and an optional note. */
@Composable
private fun NewDocumentDialog(onDismiss: () -> Unit, onSave: (String, ParsedDue?, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val due = parseDue(date)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar papel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Elige uno o escribe el nombre que quieras", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DOCUMENT_SUGGESTIONS.forEach { suggestion ->
                        Box(
                            Modifier
                                .heightIn(min = 40.dp)
                                .clip(CircleShape)
                                .border(1.dp, if (name == suggestion) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .background(if (name == suggestion) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { name = suggestion }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(suggestion, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nombre") },
                    placeholder = { Text("Otro: escribe el nombre") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    label = { Text("Vence el (opcional)") },
                    singleLine = true,
                    isError = date.isNotEmpty() && due == null,
                    supportingText = { Text("Día/mes/año o mes/año. Vacío si no vence, como la inscripción.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(value = note, onValueChange = { note = it.take(200) }, label = { Text("Nota (opcional)") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, due, note.trim()) }, enabled = name.isNotBlank() && (date.isBlank() || due != null)) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** One oil change as it was recorded: read-only, with a way to delete it. */
@Composable
private fun OilDetailDialog(change: OilChange, previous: OilChange?, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        ConfirmDialog(
            title = "¿Borrar este cambio de aceite?",
            text = "Cambio del ${formatDate(change.date)} a los ${formatKm(change.km)} km.",
            confirmLabel = "Borrar",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambio de aceite") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow("Fecha", formatDate(change.date))
                DetailRow("Kilometraje", "${formatKm(change.km)} km")
                DetailRow("Aceite", change.oil)
                DetailRow("Próximo cambio", "${formatKm(change.nextKm)} km")
                previous?.let {
                    val (km, days) = oilInterval(change, it)
                    DetailRow("Desde el anterior", "${formatKm(km)} km en $days días")
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Cerrar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = true }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.55f))
    }
}

/** Asks before something is lost for good. */
@Composable
private fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private const val OIL_INTERVAL_KM = 10_000
private const val THUMBNAIL_SIDE = 240
private const val MAX_ZOOM = 5f
private const val OIL_HISTORY_PREVIEW = 2
