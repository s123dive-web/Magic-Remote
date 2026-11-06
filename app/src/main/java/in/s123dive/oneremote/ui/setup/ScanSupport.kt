package `in`.s123dive.oneremote.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.s123dive.oneremote.AppGraph
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.core.SendResult
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.ir.IrCode
import `in`.s123dive.oneremote.ir.NecSweep
import kotlinx.coroutines.delay

/** One IR send for the wizards: through the Router as a DEBUG diagnostic so it is logged. */
suspend fun AppGraph.sendIrDiagnostic(label: String, code: IrCode): SendResult {
    val r = router.diagnostic(label, TransportKind.IR, code.describe()) { ir.send(code.carrierHz, code.pattern(`in`.s123dive.oneremote.ir.IrToggle.next()), code.describe()) }
    return when (r) {
        is RouteResult.Sent -> SendResult.Ok(r.payload)
        is RouteResult.Failed -> SendResult.Failed(code.describe(), r.reason)
        is RouteResult.Unsupported -> SendResult.Unsupported(r.reason)
    }
}

/**
 * "It reacted!" always lags the send, so the wizard replays the last few candidates one at a
 * time, 2.5 s apart, and asks after each one. The confirmed candidate is the answer.
 */
@Composable
fun VerifyDialog(graph: AppGraph, label: String, recent: List<NecSweep.Candidate>, onConfirmed: (NecSweep.Candidate) -> Unit, onNone: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var sent by remember { mutableStateOf(false) }
    val current = recent.getOrNull(index)
    LaunchedEffect(index) {
        sent = false
        val c = recent.getOrNull(index) ?: return@LaunchedEffect
        delay(800)
        graph.sendIrDiagnostic("$label verify", c.code)
        sent = true
    }
    AlertDialog(
        onDismissRequest = onNone,
        title = { Text("Did the BOX react just now?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current == null) Text("None of the recent codes was confirmed.")
                else {
                    Text("Re-sending ${index + 1} of ${recent.size}: ${current.label}")
                    LinearProgressIndicator(progress = { (index + 1f) / recent.size }, modifier = Modifier.fillMaxWidth())
                    Text(if (sent) "Sent. Did the box itself react? If only the TV reacted, answer No." else "Sending…", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current != null) {
                    Button(enabled = sent, onClick = { onConfirmed(current) }) { Text("Yes") }
                    OutlinedButton(enabled = sent, onClick = { if (index + 1 < recent.size) index++ else onNone() }) { Text("No, next") }
                    TextButton(enabled = sent, onClick = { index = index }) { Text("Send again") }
                } else TextButton(onClick = onNone) { Text("Close") }
            }
        },
    )
}
