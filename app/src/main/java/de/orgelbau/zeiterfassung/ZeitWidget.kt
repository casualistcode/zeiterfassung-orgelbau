package de.orgelbau.zeiterfassung

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.firebase.auth.FirebaseAuth

private val GRUEN = Color(0xFF2E9A66)
private val ORANGE = Color(0xFFC98316)
private val ROT = Color(0xFFC4503F)
private val HINTERGRUND = Color(0xFF16211E)
private val MESSING = Color(0xFFD8A748)

private data class WidgetZustand(val lauf: Laufend?, val baustelle: String?, val bereit: Boolean)

/**
 * Kleines Widget: im Ruhezustand nur „Start“ (mit der zuletzt gewählten
 * Baustelle und Tätigkeit), während der Arbeit „Pause/Weiter“ und „Stopp“.
 * Stopp öffnet die App, weil dort „Was wurde gemacht?“ abgefragt wird.
 */
class ZeitWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zustand = laden(context)
        provideContent { Inhalt(zustand) }
    }

    private suspend fun laden(context: Context): WidgetZustand {
        val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
        val gewaehlt = prefs.getString("baustelleId", null)
        val bereit = gewaehlt != null && prefs.getString("taetigkeitId", null) != null
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return WidgetZustand(null, null, false)
        val db = Datenbank()
        val lauf = db.laufendAusSpeicher(uid)
        val name = (lauf?.baustelleId ?: gewaehlt)?.let { db.baustellenNameAusSpeicher(it) }
        return WidgetZustand(lauf, name, bereit)
    }

    @Composable
    private fun Inhalt(z: WidgetZustand) {
        val context = LocalContext.current
        val klein = TextStyle(color = ColorProvider(MESSING), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        val l = z.lauf
        Column(
            GlanceModifier.fillMaxSize().background(HINTERGRUND).cornerRadius(24.dp).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (l == null) {
                val aktion = if (z.bereit) actionRunCallback<StartAktion>() else actionStartActivity<MainActivity>()
                Knopf("▶  Start", GRUEN, aktion, GlanceModifier.fillMaxWidth().defaultWeight())
                if (z.baustelle != null) {
                    Text(z.baustelle, style = klein, maxLines = 1, modifier = GlanceModifier.padding(top = 6.dp))
                }
            } else {
                Text(
                    if (l.inPause) "Pause" else "Läuft seit ${Zeitrechnung.uhrzeit(l.start)}",
                    style = klein,
                    maxLines = 1,
                    modifier = GlanceModifier.padding(bottom = 6.dp),
                )
                Knopf(
                    if (l.inPause) "Weiter" else "Pause",
                    if (l.inPause) GRUEN else ORANGE,
                    actionRunCallback<PauseAktion>(),
                    GlanceModifier.fillMaxWidth().defaultWeight(),
                )
                Spacer(GlanceModifier.height(6.dp))
                Knopf(
                    "Stopp",
                    ROT,
                    actionStartActivity(
                        Intent(context, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_STOPP, true)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    ),
                    GlanceModifier.fillMaxWidth().defaultWeight(),
                )
            }
        }
    }

    @Composable
    private fun Knopf(text: String, farbe: Color, aktion: Action, modifier: GlanceModifier) {
        Box(
            modifier.background(farbe).cornerRadius(18.dp).clickable(aktion),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = TextStyle(color = ColorProvider(Color.White), fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }
    }
}

class ZeitWidgetEmpfaenger : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ZeitWidget()
}

class StartAktion : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
        val b = prefs.getString("baustelleId", null) ?: return
        val t = prefs.getString("taetigkeitId", null) ?: return
        Datenbank().starten(uid, prefs.getString("person", null) ?: "", b, t, System.currentTimeMillis())
        ZeitWidget().updateAll(context)
    }
}

class PauseAktion : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = Datenbank()
        val lauf = db.laufendAusSpeicher(uid) ?: return
        db.pauseUmschalten(uid, lauf, System.currentTimeMillis())
        ZeitWidget().updateAll(context)
    }
}
