package de.orgelbau.zeiterfassung

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Zugriff auf Firestore. Schreibvorgänge werden bewusst nicht abgewartet:
 * Firestore speichert sie sofort auf dem Handy und schickt sie selbstständig
 * los, sobald wieder Netz da ist. Die Beobachter (Flows) melden die Änderung
 * sofort aus dem lokalen Zwischenspeicher.
 */
class Datenbank {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    val uid: String? get() = auth.currentUser?.uid

    /** Anonyme Anmeldung; braucht nur beim allerersten Start Internet. */
    suspend fun anmelden() {
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    fun baustellen(): Flow<List<Baustelle>> = beobachte(db.collection("baustellen")) { d ->
        Baustelle(
            id = d.id,
            name = d.getString("name") ?: "",
            ort = d.getString("ort") ?: "",
            kmEinfach = d.getDouble("kmEinfach"),
            archiviert = d.getBoolean("archiviert") == true,
        )
    }

    fun taetigkeiten(): Flow<List<Taetigkeit>> = beobachte(db.collection("taetigkeiten")) { d ->
        Taetigkeit(
            id = d.id,
            name = d.getString("name") ?: "",
            reihenfolge = (d.getLong("reihenfolge") ?: 999).toInt(),
            archiviert = d.getBoolean("archiviert") == true,
        )
    }

    /** Alle Einträge aller Personen (für Auswertung und eigene Liste; die Datenmenge ist klein). */
    fun alleEintraege(): Flow<List<Eintrag>> = beobachte(db.collection("eintraege")) { d ->
        val start = d.getLong("start") ?: 0L
        Eintrag(
            id = d.id,
            person = d.getString("person") ?: "",
            personUid = d.getString("personUid") ?: "",
            baustelleId = d.getString("baustelleId") ?: "",
            baustelle = d.getString("baustelle") ?: "",
            ort = d.getString("ort") ?: "",
            kmEinfach = d.getDouble("kmEinfach"),
            taetigkeitId = d.getString("taetigkeitId") ?: "",
            taetigkeit = d.getString("taetigkeit") ?: "",
            notiz = d.getString("notiz") ?: "",
            start = start,
            ende = d.getLong("ende") ?: start,
            pauseMin = (d.getLong("pauseMin") ?: 0L).toInt(),
            nettoMin = (d.getLong("nettoMin") ?: 0L).toInt(),
            datum = d.getString("datum") ?: Zeitrechnung.datum(start),
            quelle = d.getString("quelle") ?: "",
            erstellt = d.getLong("erstellt") ?: 0L,
        )
    }

    /** Neuer oder geänderter Eintrag aus dem Formular. */
    fun eintragSpeichern(e: EintragEntwurf, baustelle: Baustelle, taetigkeit: Taetigkeit) {
        val z = Zeitrechnung.ausFormular(e.datum, e.beginn, e.ende, e.pauseMin)
        val jetzt = System.currentTimeMillis()
        val daten = mapOf(
            "person" to e.person,
            "personUid" to e.personUid,
            "baustelleId" to baustelle.id,
            "baustelle" to baustelle.name,
            "ort" to baustelle.ort,
            "kmEinfach" to baustelle.kmEinfach,
            "taetigkeitId" to taetigkeit.id,
            "taetigkeit" to taetigkeit.name,
            "notiz" to e.notiz.trim(),
            "start" to z.start,
            "ende" to z.ende,
            "pauseMin" to e.pauseMin,
            "nettoMin" to z.nettoMin,
            "datum" to e.datum.toString(),
            "quelle" to e.quelle,
            "erstellt" to (e.erstellt ?: jetzt),
            "geaendert" to jetzt,
        )
        val ref = if (e.id == null) db.collection("eintraege").document() else db.collection("eintraege").document(e.id)
        ref.set(daten, SetOptions.merge()).addOnFailureListener { Log.w(TAG, "eintragSpeichern", it) }
    }

    fun eintragLoeschen(id: String) {
        db.collection("eintraege").document(id).delete().addOnFailureListener { Log.w(TAG, "eintragLoeschen", it) }
    }

    /** id == null legt eine neue Baustelle an. */
    fun baustelleSpeichern(id: String?, name: String, ort: String, kmEinfach: Double?, archiviert: Boolean) {
        val ref = if (id == null) db.collection("baustellen").document() else db.collection("baustellen").document(id)
        val daten = mutableMapOf<String, Any?>("name" to name, "ort" to ort, "kmEinfach" to kmEinfach, "archiviert" to archiviert)
        if (id == null) daten["erstellt"] = System.currentTimeMillis()
        ref.set(daten, SetOptions.merge()).addOnFailureListener { Log.w(TAG, "baustelleSpeichern", it) }
    }

    /** id == null legt eine neue Tätigkeit an. */
    fun taetigkeitSpeichern(id: String?, name: String, reihenfolge: Int, archiviert: Boolean) {
        val ref = if (id == null) db.collection("taetigkeiten").document() else db.collection("taetigkeiten").document(id)
        ref.set(mapOf("name" to name, "reihenfolge" to reihenfolge, "archiviert" to archiviert), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "taetigkeitSpeichern", it) }
    }

