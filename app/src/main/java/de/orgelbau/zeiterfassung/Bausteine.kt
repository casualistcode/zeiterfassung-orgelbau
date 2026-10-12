package de.orgelbau.zeiterfassung

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun GrosserKnopf(
    text: String,
    farbe: Color,
    enabled: Boolean = true,
    hoehe: Int = 96,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = farbe, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(hoehe.dp),
    ) {
        Text(text, fontSize = if (hoehe >= 90) 28.sp else 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}

@Composable
fun Beschriftung(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun Karte(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

/** Kleines farbiges Schild, z. B. „km fehlt“ oder „Läuft“. */
@Composable
fun Schild(text: String, farbe: Color) {
    Surface(color = farbe, shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Großes antippbares Feld mit Beschriftung darüber. */
@Composable
fun WertFeld(titel: String, wert: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Beschriftung(titel)
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) {
            Text(wert, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("▾", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Auswahl aus einer Liste; öffnet ein Fenster mit großen Einträgen. */
@Composable
fun <T> AuswahlFeld(
    titel: String,
    wert: String?,
    optionen: List<Pair<T, String>>,
    modifier: Modifier = Modifier,
    onWahl: (T) -> Unit,
) {
    var offen by remember { mutableStateOf(false) }
    WertFeld(titel, wert ?: "Bitte wählen", modifier, enabled = optionen.isNotEmpty()) { offen = true }
    if (offen) {
        AlertDialog(
            onDismissRequest = { offen = false },
            title = { Text(titel) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    optionen.forEach { (id, name) ->
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onWahl(id); offen = false }
                                .padding(vertical = 16.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { offen = false }) { Text("Abbrechen") } },
        )
    }
}

/** Zeile mit Text links und Wert rechts, z. B. in Tabellen der Auswertung. */
@Composable
fun Zeile(links: String, rechts: String, unten: String? = null, fett: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(links, fontWeight = if (fett) FontWeight.Bold else FontWeight.SemiBold)
            if (!unten.isNullOrBlank()) Text(unten, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(rechts, fontWeight = FontWeight.Bold)
    }
}

fun datumWaehlen(context: Context, start: LocalDate, onWahl: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, j, m, t -> onWahl(LocalDate.of(j, m + 1, t)) },
        start.year, start.monthValue - 1, start.dayOfMonth,
    ).show()
}

fun zeitWaehlen(context: Context, start: LocalTime, onWahl: (LocalTime) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onWahl(LocalTime.of(h, m)) }, start.hour, start.minute, true).show()
}
