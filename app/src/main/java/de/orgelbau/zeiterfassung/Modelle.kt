package de.orgelbau.zeiterfassung

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

data class Baustelle(
    val id: String,
    val name: String,
    val ort: String,
    val kmEinfach: Double?,
    val archiviert: Boolean,
)

data class Taetigkeit(
    val id: String,
    val name: String,
    val reihenfolge: Int,
    val archiviert: Boolean,
)

/** Eine Pause; bis == null heißt: Pause läuft gerade. */
data class Pause(val von: Long, val bis: Long?)

/** Die gerade laufende Zeit einer Person (Firestore: laufend/{uid}). */
data class Laufend(
    val baustelleId: String,
    val taetigkeitId: String,
    val start: Long,
    val pausen: List<Pause>,
) {
    val inPause: Boolean get() = pausen.any { it.bis == null }
}

/** Hülle, damit „noch nicht geladen“ (null) von „nichts läuft“ (wert == null) unterscheidbar ist. */
data class Lauf(val wert: Laufend?)

data class Eintrag(
    val id: String,
    val baustelle: String,
    val taetigkeit: String,
    val notiz: String,
    val start: Long,
    val ende: Long,
    val pauseMin: Int,
    val nettoMin: Int,
)

data class Abschluss(val ende: Long, val pauseMin: Int, val nettoMin: Int)

/** Zeitrechnung wie im Prototyp: minutengenau, keine Rundung auf halbe Stunden. */
object Zeitrechnung {
    private val uhr = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Ergebnis beim Stopp. Stopp während einer Pause: Die Arbeitszeit endet
     * mit dem Pausenbeginn, die offene Pause zählt nicht.
     */
    fun abschluss(start: Long, pausen: List<Pause>, jetzt: Long): Abschluss {
        val ende = pausen.firstOrNull { it.bis == null }?.von ?: jetzt
        val pauseMs = pausen.sumOf { p -> p.bis?.let { it - p.von } ?: 0L }
        return Abschluss(ende, minuten(pauseMs), maxOf(0, minuten(ende - start - pauseMs)))
    }

    /** Bisherige Arbeitszeit; während einer Pause bleibt sie stehen. */
    fun arbeitMs(start: Long, pausen: List<Pause>, jetzt: Long): Long =
        maxOf(0L, jetzt - start - pauseMs(pausen, jetzt))

    fun pauseMs(pausen: List<Pause>, jetzt: Long): Long =
        pausen.sumOf { (it.bis ?: jetzt) - it.von }

    fun minuten(ms: Long): Int = (ms / 60_000.0).roundToLong().toInt()

    fun datum(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

    fun uhrzeit(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(ms).atZone(zone).format(uhr)

    fun hMin(min: Int): String = "%d:%02d".format(min / 60, min % 60)

    fun hms(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    }
}
