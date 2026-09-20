package com.atop.taskmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atop.taskmanager.domain.ProcessInfo

/**
 * Dialog akcí nad procesem.
 * Potvrzovací dialog před kill je povinný (uživatel může omylem long-pressnout).
 */
@Composable
fun ProcessActionDialog(
    process: ProcessInfo,
    onDismiss: () -> Unit,
    onKill: (force: Boolean) -> Unit,
    onRenice: (priority: Int) -> Unit
) {
    var reniceMode by remember { mutableStateOf(false) }
    var priority by remember { mutableStateOf(process.nice.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(process.displayName) },
        text = {
            Column {
                Text(
                    text = "PID ${process.pid} • CPU ${formatPercent(process.cpuPercent)} • RSS ${formatKb(process.rssKb)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (reniceMode) {
                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it.filter { c -> c.isDigit() || c == '-' } },
                        label = { Text("Nice (-20..19)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                } else {
                    Text(
                        text = "SIGTERM dá procesu šanci uklidit se. SIGKILL zabije okamžitě.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (reniceMode) {
                TextButton(onClick = {
                    priority.toIntOrNull()?.let(onRenice)
                    onDismiss()
                }) { Text("Nastavit nice") }
            } else {
                TextButton(onClick = { onKill(true) }) {
                    Text("Kill -9", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!reniceMode) {
                    TextButton(onClick = { onKill(false) }) { Text("SIGTERM") }
                    TextButton(onClick = { reniceMode = true }) { Text("Renice") }
                }
                TextButton(onClick = onDismiss) { Text("Zrušit") }
            }
        }
    )
}