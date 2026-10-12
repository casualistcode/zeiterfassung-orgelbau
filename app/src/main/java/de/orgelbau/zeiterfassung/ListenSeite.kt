package de.orgelbau.zeiterfassung

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Was gerade bearbeitet wird: eine bestehende Baustelle/Tätigkeit oder eine neue (wert == null). */
private sealed interface Bearbeitung {
    data class B(val wert: Baustelle?) : Bearbeitung
    data class T(val wert: Taetigkeit?) : Bearbeitung
}

@Composable
fun ListenSeite(vm: AppViewModel) {
    val baustellen by vm.baustellen.collectAsStateWithLifecycle()
    val taetigkeiten by vm.taetigkeiten.collectAsStateWithLifecycle()
    var bearbeitung by remember { mutableStateOf<Bearbeitung?>(null) }

    SeitenRahmen("Listen") {
        val (bAktiv, bAus) = baustellen.sortedBy { it.name }.partition { !it.archiviert }
        Liste("Baustellen", bAktiv) { b -> BaustellenZeile(b) { bearbeitung = Bearbeitung.B(b) } }
        HinzufuegenKnopf("Baustelle hinzufügen") { bearbeitung = Bearbeitung.B(null) }
        if (bAus.isNotEmpty()) {
            Liste("Ausgeblendete Baustellen", bAus) { b -> BaustellenZeile(b) { bearbeitung = Bearbeitung.B(b) } }
        }

        val (tAktiv, tAus) = taetigkeiten.sortedBy { it.reihenfolge }.partition { !it.archiviert }
        Liste("Tätigkeiten", tAktiv) { t -> Zeile(t.name, "", onClick = { bearbeitung = Bearbeitung.T(t) }) }
        HinzufuegenKnopf("Tätigkeit hinzufügen") { bearbeitung = Bearbeitung.T(null) }
        if (tAus.isNotEmpty()) {
            Liste("Ausgeblendete Tätigkeiten", tAus) { t -> Zeile(t.name, "", onClick = { bearbeitung = Bearbeitung.T(t) }) }
        }
    }

    when (val b = bearbeitung) {
        is Bearbeitung.B -> BaustelleDialog(
            b.wert,
            onSpeichern = { name, ort, km, archiviert ->
                vm.baustelleSpeichern(b.wert?.id, name, ort, km, archiviert)
                bearbeitung = null
            },
            onAbbrechen = { bearbeitung = null },
        )
        is Bearbeitung.T -> TaetigkeitDialog(
            b.wert,
            onSpeichern = { name, archiviert ->
                vm.taetigkeitSpeichern(b.wert?.id, name, archiviert)
                bearbeitung = null
            },
            onAbbrechen = { bearbeitung = null },
        )
        null -> {}
    }
}

@Composable
private fun <T> Liste(titel: String, eintraege: List<T>, zeile: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Beschriftung(titel)
        if (eintraege.isEmpty()) {
            Text("Keine Einträge.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Karte {
                eintraege.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    zeile(e)
                }
            }
        }
    }
}

@Composable
private fun BaustellenZeile(b: Baustelle, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(b.name, fontWeight = FontWeight.SemiBold)
            if (b.ort.isNotBlank()) Text(b.ort, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val km = b.kmEinfach
        if (km != null && km > 0) {
            Text("${Zeitrechnung.km(km)} km", fontWeight = FontWeight.Bold)
        } else {
            Schild("km fehlt", LocalZustand.current.stopp)
        }
    }
}

@Composable
private fun HinzufuegenKnopf(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
    ) { Text("+ $text", style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun BaustelleDialog(
    b: Baustelle?,
    onSpeichern: (name: String, ort: String, km: Double?, archiviert: Boolean) -> Unit,
    onAbbrechen: () -> Unit,
) {
    var name by remember { mutableStateOf(b?.name ?: "") }
    var ort by remember { mutableStateOf(b?.ort ?: "") }
    var km by remember { mutableStateOf(b?.kmEinfach?.takeIf { it > 0 }?.let(Zeitrechnung::km) ?: "") }
    val kmWert = km.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(if (b == null) "Neue Baustelle" else "Baustelle bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ort, { ort = it }, label = { Text("Ort") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    km,
                    { km = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text("Entfernung einfach (km)") },
                    placeholder = { Text("z. B. 15") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (b != null) AusblendenKnopf(b.archiviert) { onSpeichern(name.ifBlank { b.name }, ort, kmWert, !b.archiviert) }
            }
        },
        confirmButton = {
            Button(onClick = { onSpeichern(name, ort, kmWert, b?.archiviert ?: false) }, enabled = name.isNotBlank()) {
                Text("Speichern")
            }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

@Composable
private fun TaetigkeitDialog(t: Taetigkeit?, onSpeichern: (name: String, archiviert: Boolean) -> Unit, onAbbrechen: () -> Unit) {
    var name by remember { mutableStateOf(t?.name ?: "") }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(if (t == null) "Neue Tätigkeit" else "Tätigkeit bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Bezeichnung") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (t != null) AusblendenKnopf(t.archiviert) { onSpeichern(name.ifBlank { t.name }, !t.archiviert) }
            }
        },
        confirmButton = {
            Button(onClick = { onSpeichern(name, t?.archiviert ?: false) }, enabled = name.isNotBlank()) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

/** Entfernen heißt ausblenden: verschwindet aus der Auswahl, bleibt aber in der Auswertung. */
@Composable
private fun AusblendenKnopf(ausgeblendet: Boolean, onClick: () -> Unit) {
    Column {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(
                if (ausgeblendet) "Wieder einblenden" else "Entfernen (ausblenden)",
                color = if (ausgeblendet) LocalZustand.current.laeuft else LocalZustand.current.stopp,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (!ausgeblendet) {
            Text(
                "Erscheint dann nicht mehr in der Auswahl. Bisherige Stunden bleiben in der Auswertung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
