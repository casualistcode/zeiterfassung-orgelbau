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

@OptIn(ExperimentalCoroutinesApi::class)
class StempelViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
    private val db = Datenbank()
    private val uid = MutableStateFlow(db.uid)

    var person by mutableStateOf(prefs.getString("person", null))
        private set
    var keinInternet by mutableStateOf(false)
        private set
    var baustelleId by mutableStateOf(prefs.getString("baustelleId", null))
        private set
    var taetigkeitId by mutableStateOf(prefs.getString("taetigkeitId", null))
        private set

    val baustellen = mitUid(emptyList()) { db.baustellen() }
    val taetigkeiten = mitUid(emptyList()) { db.taetigkeiten() }
    val heute = mitUid(emptyList()) { db.eintraegeAm(it, Zeitrechnung.datum(System.currentTimeMillis())) }
    val lauf: StateFlow<Lauf?> = uid
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
                uid.value = db.uid
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

    fun starten() {
        val u = uid.value ?: return
        val b = baustelleId ?: return
        val t = taetigkeitId ?: return
        db.starten(u, person ?: "", b, t, System.currentTimeMillis())
    }

    fun pause() {
        val u = uid.value ?: return
        val l = lauf.value?.wert ?: return
        if (l.inPause) return
        db.pausenSetzen(u, l.pausen + Pause(System.currentTimeMillis(), null))
    }

    fun weiter() {
        val u = uid.value ?: return
        val l = lauf.value?.wert ?: return
        val jetzt = System.currentTimeMillis()
        db.pausenSetzen(u, l.pausen.map { if (it.bis == null) it.copy(bis = jetzt) else it })
    }

    fun stoppen(notiz: String, zeitpunkt: Long) {
        val u = uid.value ?: return
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

    private fun <T> mitUid(leer: T, quelle: (String) -> Flow<T>): StateFlow<T> = uid
        .flatMapLatest { u -> if (u == null) flowOf(leer) else quelle(u) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, leer)
}
