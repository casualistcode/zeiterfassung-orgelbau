package de.orgelbau.zeiterfassung

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Eintrag bearbeiten oder Zeit von Hand nachtragen. */
@Composable
fun EintragFormular(vm: AppViewModel, start: EintragEntwurf) {
    val context = LocalContext.current
    val baustellen by vm.baustellen.collectAsStateWithLifecycle()
    val taetigkeiten by vm.taetigkeiten.collectAsStateWithLifecycle()
    var e by remember(start) { mutableStateOf(start) }
    var pauseText by remember(start) { mutableStateOf(start.pauseMin.toString()) }
    var loeschenFragen by remember { mutableStateOf(false) }

    val baustelle = baustellen.find { it.id == e.baustelleId }
    val taetigkeit = taetigkeiten.find { it.id == e.taetigkeitId }
    val zeit = Zeitrechnung.ausFormular(e.datum, e.beginn, e.ende, e.pauseMin)
    val farben = LocalZustand.current

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                (if (e.id == null) "Zeit nachtragen" else "Eintrag bearbeiten").uppercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(e.person, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        WertFeld("Datum", Zeitrechnung.tagText(e.datum.toString())) {
            datumWaehlen(context, e.datum) { e = e.copy(datum = it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WertFeld("Beginn", Zeitrechnung.uhrzeit(e.beginn), Modifier.weight(1f)) {
                zeitWaehlen(context, e.beginn) { e = e.copy(beginn = it) }
            }
            WertFeld("Ende", Zeitrechnung.uhrzeit(e.ende), Modifier.weight(1f)) {
                zeitWaehlen(context, e.ende) { e = e.copy(ende = it) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Beschriftung("Pause in Minuten")
            OutlinedTextField(
                value = pauseText,
                onValueChange = { t ->
                    val ziffern = t.filter(Char::isDigit).take(3)
                    pauseText = ziffern
                    e = e.copy(pauseMin = ziffern.toIntOrNull() ?: 0)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.titleMedium,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Karte {
            Column(Modifier.padding(14.dp)) {
                Text("Arbeitszeit ${Zeitrechnung.hMin(zeit.nettoMin)} h", fontWeight = FontWeight.Bold)
                if (e.ende.isBefore(e.beginn)) {
                    Text("Ende am nächsten Tag", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        AuswahlFeld(
            titel = "Baustelle",
            wert = baustelle?.let { "${it.name} · ${it.ort}" },
            optionen = baustellen.filter { !it.archiviert || it.id == e.baustelleId }.sortedBy { it.name }.map { it.id to it.name },
        ) { e = e.copy(baustelleId = it) }
        AuswahlFeld(
            titel = "Tätigkeit",
            wert = taetigkeit?.name,
            optionen = taetigkeiten.filter { !it.archiviert || it.id == e.taetigkeitId }.sortedBy { it.reihenfolge }.map { it.id to it.name },
        ) { e = e.copy(taetigkeitId = it) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Beschriftung("Was wurde gemacht?")
            OutlinedTextField(
                value = e.notiz,
                onValueChange = { e = e.copy(notiz = it) },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GrosserKnopf("SPEICHERN", farben.laeuft, enabled = baustelle != null && taetigkeit != null, hoehe = 72) {
            vm.formularSpeichern(e)
        }
        OutlinedButton(
            onClick = vm::formularSchliessen,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        ) { Text("Abbrechen", style = MaterialTheme.typography.titleMedium) }
        if (e.id != null) {
            TextButton(
                onClick = { loeschenFragen = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            ) { Text("Eintrag löschen", color = farben.stopp, style = MaterialTheme.typography.titleMedium) }
        }
    }

    val id = e.id
    if (loeschenFragen && id != null) {
        AlertDialog(
            onDismissRequest = { loeschenFragen = false },
            title = { Text("Eintrag löschen?") },
            text = {
                Text(
                    "${Zeitrechnung.tagText(e.datum.toString())}, ${Zeitrechnung.uhrzeit(e.beginn)}–${Zeitrechnung.uhrzeit(e.ende)} Uhr" +
                        (baustelle?.let { ", ${it.name}" } ?: "") + ". Das lässt sich nicht rückgängig machen."
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.eintragLoeschen(id) },
                    colors = ButtonDefaults.buttonColors(containerColor = farben.stopp, contentColor = Color.White),
                ) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { loeschenFragen = false }) { Text("Abbrechen") } },
        )
    }
}
