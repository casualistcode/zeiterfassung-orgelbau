package de.orgelbau.zeiterfassung

import org.junit.Assert.assertEquals
import org.junit.Test

class ZeitrechnungTest {
    private val min = 60_000L

    @Test
    fun ohnePause() {
        val a = Zeitrechnung.abschluss(0, emptyList(), 90 * min)
        assertEquals(0, a.pauseMin)
        assertEquals(90, a.nettoMin)
    }

    @Test
    fun mehrerePausenWerdenZusammengezaehlt() {
        val p = listOf(Pause(10 * min, 20 * min), Pause(30 * min, 35 * min))
        val a = Zeitrechnung.abschluss(0, p, 60 * min)
        assertEquals(15, a.pauseMin)
        assertEquals(45, a.nettoMin)
    }

    @Test
    fun stoppInPauseEndetMitPausenbeginn() {
        val p = listOf(Pause(10 * min, 20 * min), Pause(50 * min, null))
        val a = Zeitrechnung.abschluss(0, p, 80 * min)
        assertEquals(50 * min, a.ende)
        assertEquals(10, a.pauseMin)
        assertEquals(40, a.nettoMin)
    }

    @Test
    fun laufendeZeitStehtInDerPause() {
        val p = listOf(Pause(10 * min, null))
        assertEquals(10 * min, Zeitrechnung.arbeitMs(0, p, 30 * min))
    }

    @Test
    fun minutengenauOhneHalbeStunden() {
        val a = Zeitrechnung.abschluss(0, emptyList(), 7 * 60 * min + 13 * min)
        assertEquals("7:13", Zeitrechnung.hMin(a.nettoMin))
    }
}
