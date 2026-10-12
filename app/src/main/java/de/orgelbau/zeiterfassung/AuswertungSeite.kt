package de.orgelbau.zeiterfassung

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun AuswertungSeite(vm: AppViewModel) {
    val context = LocalContext.current
    val eintraege by vm.eintraege.collectAsStateWithLifecycle()
    val baustellen by vm.baustellen.collectAsStateWithLifecycle()

    val bId = vm.awBaustelleAktuell(eintraege)
    val heute = LocalDate.now()
    val bereich = Auswertung.bereich(vm.awZeitraum, heute, vm.awVon, vm.awBis)
    val erg = remember(eintraege, baustellen, bId, bereich, vm.awPerson) {
        Auswertung.auswerten(eintraege, baustellen, bId, bereich, vm.awPerson)
    }
    val personen = eintraege.map { it.person }.filter { it.isNotBlank() }.distinct().sorted()
    val bName = if (bId == null) "Alle Baustellen" else baustellen.find { it.id == bId }?.name
        ?: eintraege.firstOrNull { it.baustelleId == bId }?.baustelle ?: "Baustelle"

    SeitenRahmen("Auswertung") {
        AuswahlFeld(
            titel = "Baustelle",
            wert = bName,
            optionen = listOf<Pair<String?, String>>(null to "Alle Baustellen") +
                baustellen.sortedWith(compareBy({ it.archiviert }, { it.name }))
                    .map { it.id to (it.name + if (it.archiviert) " (ausgeblendet)" else "") },
            onWahl = vm::awBaustelleWaehlen,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AuswahlFeld(
                titel = "Zeitraum",
                wert = vm.awZeitraum.text,
                optionen = Zeitraum.entries.map { it to it.text },
                modifier = Modifier.weight(1f),
            ) { vm.awZeitraum = it }
            AuswahlFeld(
                titel = "Person",
                wert = vm.awPerson ?: "Alle",
                optionen = listOf<Pair<String?, String>>(null to "Alle Personen") + personen.map { it to it },
                modifier = Modifier.weight(1f),
            ) { vm.awPerson = it }
        }
        if (vm.awZeitraum == Zeitraum.EIGENER) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WertFeld("Von", vm.awVon?.let(Zeitrechnung::datumText) ?: "Anfang", Modifier.weight(1f)) {
                    datumWaehlen(context, vm.awVon ?: heute.withDayOfMonth(1)) { vm.awVon = it }
                }
                WertFeld("Bis", vm.awBis?.let(Zeitrechnung::datumText) ?: "heute", Modifier.weight(1f)) {
                    datumWaehlen(context, vm.awBis ?: heute) { vm.awBis = it }
                }
            }
        }

        Column {
            Text(bName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                bereich.text + (vm.awPerson?.let { " · $it" } ?: ""),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GrosserKnopf("PDF teilen", MaterialTheme.colorScheme.primary, enabled = erg.eintraege.isNotEmpty(), hoehe = 64) {
            val ort = baustellen.find { it.id == bId }?.ort
            val datei = PdfExport.erstellen(context, bName, ort, bereich.text, vm.awPerson, erg, mitBaustelle = bId == null)
            PdfExport.teilen(context, datei)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kachel("Arbeitszeit", "${Zeitrechnung.hMin(erg.gesamtMin)} h", "${Zeitrechnung.dezimal(erg.gesamtMin)} h dezimal", Modifier.weight(1f))
            Kachel("Arbeitstage", "${erg.tage}", null, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kachel("Kilometer", "${Zeitrechnung.km(erg.km)} km", null, Modifier.weight(1f), kmFehlt = erg.kmFehlt)
            Kachel("Personen", "${erg.personen.size}", null, Modifier.weight(1f))
        }

        if (erg.eintraege.isEmpty()) {
            Text("Keine Einträge in diesem Zeitraum.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SeitenRahmen
        }

        Abschnitt("Nach Person") {
            erg.personen.forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Zeile(p.name, "${Zeitrechnung.hMin(p.min)} h", "${p.tage} ${if (p.tage == 1) "Tag" else "Tage"} · ${Zeitrechnung.km(p.km)} km")
            }
        }
        Abschnitt("Nach Tätigkeit") {
            erg.taetigkeiten.forEachIndexed { i, (t, m) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                val anteil = if (erg.gesamtMin > 0) (m * 100.0 / erg.gesamtMin).roundToInt() else 0
                Zeile(t, "${Zeitrechnung.hMin(m)} h", "$anteil %")
            }
        }
        Abschnitt("Einzelnachweis") {
            erg.eintraege.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                EintragZeile(e, mitDatum = true, mitPerson = true) { vm.bearbeiten(e) }
            }
        }
    }
}

@Composable
private fun Abschnitt(titel: String, inhalt: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Beschriftung(titel)
        Karte { inhalt() }
    }
}

@Composable
private fun Kachel(titel: String, wert: String, unten: String?, modifier: Modifier, kmFehlt: Boolean = false) {
    Column(modifier) {
        Karte {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Beschriftung(titel)
                Text(wert, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (unten != null) Text(unten, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (kmFehlt) Schild("km fehlt", LocalZustand.current.stopp)
            }
        }
    }
}
