package de.orgelbau.zeiterfassung

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class Seite(val titel: String) {
    STEMPELN("Stempeln"),
    EINTRAEGE("Einträge"),
    AUSWERTUNG("Auswertung"),
    LISTEN("Listen"),
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
    private val db = Datenbank()
    private val uidFlow = MutableStateFlow(db.uid)
    val uid: String? get() = uidFlow.value

    var person by mutableStateOf(prefs.getString("person", null))
        private set
    var keinInternet by mutableStateOf(false)
        private set
    var baustelleId by mutableStateOf(prefs.getString("baustelleId", null))
        private set
    var taetigkeitId by mutableStateOf(prefs.getString("taetigkeitId", null))
        private set

    var seite by mutableStateOf(Seite.STEMPELN)

    /** Offenes Formular „Eintrag bearbeiten / Zeit nachtragen“, sonst null. */
    var formular by mutableStateOf<EintragEntwurf?>(null)
        private set

    // Auswahl in der Auswertung (bleibt beim Wechsel der Seiten erhalten)
    var awBaustelleGewaehlt by mutableStateOf(false)
        private set
    var awBaustelle by mutableStateOf<String?>(null)
        private set
    var awZeitraum by mutableStateOf(Zeitraum.GESAMT)
    var awVon by mutableStateOf<LocalDate?>(null)
    var awBis by mutableStateOf<LocalDate?>(null)
    var awPerson by mutableStateOf<String?>(null)

    val baustellen = mitUid(emptyList()) { db.baustellen() }
    val taetigkeiten = mitUid(emptyList()) { db.taetigkeiten() }
    val eintraege = mitUid(emptyList()) { db.alleEintraege() }
    val lauf: StateFlow<Lauf?> = uidFlow
        .flatMapLatest { u -> if (u == null) flowOf(null) else db.laufend(u).map { Lauf(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        anmelden()
    }

    fun anmelden() {
        keinInternet = false
        viewModelScope.launch {
            try {
                db.anmelden()
                uidFlow.value = db.uid
            } catch (e: Exception) {
                keinInternet = true
                return@launch
            }
            try {
                db.anfangsbestandAnlegen()
            } catch (_: Exception) {
                // ohne Netz: beim nächsten Start erneut versuchen
            }
        }
    }

    /** Eigene Einträge: gleiche Anmeldung oder gleicher Name (z. B. nach Neuinstallation). */
    fun istMeiner(e: Eintrag): Boolean = e.personUid == uid || e.person == person

    fun personSetzen(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        prefs.edit().putString("person", n).apply()
        person = n
    }

    fun baustelleWaehlen(id: String) {
        prefs.edit().putString("baustelleId", id).apply()
        baustelleId = id
    }

    fun taetigkeitWaehlen(id: String) {
        prefs.edit().putString("taetigkeitId", id).apply()
        taetigkeitId = id
    }

    // --- Stempeluhr ---

    fun starten() {
        val u = uid ?: return
        val b = baustelleId ?: return
        val t = taetigkeitId ?: return
        db.starten(u, person ?: "", b, t, System.currentTimeMillis())
    }

    fun pause() {
        val u = uid ?: return
        val l = lauf.value?.wert ?: return
        if (l.inPause) return
        db.pausenSetzen(u, l.pausen + Pause(System.currentTimeMillis(), null))
    }

    fun weiter() {
        val u = uid ?: return
        val l = lauf.value?.wert ?: return
        val jetzt = System.currentTimeMillis()
        db.pausenSetzen(u, l.pausen.map { if (it.bis == null) it.copy(bis = jetzt) else it })
    }

    fun stoppen(notiz: String, zeitpunkt: Long) {
        val u = uid ?: return
        val l = lauf.value?.wert ?: return
        db.stoppen(
            uid = u,
            person = person ?: "",
            lauf = l,
            baustelle = baustellen.value.find { it.id == l.baustelleId },
            taetigkeit = taetigkeiten.value.find { it.id == l.taetigkeitId },
            notiz = notiz.trim(),
            jetzt = zeitpunkt,
        )
    }

    // --- Einträge bearbeiten / nachtragen ---

    fun zeitNachtragen() {
        formular = EintragEntwurf(
            id = null,
            person = person ?: "",
            personUid = uid ?: "",
            quelle = "nachgetragen",
            erstellt = null,
            datum = LocalDate.now(),
            beginn = LocalTime.of(7, 0),
            ende = LocalTime.of(16, 0),
            pauseMin = 30,
            baustelleId = baustelleId,
            taetigkeitId = taetigkeitId,
            notiz = "",
        )
    }

    fun bearbeiten(e: Eintrag) {
        formular = EintragEntwurf.aus(e)
    }

    fun formularSchliessen() {
        formular = null
    }

    fun formularSpeichern(e: EintragEntwurf) {
        val b = baustellen.value.find { it.id == e.baustelleId } ?: return
        val t = taetigkeiten.value.find { it.id == e.taetigkeitId } ?: return
        db.eintragSpeichern(e, b, t)
        formular = null
    }

    fun eintragLoeschen(id: String) {
        db.eintragLoeschen(id)
        formular = null
    }

    // --- Listen ---

    fun baustelleSpeichern(id: String?, name: String, ort: String, kmEinfach: Double?, archiviert: Boolean) =
        db.baustelleSpeichern(id, name.trim(), ort.trim(), kmEinfach, archiviert)

    fun taetigkeitSpeichern(id: String?, name: String, archiviert: Boolean) {
        val vorhanden = taetigkeiten.value.find { it.id == id }
        val reihenfolge = vorhanden?.reihenfolge ?: ((taetigkeiten.value.maxOfOrNull { it.reihenfolge } ?: -1) + 1)
        db.taetigkeitSpeichern(id, name.trim(), reihenfolge, archiviert)
    }

    // --- Auswertung ---

    /** Ohne eigene Wahl: die Baustelle des neuesten Eintrags. null heißt „Alle Baustellen“. */
    fun awBaustelleAktuell(alle: List<Eintrag>): String? =
        if (awBaustelleGewaehlt) awBaustelle else alle.maxByOrNull { it.start }?.baustelleId

    fun awBaustelleWaehlen(id: String?) {
        awBaustelleGewaehlt = true
        awBaustelle = id
    }

    private fun <T> mitUid(leer: T, quelle: (String) -> Flow<T>): StateFlow<T> = uidFlow
        .flatMapLatest { u -> if (u == null) flowOf(leer) else quelle(u) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, leer)
}