    fun laufend(uid: String): Flow<Laufend?> = callbackFlow {
        val reg = db.collection("laufend").document(uid).addSnapshotListener { s, e ->
            if (e != null) Log.w(TAG, "laufend", e)
            if (s != null) trySend(if (s.exists()) zuLaufend(s) else null)
        }
        awaitClose { reg.remove() }
    }

    fun starten(uid: String, person: String, baustelleId: String, taetigkeitId: String, jetzt: Long) {
        db.collection("laufend").document(uid).set(
            mapOf(
                "person" to person,
                "baustelleId" to baustelleId,
                "taetigkeitId" to taetigkeitId,
                "start" to jetzt,
                "pausen" to emptyList<Any>(),
                "status" to "laeuft",
            )
        ).addOnFailureListener { Log.w(TAG, "starten", it) }
    }

    fun pausenSetzen(uid: String, pausen: List<Pause>) {
        db.collection("laufend").document(uid).update(
            "pausen", pausen.map { mapOf("von" to it.von, "bis" to it.bis) },
            "status", if (pausen.any { it.bis == null }) "pause" else "laeuft",
        ).addOnFailureListener { Log.w(TAG, "pausen", it) }
    }

    /** Eintrag anlegen und laufende Zeit entfernen, beides in einem Rutsch. */
    fun stoppen(
        uid: String,
        person: String,
        lauf: Laufend,
        baustelle: Baustelle?,
        taetigkeit: Taetigkeit?,
        notiz: String,
        jetzt: Long,
    ) {
        val a = Zeitrechnung.abschluss(lauf.start, lauf.pausen, jetzt)
        val batch = db.batch()
        batch.set(
            db.collection("eintraege").document(),
            mapOf(
                "person" to person,
                "personUid" to uid,
                "baustelleId" to lauf.baustelleId,
                "baustelle" to (baustelle?.name ?: lauf.baustelleId),
                "ort" to (baustelle?.ort ?: ""),
                "kmEinfach" to baustelle?.kmEinfach,
                "taetigkeitId" to lauf.taetigkeitId,
                "taetigkeit" to (taetigkeit?.name ?: lauf.taetigkeitId),
                "notiz" to notiz,
                "start" to lauf.start,
                "ende" to a.ende,
                "pauseMin" to a.pauseMin,
                "nettoMin" to a.nettoMin,
                "datum" to Zeitrechnung.datum(lauf.start),
                "quelle" to "stempeluhr",
                "erstellt" to jetzt,
                "geaendert" to jetzt,
            )
        )
        batch.delete(db.collection("laufend").document(uid))
        batch.commit().addOnFailureListener { Log.w(TAG, "stoppen", it) }
    }

    /** Pause beginnen bzw. beenden. */
    fun pauseUmschalten(uid: String, lauf: Laufend, jetzt: Long) {
        pausenSetzen(
            uid,
            if (lauf.inPause) lauf.pausen.map { if (it.bis == null) it.copy(bis = jetzt) else it }
            else lauf.pausen + Pause(jetzt, null),
        )
    }

    /** Für das Widget: laufende Zeit aus dem Zwischenspeicher auf dem Handy (geht ohne Netz). */
    suspend fun laufendAusSpeicher(uid: String): Laufend? = try {
        val s = db.collection("laufend").document(uid).get(Source.CACHE).await()
        if (s.exists()) zuLaufend(s) else null
    } catch (_: Exception) {
        null
    }

    suspend fun baustellenNameAusSpeicher(id: String): String? = try {
        db.collection("baustellen").document(id).get(Source.CACHE).await().getString("name")
    } catch (_: Exception) {
        null
    }

    private fun zuLaufend(d: DocumentSnapshot): Laufend {
        @Suppress("UNCHECKED_CAST")
        val roh = d.get("pausen") as? List<Map<String, Any?>> ?: emptyList()
        return Laufend(
            baustelleId = d.getString("baustelleId") ?: "",
            taetigkeitId = d.getString("taetigkeitId") ?: "",
            start = d.getLong("start") ?: 0L,
            pausen = roh.map { Pause((it["von"] as Number).toLong(), (it["bis"] as? Number)?.toLong()) },
        )
    }

    private fun <T> beobachte(q: Query, umwandeln: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        val reg = q.addSnapshotListener { s, e ->
            if (e != null) Log.w(TAG, "beobachte", e)
            if (s != null) trySend(s.documents.map(umwandeln))
        }
        awaitClose { reg.remove() }
    }

    private companion object {
        const val TAG = "Datenbank"
    }
}
