package de.orgelbau.zeiterfassung

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

    fun eintraegeAm(uid: String, datum: String): Flow<List<Eintrag>> = beobachte(
        db.collection("eintraege").whereEqualTo("personUid", uid).whereEqualTo("datum", datum)
    ) { d ->
        Eintrag(
            id = d.id,
            baustelle = d.getString("baustelle") ?: "",
            taetigkeit = d.getString("taetigkeit") ?: "",
            notiz = d.getString("notiz") ?: "",
            start = d.getLong("start") ?: 0L,
            ende = d.getLong("ende") ?: 0L,
            pauseMin = (d.getLong("pauseMin") ?: 0L).toInt(),
            nettoMin = (d.getLong("nettoMin") ?: 0L).toInt(),
        )
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

    /**
     * Legt beim allerersten Start die Anfangslisten an. Fragt dafür bewusst
     * den Server (nicht den Zwischenspeicher), damit nie bearbeitete Einträge
     * überschrieben werden. Ohne Netz passiert nichts.
     */
    suspend fun anfangsbestandAnlegen() {
        val leer = db.collection("baustellen").limit(1).get(Source.SERVER).await().isEmpty
        if (!leer) return
        val jetzt = System.currentTimeMillis()
        val batch = db.batch()
        listOf(
            Triple("jaegerwirt", "Jägerwirt", "Fürstenzell"),
            Triple("koesslarn", "Kößlarn", "Kößlarn"),
            Triple("denkhof", "Denkhof", "bei Passau (ca. 17 km nördlich)"),
            Triple("werkstatt-passau", "Werkstatt Passau", "Passau"),
        ).forEach { (id, name, ort) ->
            batch.set(
                db.collection("baustellen").document(id),
                mapOf("name" to name, "ort" to ort, "kmEinfach" to null, "archiviert" to false, "erstellt" to jetzt),
            )
        }
        listOf(
            "Ausbau & Reinigung", "Einbau", "Reparatur", "Intonation", "Stimmung",
            "Elektrik / Schaltschrank", "Vorbereitung / Material", "Sonstiges",
        ).forEachIndexed { i, name ->
            batch.set(
                db.collection("taetigkeiten").document("t%02d".format(i + 1)),
                mapOf("name" to name, "reihenfolge" to i, "archiviert" to false),
            )
        }
        batch.commit().await()
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
