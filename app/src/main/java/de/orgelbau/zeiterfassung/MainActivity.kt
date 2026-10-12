package de.orgelbau.zeiterfassung

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        stoppPruefen(intent)
        setContent {
            ZeitTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        stoppPruefen(intent)
    }

    /** Das Widget öffnet die App mit diesem Zusatz, damit gleich „Was wurde gemacht?“ kommt. */
    private fun stoppPruefen(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_STOPP, false) == true) {
            i.removeExtra(EXTRA_STOPP)
            vm.seite = Seite.STEMPELN
            vm.stoppAngefragt = true
        }
    }

    companion object {
        const val EXTRA_STOPP = "stopp"
    }
}

@Composable
fun App(vm: AppViewModel) {
    val formular = vm.formular
    when {
        vm.person == null -> Box(Modifier.safeDrawingPadding()) { NamensSeite(vm::personSetzen) }
        vm.keinInternet -> Box(Modifier.safeDrawingPadding()) { KeinInternetSeite(vm::anmelden) }
        formular != null -> {
            BackHandler(onBack = vm::formularSchliessen)
            Box(Modifier.safeDrawingPadding()) { EintragFormular(vm, formular) }
        }
        else -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { Navigation(vm.seite) { vm.seite = it } },
        ) { innen ->
            Box(Modifier.padding(innen).consumeWindowInsets(innen)) {
                when (vm.seite) {
                    Seite.STEMPELN -> StempelSeite(vm)
                    Seite.EINTRAEGE -> EintraegeSeite(vm)
                    Seite.AUSWERTUNG -> AuswertungSeite(vm)
                    Seite.LISTEN -> ListenSeite(vm)
                }
            }
        }
    }
}

