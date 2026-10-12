package de.orgelbau.zeiterfassung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AuswertungTest {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun eintrag(person: String, datum: String, baustelleId: String, min: Int, taetigkeit: String = "Einbau") =
        Eintrag(
            id = "$person$datum$min", person = person, personUid = person, baustelleId = baustelleId,
            baustelle = baustelleId, ort = "", kmEinfach = null, taetigkeitId = "", taetigkeit = taetigkeit,
            notiz = "", start = LocalDate.parse(datum).atStartOfDay(zone).toInstant().toEpochMilli() + min,
            ende = 0, pauseMin = 0, nettoMin = min, datum = datum, quelle = "", erstellt = 0,
        )

    private val baustellen = listOf(
        Baustelle("a", "Baustelle A", "Ort A", 20.0, false),
        Baustelle("b", "Baustelle B", "", null, false),
    )

    @Test
    fun kilometerEinmalProPersonTagUndBaustelle() {
        val es = listOf(
            eintrag("Simon", "2026-10-12", "a", 240),
            eintrag("Simon", "2026-10-12", "a", 180),
            eintrag("Simon", "2026-10-13", "a", 480),
            eintrag("Kollege", "2026-10-12", "a", 480),
        )
        val r = Auswertung.auswerten(es, baustellen, "a", Bereich(null, null, ""), null)
        assertEquals(3 * 40.0, r.km, 0.001)
        assertEquals(240 + 180 + 480 + 480, r.gesamtMin)
        assertEquals(2, r.tage)
        assertEquals(2, r.personen.size)
        assertFalse(r.kmFehlt)
    }

    @Test
    fun fehlendeKilometerWerdenGemeldet() {
        val r = Auswertung.auswerten(listOf(eintrag("Simon", "2026-10-12", "b", 60)), baustellen, null, Bereich(null, null, ""), null)
        assertTrue(r.kmFehlt)
    }

    @Test
    fun zeitraumUndPersonFiltern() {
        val es = listOf(
            eintrag("Simon", "2026-09-30", "a", 60),
            eintrag("Simon", "2026-10-01", "a", 120),
            eintrag("Kollege", "2026-10-02", "a", 480),
        )
        val okt = Auswertung.bereich(Zeitraum.MONAT, LocalDate.of(2026, 10, 12), null, null)
        val r = Auswertung.auswerten(es, baustellen, null, okt, "Simon")
        assertEquals(120, r.gesamtMin)
        assertEquals("Oktober 2026", okt.text)
    }

    @Test
    fun wocheBeginntMontags() {
        val w = Auswertung.bereich(Zeitraum.WOCHE, LocalDate.of(2026, 10, 15), null, null)
        assertEquals(LocalDate.of(2026, 10, 12), w.von)
        assertEquals(LocalDate.of(2026, 10, 18), w.bis)
    }

    @Test
    fun nachtragenUeberMitternacht() {
        val z = Zeitrechnung.ausFormular(LocalDate.of(2026, 10, 12), LocalTime.of(22, 0), LocalTime.of(1, 30), 15, zone)
        assertEquals(195, z.nettoMin)
        assertEquals("2026-10-13", Zeitrechnung.datum(z.ende, zone))
    }

    @Test
    fun nachtragenNormal() {
        val z = Zeitrechnung.ausFormular(LocalDate.of(2026, 10, 12), LocalTime.of(7, 0), LocalTime.of(16, 0), 30, zone)
        assertEquals(510, z.nettoMin)
    }
}
