package de.orgelbau.zeiterfassung

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    val person: String,
    val personUid: String,
    val baustelleId: String,
    val baustelle: String,
    val ort: String,
    val kmEinfach: Double?,
    val taetigkeitId: String,
    val taetigkeit: String,
    val notiz: String,
    val start: Long,
    val ende: Long,
    val pauseMin: Int,
    val nettoMin: Int,
    val datum: String,
    val quelle: String,
    val erstellt: Long,
)

data class Abschluss(val ende: Long, val pauseMin: Int, val nettoMin: Int)

/** Eingaben im Formular „Eintrag bearbeiten / Zeit nachtragen“. id == null heißt: neuer Eintrag. */
data class EintragEntwurf(
    val id: String?,
    val person: String,
    val personUid: String,
    val quelle: String,
    val erstellt: Long?,
    val datum: LocalDate,
    val beginn: LocalTime,
    val ende: LocalTime,
    val pauseMin: Int,
    val baustelleId: String?,
    val taetigkeitId: String?,
    val notiz: String,
) {
    companion object {
        fun aus(e: Eintrag, zone: ZoneId = ZoneId.systemDefault()): EintragEntwurf {
            val s = Instant.ofEpochMilli(e.start).atZone(zone)
            val en = Instant.ofEpochMilli(e.ende).atZone(zone)
            return EintragEntwurf(
                id = e.id, person = e.person, personUid = e.personUid, quelle = e.quelle, erstellt = e.erstellt,
                datum = s.toLocalDate(),
                beginn = s.toLocalTime().withSecond(0).withNano(0),
                ende = en.toLocalTime().withSecond(0).withNano(0),
                pauseMin = e.pauseMin,
                baustelleId = e.baustelleId, taetigkeitId = e.taetigkeitId, notiz = e.notiz,
            )
        }
    }
}

data class Zeitspanne(val start: Long, val ende: Long, val nettoMin: Int)

/** Zeitrechnung wie im Prototyp: minutengenau, keine Rundung auf halbe Stunden. */
object Zeitrechnung {
    private val uhr = DateTimeFormatter.ofPattern("HH:mm")
    private val tag = DateTimeFormatter.ofPattern("EE dd.MM.yy", Locale.GERMAN)
    private val kurz = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /**
     * Ergebnis beim Stopp. Stopp während einer Pause: Die Arbeitszeit endet
     * mit dem Pausenbeginn, die offene Pause zählt nicht.
     */
    fun abschluss(start: Long, pausen: List<Pause>, jetzt: Long): Abschluss {
        val ende = pausen.firstOrNull { it.bis == null }?.von ?: jetzt
        val pauseMs = pausen.sumOf { p -> p.bis?.let { it - p.von } ?: 0L }
        return Abschluss(ende, minuten(pauseMs), maxOf(0, minuten(ende - start - pauseMs)))
    }

    /** Zeit von Hand: Ende vor Beginn heißt nächster Tag. */
    fun ausFormular(
        datum: LocalDate,
        beginn: LocalTime,
        ende: LocalTime,
        pauseMin: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Zeitspanne {
        val s = datum.atTime(beginn).atZone(zone)
        val e = (if (ende.isBefore(beginn)) datum.plusDays(1) else datum).atTime(ende).atZone(zone)
        val startMs = s.toInstant().toEpochMilli()
        val endeMs = e.toInstant().toEpochMilli()
        return Zeitspanne(startMs, endeMs, maxOf(0, minuten(endeMs - startMs) - pauseMin))
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

    fun uhrzeit(t: LocalTime): String = t.format(uhr)

    /** „Mo. 12.10.26“ aus „2026-10-12“ */
    fun tagText(datum: String): String = LocalDate.parse(datum).format(tag)

    fun datumText(d: LocalDate): String = d.format(kurz)

    fun hMin(min: Int): String = "%d:%02d".format(min / 60, min % 60)

    fun dezimal(min: Int): String = "%.2f".format(Locale.GERMANY, min / 60.0)

    fun km(km: Double): String =
        if (km % 1.0 == 0.0) "%.0f".format(km) else "%.1f".format(Locale.GERMANY, km)

    fun hms(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    }
}