@Composable
fun Navigation(aktiv: Seite, onWahl: (Seite) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Seite.entries.forEach { s ->
            NavigationBarItem(
                selected = s == aktiv,
                onClick = { onWahl(s) },
                icon = {
                    Icon(
                        when (s) {
                            Seite.STEMPELN -> Icons.Filled.PlayArrow
                            Seite.EINTRAEGE -> Icons.Filled.List
                            Seite.AUSWERTUNG -> Icons.Filled.DateRange
                            Seite.LISTEN -> Icons.Filled.Build
                        },
                        contentDescription = null,
                    )
                },
                label = { Text(s.titel, fontWeight = if (s == aktiv) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = LocalZustand.current.messingHell,
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

/** Gemeinsamer Rahmen der Seiten: große Überschrift und scrollbarer Inhalt. */
@Composable
fun SeitenRahmen(titel: String, unterzeile: String? = null, inhalt: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(titel, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (unterzeile != null) Text(unterzeile, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        inhalt()
    }
}

@Composable
fun NamensSeite(onFertig: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        OrgelPfeifen(Modifier.size(56.dp, 48.dp), MaterialTheme.colorScheme.primary)
        Text("Willkommen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Wie heißt du? Der Name steht später auf den Stundennachweisen.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Vor- und Nachname") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        GrosserKnopf("Weiter", LocalZustand.current.laeuft, enabled = name.isNotBlank()) { onFertig(name) }
    }
}

@Composable
fun KeinInternetSeite(onNochmal: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Keine Verbindung", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Beim allerersten Start braucht die App einmal Internet. Danach funktioniert das Stempeln auch ohne Netz.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GrosserKnopf("Nochmal versuchen", MaterialTheme.colorScheme.primary, onClick = onNochmal)
    }
}

private val tagFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

@Composable
fun StempelSeite(vm: AppViewModel) {
    val lauf by vm.lauf.collectAsStateWithLifecycle()
    val baustellen by vm.baustellen.collectAsStateWithLifecycle()
    val taetigkeiten by vm.taetigkeiten.collectAsStateWithLifecycle()
    val eintraege by vm.eintraege.collectAsStateWithLifecycle()

    var jetzt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            jetzt = System.currentTimeMillis()
            delay(1000)
        }
    }
    var stoppZeitpunkt by rememberSaveable { mutableStateOf<Long?>(null) }
    val aktuell = lauf
    LaunchedEffect(vm.stoppAngefragt, aktuell) {
        if (vm.stoppAngefragt && aktuell != null) {
            if (aktuell.wert != null) stoppZeitpunkt = System.currentTimeMillis()
            vm.stoppAngefragt = false
        }
    }
    val heuteDatum = Zeitrechnung.datum(jetzt)
    val heute = eintraege.filter { it.datum == heuteDatum && vm.istMeiner(it) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Kopf(vm.person ?: "")

        val l = aktuell?.wert
        when {
            aktuell == null -> Text("Wird geladen …", color = MaterialTheme.colorScheme.onSurfaceVariant)
            l == null -> StartBereich(vm, baustellen, taetigkeiten)
            else -> LaufBereich(
                lauf = l,
                baustelle = baustellen.find { it.id == l.baustelleId }?.name ?: "",
                taetigkeit = taetigkeiten.find { it.id == l.taetigkeitId }?.name ?: "",
                jetzt = jetzt,
                onPauseWeiter = vm::pauseUmschalten,
                onStopp = { stoppZeitpunkt = System.currentTimeMillis() },
            )
        }
        HeuteListe(heute, vm::bearbeiten)
    }

    val zp = stoppZeitpunkt
    val l = aktuell?.wert
    if (zp != null && l != null) {
        StoppDialog(
            lauf = l,
            zeitpunkt = zp,
            onSpeichern = { notiz ->
                vm.stoppen(notiz, zp)
                stoppZeitpunkt = null
            },
            onZurueck = { stoppZeitpunkt = null },
        )
    }
}

@Composable
private fun Kopf(person: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OrgelPfeifen(Modifier.size(30.dp, 28.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Zeiterfassung", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(LocalDate.now().format(tagFormat), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val kuerzel = person.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(LocalZustand.current.messingHell),
            contentAlignment = Alignment.Center,
        ) { Text(kuerzel, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun StartBereich(vm: AppViewModel, baustellen: List<Baustelle>, taetigkeiten: List<Taetigkeit>) {
    val farben = LocalZustand.current
    if (baustellen.none { !it.archiviert } || taetigkeiten.none { !it.archiviert }) {
        Karte {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Noch keine Baustellen oder Tätigkeiten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Lege sie unter „Listen“ an. Beim allerersten Start bitte kurz mit Internet verbinden, damit vorhandene Listen geladen werden.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GrosserKnopf("Zu den Listen", MaterialTheme.colorScheme.primary, hoehe = 60) { vm.seite = Seite.LISTEN }
            }
        }
        return
    }
    val baustelle = baustellen.find { it.id == vm.baustelleId && !it.archiviert }
    val taetigkeit = taetigkeiten.find { it.id == vm.taetigkeitId && !it.archiviert }
    Karte {
        AuswahlZeile(
            titel = "Baustelle",
            wert = baustelle?.let { if (it.ort.isBlank()) it.name else "${it.name} · ${it.ort}" },
            optionen = baustellen.filter { !it.archiviert }.sortedBy { it.name }.map { it.id to it.name },
            onWahl = vm::baustelleWaehlen,
        )
        HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
        AuswahlZeile(
            titel = "Tätigkeit",
            wert = taetigkeit?.name,
            optionen = taetigkeiten.filter { !it.archiviert }.sortedBy { it.reihenfolge }.map { it.id to it.name },
            onWahl = vm::taetigkeitWaehlen,
        )
    }
    val bereit = baustelle != null && taetigkeit != null
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        // Großer runder Start-Knopf mit hellem Hof
        Box(
            Modifier.size(232.dp).clip(CircleShape)
                .background(if (bereit) farben.laeuft.copy(alpha = 0.14f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(196.dp).clip(CircleShape)
                    .background(if (bereit) farben.laeuft else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = bereit, onClick = vm::starten),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val farbe = if (bereit) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = farbe, modifier = Modifier.size(64.dp))
                    Text("Start", color = farbe, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (!bereit) {
        Text(
            "Bitte zuerst Baustelle und Tätigkeit wählen.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun LaufBereich(
    lauf: Laufend,
    baustelle: String,
    taetigkeit: String,
    jetzt: Long,
    onPauseWeiter: () -> Unit,
    onStopp: () -> Unit,
) {
    val farben = LocalZustand.current
    val farbe = if (lauf.inPause) farben.pause else farben.laeuft
    val arbeit = Zeitrechnung.arbeitMs(lauf.start, lauf.pausen, jetzt)
    Karte {
        Box(Modifier.fillMaxWidth()) {
            // dezenter Pfeifenprospekt im Hintergrund
            OrgelPfeifen(
                Modifier.align(Alignment.BottomEnd).padding(end = 20.dp).size(110.dp, 90.dp),
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                anzahl = 7,
            )
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ZeitRing(arbeit / ACHT_STUNDEN, farbe, Modifier.size(232.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Schild(if (lauf.inPause) "Pause" else "Läuft", farbe)
                        Spacer(Modifier.height(6.dp))
                        val s = arbeit / 1000
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("%d:%02d".format(s / 3600, s / 60 % 60), fontSize = 56.sp, fontWeight = FontWeight.Bold)
                            Text(
                                ":%02d".format(s % 60),
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp, start = 2.dp),
                            )
                        }
                        Text("Std. gearbeitet", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(baustelle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(taetigkeit, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Beginn ${Zeitrechnung.uhrzeit(lauf.start)} Uhr · Pause ${
                        Zeitrechnung.minuten(Zeitrechnung.pauseMs(lauf.pausen, jetzt))
                    } min",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GrosserKnopf(
            if (lauf.inPause) "Weiter" else "Pause",
            if (lauf.inPause) farben.laeuft else farben.pause,
            Modifier.weight(1f),
            hoehe = 96,
            onClick = onPauseWeiter,
        )
        GrosserKnopf("Stopp", farben.stopp, Modifier.weight(1f), hoehe = 96, onClick = onStopp)
    }
}

private const val ACHT_STUNDEN = 8 * 3600_000f

/** Fortschrittsring; ein voller Ring entspricht 8 Stunden. */
@Composable
private fun ZeitRing(anteil: Float, farbe: Color, modifier: Modifier, inhalt: @Composable () -> Unit) {
    val spur = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = 14.dp.toPx()
            val r = Size(size.width - s, size.height - s)
            drawArc(spur, 0f, 360f, false, Offset(s / 2, s / 2), r, style = Stroke(s))
            drawArc(farbe, -90f, 360f * anteil.coerceIn(0f, 1f), false, Offset(s / 2, s / 2), r, style = Stroke(s, cap = StrokeCap.Round))
        }
        inhalt()
    }
}

@Composable
fun StoppDialog(lauf: Laufend, zeitpunkt: Long, onSpeichern: (String) -> Unit, onZurueck: () -> Unit) {
    val a = Zeitrechnung.abschluss(lauf.start, lauf.pausen, zeitpunkt)
    var notiz by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onZurueck,
        title = { Text("Was wurde gemacht?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${Zeitrechnung.uhrzeit(lauf.start)} – ${Zeitrechnung.uhrzeit(a.ende)} Uhr · Pause ${a.pauseMin} min"
                )
                Text("Arbeitszeit ${Zeitrechnung.hMin(a.nettoMin)} h", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = notiz,
                    onValueChange = { notiz = it },
                    placeholder = { Text("z. B. Pfeifen Register 3 gereinigt") },
                    minLines = 3,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSpeichern(notiz) },
                colors = ButtonDefaults.buttonColors(containerColor = LocalZustand.current.stopp, contentColor = Color.White),
                modifier = Modifier.heightIn(min = 52.dp),
            ) { Text("Speichern", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onZurueck, modifier = Modifier.heightIn(min = 52.dp)) { Text("Zurück") }
        },
    )
}

@Composable
fun HeuteListe(heute: List<Eintrag>, onBearbeiten: (Eintrag) -> Unit) {
    if (heute.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Beschriftung("Heute", Modifier.weight(1f))
            Text("${Zeitrechnung.hMin(heute.sumOf { it.nettoMin })} h", fontWeight = FontWeight.Bold)
        }
        Karte {
            heute.sortedBy { it.start }.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
                EintragZeile(e, mitDatum = false) { onBearbeiten(e) }
            }
        }
    }
}

/** Ein Eintrag als antippbare Zeile (Stempeln, Einträge, Auswertung). */
@Composable
fun EintragZeile(e: Eintrag, mitDatum: Boolean, mitPerson: Boolean = false, onClick: () -> Unit) {
    val zeit = "${Zeitrechnung.uhrzeit(e.start)}–${Zeitrechnung.uhrzeit(e.ende)}"
    val kopf = buildString {
        if (mitDatum) append(Zeitrechnung.tagText(e.datum)).append(" · ")
        append(zeit).append(" · ").append(e.baustelle)
    }
    val unten = listOfNotNull(
        e.person.takeIf { mitPerson && it.isNotBlank() },
        e.taetigkeit.ifBlank { null },
        e.notiz.ifBlank { null },
        if (e.pauseMin > 0) "Pause ${e.pauseMin} min" else null,
    ).joinToString(" · ")
    Zeile(kopf, "${Zeitrechnung.hMin(e.nettoMin)} h", unten, onClick = onClick)
}
