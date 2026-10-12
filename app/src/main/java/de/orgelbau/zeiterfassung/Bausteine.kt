package de.orgelbau.zeiterfassung

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun GrosserKnopf(
    text: String,
    farbe: Color,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    hoehe: Int = 88,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = farbe, contentColor = Color.White),
        modifier = modifier.height(hoehe.dp),
    ) {
        Text(text, fontSize = if (hoehe >= 80) 24.sp else 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
fun Beschriftung(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun Karte(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

/** Kleines farbiges Schild, z. B. „km fehlt“ oder „Läuft“. */
@Composable
fun Schild(text: String, farbe: Color, textFarbe: Color = Color.White) {
    Surface(color = farbe, shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = textFarbe,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/**
 * Dezenter Hinweis auf den Orgelbau: ein kleiner Pfeifenprospekt
 * (Labialpfeifen mit Körper, Mund und kegelförmigem Fuß, Mitte am höchsten).
 */
@Composable
fun OrgelPfeifen(modifier: Modifier, farbe: Color, mund: Color = Color.Transparent, anzahl: Int = 5) {
    Canvas(modifier) {
        val mitte = (anzahl - 1) / 2f
        val spalt = size.width * 0.05f
        val breite = (size.width - spalt * (anzahl - 1)) / anzahl
        val fuss = size.height * 0.18f
        val koerperUnten = size.height - fuss
        for (i in 0 until anzahl) {
            val abstand = kotlin.math.abs(i - mitte) / maxOf(mitte, 1f)
            val hoehe = 1f - 0.45f * abstand
            val x = i * (breite + spalt)
            val oben = size.height * (1f - hoehe)
            drawRoundRect(farbe, Offset(x, oben), Size(breite, koerperUnten - oben), CornerRadius(breite * 0.18f))
            val p = Path().apply {
                moveTo(x, koerperUnten)
                lineTo(x + breite, koerperUnten)
                lineTo(x + breite * 0.64f, size.height)
                lineTo(x + breite * 0.36f, size.height)
                close()
            }
            drawPath(p, farbe)
            if (mund != Color.Transparent) {
                drawRect(mund, Offset(x + breite * 0.22f, koerperUnten - breite * 0.6f), Size(breite * 0.56f, breite * 0.2f))
            }
        }
    }
}

/** Großes antippbares Feld: Beschriftung und Wert in einer Fläche. */
@Composable
fun WertFeld(titel: String, wert: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        WertInhalt(titel, wert)
    }
}

@Composable
private fun WertInhalt(titel: String, wert: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(wert, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Auswahl aus einer Liste als eigene Fläche. */
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
    if (offen) AuswahlDialog(titel, optionen, { offen = false }, onWahl)
}

/** Auswahl aus einer Liste als Zeile innerhalb einer Karte. */
@Composable
fun <T> AuswahlZeile(titel: String, wert: String?, optionen: List<Pair<T, String>>, onWahl: (T) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(enabled = optionen.isNotEmpty()) { offen = true }) {
        WertInhalt(titel, wert ?: "Bitte wählen")
    }
    if (offen) AuswahlDialog(titel, optionen, { offen = false }, onWahl)
}

@Composable
private fun <T> AuswahlDialog(titel: String, optionen: List<Pair<T, String>>, onZu: () -> Unit, onWahl: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onZu,
        title = { Text(titel) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                optionen.forEach { (id, name) ->
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onWahl(id); onZu() }
                            .padding(vertical = 16.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onZu) { Text("Abbrechen") } },
    )
}

/** Zeile mit Text links und Wert rechts, z. B. in Listen und Tabellen. */
@Composable
fun Zeile(links: String, rechts: String, unten: String? = null, fett: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(links, fontWeight = if (fett) FontWeight.Bold else FontWeight.SemiBold)
            if (!unten.isNullOrBlank()) {
                Text(unten, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (rechts.isNotEmpty()) Text(rechts, fontWeight = FontWeight.Bold)
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
