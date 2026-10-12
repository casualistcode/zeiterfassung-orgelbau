package de.orgelbau.zeiterfassung

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Stundennachweis als PDF (A4) zum Verschicken per E-Mail oder WhatsApp. */
object PdfExport {
    private const val B = 595
    private const val H = 842
    private const val RAND = 40f
    private const val BREITE = B - 2 * RAND

    private val tinte = Color.rgb(0x16, 0x21, 0x1E)
    private val grauFarbe = Color.rgb(0x56, 0x66, 0x5F)
    private val messing = Color.rgb(0x8C, 0x62, 0x10)
    private val rot = Color.rgb(0xAD, 0x34, 0x27)

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = tinte }
    private val fett = Paint(text).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val grau = Paint(text).apply { color = grauFarbe }
    private val linie = Paint().apply { color = Color.rgb(0xC9, 0xD3, 0xCE); strokeWidth = 0.6f }
    private val flaeche = Paint().apply { color = Color.rgb(0xEE, 0xF2, 0xF0) }

    private class Spalte(val titel: String, val breite: Float, val rechts: Boolean = false, val umbruch: Boolean = false)

    fun erstellen(
        context: Context,
        baustelle: String,
        ort: String?,
        bereich: String,
        person: String?,
        erg: Ergebnis,
        mitBaustelle: Boolean,
    ): File {
        val doc = PdfDocument()
        var nr = 0
        lateinit var seite: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f
        val erstellt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))

        fun fusszeile() {
            c.drawLine(RAND, H - 36f, B - RAND, H - 36f, linie)
            c.drawText("Stundennachweis $baustelle · erstellt am $erstellt", RAND, H - 24f, grau)
            val s = "Seite $nr"
            c.drawText(s, B - RAND - grau.measureText(s), H - 24f, grau)
        }

        fun neueSeite() {
            if (nr > 0) {
                fusszeile()
                doc.finishPage(seite)
            }
            nr++
            seite = doc.startPage(PdfDocument.PageInfo.Builder(B, H, nr).create())
            c = seite.canvas
            y = RAND
        }

        fun platz(h: Float): Boolean {
            if (y + h > H - 50f) {
                neueSeite()
                return true
            }
            return false
        }

        fun tabelle(titel: String, spalten: List<Spalte>, zeilen: List<List<String>>, summe: List<String>? = null) {
            fun kopf() {
                var x = RAND
                spalten.forEach { s ->
                    val w = fett.measureText(s.titel)
                    c.drawText(s.titel, if (s.rechts) x + s.breite - w else x, y + 10f, fett)
                    x += s.breite
                }
                y += 15f
                c.drawLine(RAND, y, B - RAND, y, linie)
                y += 3f
            }
            platz(60f)
            c.drawText(titel, RAND, y + 12f, Paint(fett).apply { textSize = 12f; color = messing })
            y += 22f
            kopf()
            (zeilen + listOfNotNull(summe)).forEachIndexed { i, z ->
                val istSumme = summe != null && i == zeilen.size
                val p = if (istSumme) fett else text
                val teile = spalten.mapIndexed { j, s ->
                    if (s.umbruch) umbrechen(z[j], s.breite - 6f, p) else listOf(kuerzen(z[j], s.breite - 6f, p))
                }
                val hoehe = teile.maxOf { it.size } * 11f + 4f
                if (platz(hoehe)) kopf()
                if (istSumme) c.drawLine(RAND, y, B - RAND, y, linie)
                var x = RAND
                spalten.forEachIndexed { j, s ->
                    teile[j].forEachIndexed { k, t ->
                        val w = p.measureText(t)
                        c.drawText(t, if (s.rechts) x + s.breite - w else x, y + 10f + k * 11f, p)
                    }
                    x += s.breite
                }
                y += hoehe
            }
            y += 14f
        }

        neueSeite()

        // Kopf
        c.drawText("STUNDENNACHWEIS", RAND, y + 10f, Paint(fett).apply { textSize = 9f; color = messing; letterSpacing = 0.15f })
        y += 30f
        c.drawText(baustelle, RAND, y, Paint(fett).apply { textSize = 20f })
        y += 16f
        listOfNotNull(
            ort?.takeIf { it.isNotBlank() },
            "Zeitraum: $bereich",
            person?.let { "Person: $it" },
        ).forEach {
            c.drawText(it, RAND, y, Paint(grau).apply { textSize = 10f })
            y += 14f
        }
        y += 8f

        // Übersicht in vier Feldern
        val feldB = (BREITE - 3 * 8f) / 4
        listOf(
            "Arbeitszeit" to "${Zeitrechnung.hMin(erg.gesamtMin)} h",
            "Dezimal" to "${Zeitrechnung.dezimal(erg.gesamtMin)} h",
            "Arbeitstage" to "${erg.tage}",
            "Kilometer" to "${Zeitrechnung.km(erg.km)} km",
        ).forEachIndexed { i, (t, w) ->
            val x = RAND + i * (feldB + 8f)
            c.drawRoundRect(x, y, x + feldB, y + 46f, 8f, 8f, flaeche)
            c.drawText(t, x + 10f, y + 16f, grau)
            c.drawText(w, x + 10f, y + 36f, Paint(fett).apply { textSize = 15f })
        }
        y += 54f
        if (erg.kmFehlt) {
            c.drawText("Hinweis: Bei mindestens einer Baustelle fehlt die Entfernung (km fehlt).", RAND, y + 8f, Paint(text).apply { color = rot })
            y += 16f
        }
        y += 12f

        if (erg.personen.size > 1 || person == null) {
            tabelle(
                "Nach Person",
                listOf(Spalte("Person", BREITE - 210f), Spalte("Tage", 60f, true), Spalte("km", 70f, true), Spalte("Stunden", 80f, true)),
                erg.personen.map { listOf(it.name, "${it.tage}", Zeitrechnung.km(it.km), Zeitrechnung.hMin(it.min)) },
            )
        }
        tabelle(
            "Nach Tätigkeit",
            listOf(Spalte("Tätigkeit", BREITE - 150f), Spalte("Anteil", 70f, true), Spalte("Stunden", 80f, true)),
            erg.taetigkeiten.map { (t, m) ->
                val anteil = if (erg.gesamtMin > 0) (m * 100.0 / erg.gesamtMin).roundToInt() else 0
                listOf(t, "$anteil %", Zeitrechnung.hMin(m))
            },
        )
        tabelle(
            "Einzelnachweis",
            listOf(
                Spalte("Datum", 64f),
                Spalte("Person", 78f),
                Spalte("Bezeichnung der Arbeit", BREITE - 64f - 78f - 66f - 44f - 48f, umbruch = true),
                Spalte("Von–Bis", 66f),
                Spalte("Pause", 44f, true),
                Spalte("Std.", 48f, true),
            ),
            erg.eintraege.map { e ->
                val arbeit = listOfNotNull(
                    e.baustelle.takeIf { mitBaustelle },
                    e.taetigkeit.ifBlank { null },
                    e.notiz.ifBlank { null },
                ).joinToString(" · ")
                listOf(
                    Zeitrechnung.tagText(e.datum),
                    e.person,
                    arbeit,
                    "${Zeitrechnung.uhrzeit(e.start)}–${Zeitrechnung.uhrzeit(e.ende)}",
                    if (e.pauseMin > 0) "${e.pauseMin} min" else "–",
                    Zeitrechnung.hMin(e.nettoMin),
                )
            },
            summe = listOf("Summe", "", "", "", "", Zeitrechnung.hMin(erg.gesamtMin)),
        )

        fusszeile()
        doc.finishPage(seite)

        val ordner = File(context.cacheDir, "nachweise").apply { mkdirs() }
        val name = "Stundennachweis $baustelle $bereich"
            .replace(Regex("[^A-Za-z0-9ÄÖÜäöüß.\\- ]"), "")
            .trim().replace(Regex("\\s+"), "_")
        val datei = File(ordner, "$name.pdf")
        datei.outputStream().use { doc.writeTo(it) }
        doc.close()
        return datei
    }

    fun teilen(context: Context, datei: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.dateien", datei)
        val senden = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, datei.nameWithoutExtension.replace('_', ' '))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(senden, "Nachweis teilen"))
    }

    private fun umbrechen(s: String, w: Float, p: Paint): List<String> {
        val zeilen = mutableListOf<String>()
        var akt = ""
        for (wort in s.split(" ")) {
            val probe = if (akt.isEmpty()) wort else "$akt $wort"
            if (p.measureText(probe) <= w || akt.isEmpty()) {
                akt = probe
            } else {
                zeilen += akt
                akt = wort
            }
        }
        if (akt.isNotEmpty()) zeilen += akt
        return zeilen.ifEmpty { listOf("") }.map { kuerzen(it, w, p) }
    }

    private fun kuerzen(s: String, w: Float, p: Paint): String {
        if (p.measureText(s) <= w) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > w) t = t.dropLast(1)
        return "$t…"
    }
}
