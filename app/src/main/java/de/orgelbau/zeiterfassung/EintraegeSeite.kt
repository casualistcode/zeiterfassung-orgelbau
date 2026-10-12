package de.orgelbau.zeiterfassung

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Alle eigenen Einträge, nach Tagen sortiert (neueste zuerst). */
@Composable
fun EintraegeSeite(vm: AppViewModel) {
    val eintraege by vm.eintraege.collectAsStateWithLifecycle()
    val meine = eintraege.filter(vm::istMeiner)
    val tage = meine.groupBy { it.datum }.toSortedMap(Comparator.reverseOrder())

    SeitenRahmen("Einträge", vm.person) {
        GrosserKnopf("Zeit nachtragen", MaterialTheme.colorScheme.primary, hoehe = 72, onClick = vm::zeitNachtragen)
        if (tage.isEmpty()) {
            Text("Noch keine Einträge.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        tage.forEach { (datum, es) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Beschriftung(Zeitrechnung.tagText(datum), Modifier.weight(1f))
                    Text("${Zeitrechnung.hMin(es.sumOf { it.nettoMin })} h", fontWeight = FontWeight.Bold)
                }
                Karte {
                    es.sortedBy { it.start }.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        EintragZeile(e, mitDatum = false) { vm.bearbeiten(e) }
                    }
                }
            }
        }
    }
}
