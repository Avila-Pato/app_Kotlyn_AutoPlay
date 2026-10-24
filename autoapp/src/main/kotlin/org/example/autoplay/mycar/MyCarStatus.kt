package org.example.autoplay.mycar

import org.example.autoplay.ui.Tone
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil

/** Oil is due after this many months even if the km limit is not reached. */
private const val OIL_MAX_MONTHS = 12L

private val MONTHS = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
)
private val DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val INPUT_DATE = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT)
private val MONTH_YEAR = Regex("""(\d{1,2})/(\d{4})""")

/** The highest odometer reading on record. */
fun lastKnownKm(car: MyCar): Int = maxOf(car.km, car.oilChanges.maxOfOrNull { it.km } ?: 0)

/** The odometer never goes down. */
fun isValidKm(car: MyCar, km: Int): Boolean = km >= lastKnownKm(car)

fun lastOilChange(car: MyCar): OilChange? = car.oilChanges.maxByOrNull { it.date }

/** Km left until the next oil change; negative when it is overdue. Null without any change on record. */
fun kmToNextOil(car: MyCar): Int? = lastOilChange(car)?.let { it.nextKm - car.km }

/** Share of the way from the last oil change to the next one, 0..1. */
fun oilProgress(car: MyCar): Float {
    val last = lastOilChange(car) ?: return 0f
    val span = last.nextKm - last.km
    if (span <= 0) return 1f
    return ((car.km - last.km).toFloat() / span).coerceIn(0f, 1f)
}

/** What the car did between two oil changes: km driven and days that passed. */
fun oilInterval(newer: OilChange, older: OilChange): Pair<Int, Long> =
    (newer.km - older.km) to ChronoUnit.DAYS.between(older.date, newer.date)

/** How much of the oil interval is left, 1 right after a change and 0 when it is due; 0 with no change on record. */
fun oilRemainingFraction(car: MyCar): Float = if (lastOilChange(car) == null) 0f else 1f - oilProgress(car)

/** How much of a year is left before a paper expires (capped at 1); 0 with no date or once it expired. */
fun documentRemainingFraction(daysLeft: Long?): Float = if (daysLeft == null) 0f else (daysLeft / 365f).coerceIn(0f, 1f)

/**
 * When the next oil change falls: the pace between the two latest readings (oil changes plus the
 * current km) projected onto the km that remain, or 12 months after the last change, whichever comes first.
 */
fun estimatedNextOilDate(car: MyCar): LocalDate? {
    val last = lastOilChange(car) ?: return null
    val byTime = last.date.plusMonths(OIL_MAX_MONTHS)
    val remaining = last.nextKm - car.km
    if (remaining <= 0) return minOf(car.kmDate, byTime)

    val readings = (car.oilChanges.map { it.date to it.km } + (car.kmDate to car.km)).sortedBy { it.first }
    if (readings.size < 2) return byTime
    val (fromDate, fromKm) = readings[readings.size - 2]
    val (toDate, toKm) = readings.last()
    val days = ChronoUnit.DAYS.between(fromDate, toDate)
    val driven = toKm - fromKm
    if (days <= 0 || driven <= 0) return byTime
    val daysToGo = ceil(remaining.toDouble() * days / driven).toLong()
    return minOf(toDate.plusDays(daysToGo), byTime)
}

fun documentDaysLeft(document: CarDocument, today: LocalDate): Long? =
    document.expiresOn?.let { ChronoUnit.DAYS.between(today, it) }

fun documentTone(daysLeft: Long?): Tone = when {
    daysLeft == null -> Tone.Neutral
    daysLeft > 60 -> Tone.Success
    daysLeft >= 15 -> Tone.Warning
    else -> Tone.Error
}

fun oilTone(kmLeft: Int): Tone = when {
    kmLeft > 2_000 -> Tone.Success
    kmLeft >= 500 -> Tone.Warning
    else -> Tone.Error
}

/**
 * "Permiso de circulación" -> "Permiso" (the name up to the first " de "), and a certificate by what it certifies:
 * "Certificado de inscripción" -> "Inscripción".
 */
fun CarDocument.shortName(): String =
    name.trim().removePrefix("Certificado de ").removePrefix("certificado de ").substringBefore(" de ")
        .replaceFirstChar { it.uppercase() }

fun dueText(daysLeft: Long): String = when {
    daysLeft > 1 -> "vence en $daysLeft días"
    daysLeft == 1L -> "vence mañana"
    daysLeft == 0L -> "vence hoy"
    daysLeft == -1L -> "venció ayer"
    else -> "venció hace ${-daysLeft} días"
}

/** What the card shows as the expiry: the loose text when there is one, else the date. */
fun documentExpiryText(document: CarDocument): String = when {
    document.expiresText.isNotBlank() -> document.expiresText
    document.expiresOn != null -> formatDate(document.expiresOn)
    else -> "Sin fecha"
}

class Urgent(val text: String, val tone: Tone)

/** The single most pressing thing for the summary: the worst tone wins, oil first among equals. */
fun mostUrgent(car: MyCar, today: LocalDate): Urgent? {
    val candidates = buildList {
        kmToNextOil(car)?.let { left ->
            add(Urgent(if (left > 0) "Aceite en ${formatKm(left)} km" else "Toca cambio de aceite", oilTone(left)))
        }
        car.documents
            .mapNotNull { doc -> documentDaysLeft(doc, today)?.let { doc to it } }
            .sortedBy { it.second }
            .forEach { (doc, days) -> add(Urgent("${doc.shortName()} ${dueText(days)}", documentTone(days))) }
    }
    return candidates.maxByOrNull { severity(it.tone) }
}

private fun severity(tone: Tone): Int = when (tone) {
    Tone.Error -> 2
    Tone.Warning -> 1
    else -> 0
}

/** 83934 -> "83.934" */
fun formatKm(km: Int): String {
    val digits = abs(km).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (km < 0) "-$digits" else digits
}

fun formatDate(date: LocalDate): String = date.format(DISPLAY_DATE)

/** Parses "d/M/yyyy"; null for anything that is not a real date. */
fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim(), INPUT_DATE) }.getOrNull()

/** A user-typed expiry: [date] drives the countdown, [text] is kept only for month-only input. */
class ParsedDue(val date: LocalDate, val text: String)

/** Accepts "d/M/yyyy" (exact day) or "M/yyyy" (last day of that month, shown as "Abril 2027"). */
fun parseDue(input: String): ParsedDue? {
    parseDate(input)?.let { return ParsedDue(it, "") }
    val match = MONTH_YEAR.matchEntire(input.trim()) ?: return null
    val month = match.groupValues[1].toInt()
    val year = match.groupValues[2].toInt()
    if (month !in 1..12) return null
    return ParsedDue(YearMonth.of(year, month).atEndOfMonth(), "${MONTHS[month - 1]} $year")
}
