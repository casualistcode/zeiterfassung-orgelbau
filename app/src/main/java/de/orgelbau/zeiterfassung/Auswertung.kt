package de.orgelbau.zeiterfassung

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

enum class Zeitraum(val text: String) {
    GESAMT("Gesamt"),
    WOCHE("Diese Woche"),
    MONAT("Dieser Monat"),
    VORMONAT("Letzter Monat"),
    EIGENER("Eigener Zeitraum"),
}

/** Datumsbereich; null heißt offen (kein Anfang bzw. kein Ende). */
data class Bereich(val von: LocalDate?, val bis: LocalDate?, val text: String)

data class PersonSumme(val name: String, val min: Int, val tage: Int, val km: Double)

data class Ergebnis(
    val eintraege: List<Eintrag>,
    val gesamtMin: Int,
    val tage: Int,
    val km: Double,
    val kmFehlt: Boolean,
    val personen: List<PersonSumme>,
    val taetigkeiten: List<Pair<String, Int>>,
)

object Auswertung {
    private val monate = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    )

    fun bereich(z: Zeitraum, heute: LocalDate, eigenVon: LocalDate?, eigenBis: LocalDate?): Bereich = when (z) {
        Zeitraum.GESAMT -> Bereich(null, null, "Gesamter Zeitraum")
        Zeitraum.WOCHE -> {
            val mo = heute.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val so = mo.plusDays(6)
            Bereich(mo, so, "KW ${heute.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} (${Zeitrechnung.datumText(mo)} – ${Zeitrechnung.datumText(so)})")
        }
        Zeitraum.MONAT -> {
            val a = heute.withDayOfMonth(1)
            Bereich(a, a.plusMonths(1).minusDays(1), "${monate[a.monthValue - 1]} ${a.year}")
        }
        Zeitraum.VORMONAT -> {
            val a = heute.withDayOfMonth(1).minusMonths(1)
            Bereich(a, a.plusMonths(1).minusDays(1), "${monate[a.monthValue - 1]} ${a.year}")
        }
        Zeitraum.EIGENER -> Bereich(
            eigenVon, eigenBis,
            "${eigenVon?.let(Zeitrechnung::datumText) ?: "Anfang"} – ${eigenBis?.let(Zeitrechnung::datumText) ?: "heute"}",
        )
    }

    /**
     * Kilometer: pro Person, Tag und Baustelle einmal Hin- und Rückfahrt
     * (2 × einfache Entfernung). Die aktuelle Entfernung der Baustelle hat
     * Vorrang vor dem im Eintrag gespeicherten Wert.
     */
    fun auswerten(
        alle: List<Eintrag>,
        baustellen: List<Baustelle>,
        baustelleId: String?,
        bereich: Bereich,
        person: String?,
    ): Ergebnis {
        val vonText = bereich.von?.toString()
        val bisText = bereich.bis?.toString()
        val es = alle.filter { e ->
            (baustelleId == null || e.baustelleId == baustelleId) &&
                (vonText == null || e.datum >= vonText) &&
                (bisText == null || e.datum <= bisText) &&
                (person == null || e.person == person)
        }.sortedBy { it.start }

        val kmJe = baustellen.associate { it.id to it.kmEinfach }
        val gezaehlt = HashSet<String>()
        var kmFehlt = false
        val minJe = LinkedHashMap<String, Int>()
        val tageJe = HashMap<String, MutableSet<String>>()
        val kmJePerson = HashMap<String, Double>()
        val taet = HashMap<String, Int>()

        for (e in es) {
            minJe[e.person] = (minJe[e.person] ?: 0) + e.nettoMin
            tageJe.getOrPut(e.person) { mutableSetOf() }.add(e.datum)
            val t = e.taetigkeit.ifBlank { "Ohne Tätigkeit" }
            taet[t] = (taet[t] ?: 0) + e.nettoMin
            if (gezaehlt.add("${e.person}|${e.datum}|${e.baustelleId}")) {
                val km = kmJe[e.baustelleId]?.takeIf { it > 0 } ?: e.kmEinfach?.takeIf { it > 0 }
                if (km == null) kmFehlt = true
                kmJePerson[e.person] = (kmJePerson[e.person] ?: 0.0) + 2 * (km ?: 0.0)
            }
        }

        return Ergebnis(
            eintraege = es,
            gesamtMin = es.sumOf { it.nettoMin },
            tage = es.map { it.datum }.toSet().size,
            km = kmJePerson.values.sum(),
            kmFehlt = kmFehlt,
            personen = minJe.map { (p, m) -> PersonSumme(p, m, tageJe[p]?.size ?: 0, kmJePerson[p] ?: 0.0) }
                .sortedByDescending { it.min },
            taetigkeiten = taet.toList().sortedByDescending { it.second },
        )
    }
}
