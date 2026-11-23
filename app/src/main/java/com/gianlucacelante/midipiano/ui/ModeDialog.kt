package com.gianlucacelante.midipiano.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gianlucacelante.midipiano.practice.ModeLabels
import com.gianlucacelante.midipiano.practice.StudyMode
import com.gianlucacelante.midipiano.song.Hand

/** «Come vuoi studiare?»: modalità (impara, a tempo, ascolta) e accompagnamento; le mani si scelgono dai cerchi del player. */
@Composable
fun ModeDialog(
    mode: StudyMode, hand: Hand, backing: Boolean,
    onMode: (StudyMode) -> Unit, onBacking: (Boolean) -> Unit, onDismiss: () -> Unit,
) {
    val accent = FlowColors.Accent
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(560.dp).clip(RoundedCornerShape(24.dp)).background(FlowColors.Surface).padding(24.dp)) {
            Text("Come vuoi studiare?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)

            Text("Modalità", color = FlowColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((m, title, sub) in listOf(
                    Triple(StudyMode.WAIT, "Impara", "il brano aspetta le tue note"),
                    Triple(StudyMode.TEMPO, "A tempo", "scorre da solo, conta la precisione"),
                    Triple(StudyMode.LISTEN, "Ascolta", "suona lui, tu guardi"),
                )) {
                    val on = mode == m
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) FlowColors.SurfaceHigh else Color.Transparent)
                            .border(2.dp, if (on) accent else FlowColors.SurfaceHigh, RoundedCornerShape(14.dp)).clickable { onMode(m) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(title, color = if (on) accent else Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(100.dp))
                        Text(sub, color = FlowColors.Muted, fontSize = 14.sp)
                    }
                }
            }

            if (mode != StudyMode.LISTEN) {
                // le mani si scelgono toccando i cerchi accanto al player
                if (ModeLabels.backingApplies(mode, hand)) {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("L'app suona l'altra mano", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Switch(checked = backing, onCheckedChange = onBacking, colors = SwitchDefaults.colors(checkedTrackColor = accent))
                    }
                }
            }

            Text(ModeLabels.describe(mode, hand, backing), color = FlowColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Fatto", color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
