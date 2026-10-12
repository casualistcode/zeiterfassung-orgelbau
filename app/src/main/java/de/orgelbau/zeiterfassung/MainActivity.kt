package de.orgelbau.zeiterfassung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZeitTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
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
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
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
                label = { Text(s.titel) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

/** Gemeinsamer Rahmen der Seiten: Überschrift und scrollbarer Inhalt. */
@Composable
fun SeitenRahmen(titel: String, unterzeile: String? = null, inhalt: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(titel.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
            modifier = Modifier.fillMaxWidth(),
        )
        GrosserKnopf("WEITER", LocalZustand.current.laeuft, enabled = name.isNotBlank()) { onFertig(name) }
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
        GrosserKnopf("NOCHMAL VERSUCHEN", MaterialTheme.colorScheme.primary, onClick = onNochmal)
    }
}

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
    val heuteDatum = Zeitrechnung.datum(jetzt)
    val heute = eintraege.filter { it.datum == heuteDatum && vm.istMeiner(it) }

    SeitenRahmen("Zeiterfassung", vm.person) {
        val l = lauf?.wert
        when {
            lauf == null -> Text("Wird geladen …", color = MaterialTheme.colorScheme.onSurfaceVariant)
            l == null -> StartBereich(vm, baustellen, taetigkeiten)
            else -> LaufBereich(
                lauf = l,
                baustelle = baustellen.find { it.id == l.baustelleId }?.name ?: "",
                taetigkeit = taetigkeiten.find { it.id == l.taetigkeitId }?.name ?: "",
                jetzt = jetzt,
                onPause = vm::pause,
                onWeiter = vm::weiter,
                onStopp = { stoppZeitpunkt = System.currentTimeMillis() },
            )
        }
        HeuteListe(heute, vm::bearbeiten)
    }

    val zp = stoppZeitpunkt
    val l = lauf?.wert
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
fun StartBereich(vm: AppViewModel, baustellen: List<Baustelle>, taetigkeiten: List<Taetigkeit>) {
    val baustelle = baustellen.find { it.id == vm.baustelleId && !it.archiviert }
    val taetigkeit = taetigkeiten.find { it.id == vm.taetigkeitId && !it.archiviert }
    AuswahlFeld(
        titel = "Baustelle",
        wert = baustelle?.let { "${it.name} · ${it.ort}" },
        optionen = baustellen.filter { !it.archiviert }.sortedBy { it.name }.map { it.id to it.name },
        onWahl = vm::baustelleWaehlen,
    )
    AuswahlFeld(
        titel = "Tätigkeit",
        wert = taetigkeit?.name,
        optionen = taetigkeiten.filter { !it.archiviert }.sortedBy { it.reihenfolge }.map { it.id to it.name },
        onWahl = vm::taetigkeitWaehlen,
    )
    GrosserKnopf(
        "START",
        LocalZustand.current.laeuft,
        enabled = baustelle != null && taetigkeit != null,
        hoehe = 120,
        onClick = vm::starten,
    )
    if (baustellen.isEmpty()) {
        Text(
            "Die Listen werden geladen. Beim ersten Start bitte kurz mit Internet verbinden.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LaufBereich(
    lauf: Laufend,
    baustelle: String,
    taetigkeit: String,
    jetzt: Long,
    onPause: () -> Unit,
    onWeiter: () -> Unit,
    onStopp: () -> Unit,
) {
    val farben = LocalZustand.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Schild(if (lauf.inPause) "PAUSE" else "LÄUFT", if (lauf.inPause) farben.pause else farben.laeuft)
            Text(
                Zeitrechnung.hms(Zeitrechnung.arbeitMs(lauf.start, lauf.pausen, jetzt)),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(baustelle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
    if (lauf.inPause) {
        GrosserKnopf("WEITER", farben.laeuft, onClick = onWeiter)
    } else {
        GrosserKnopf("PAUSE", farben.pause, onClick = onPause)
    }
    GrosserKnopf("STOPP", farben.stopp, onClick = onStopp)
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
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Beschriftung("Heute", Modifier.weight(1f))
            Text("${Zeitrechnung.hMin(heute.sumOf { it.nettoMin })} h", fontWeight = FontWeight.Bold)
        }
        Karte {
            heute.sortedBy { it.start }.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
        if (mitDatum) append(Zeitrechnung.tagText(e.datum)).append("  ")
        append(zeit).append("  ").append(e.baustelle)
    }
    val unten = listOfNotNull(
        e.person.takeIf { mitPerson && it.isNotBlank() },
        e.taetigkeit.ifBlank { null },
        e.notiz.ifBlank { null },
        if (e.pauseMin > 0) "Pause ${e.pauseMin} min" else null,
    ).joinToString(" · ")
    Zeile(kopf, Zeitrechnung.hMin(e.nettoMin), unten, onClick = onClick)
}
