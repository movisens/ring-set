package com.krejci.halo.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krejci.halo.data.MetricType
import kotlinx.coroutines.launch
import com.krejci.halo.ui.RingViewModel
import com.krejci.halo.ui.SleepColor
import com.krejci.halo.ui.components.ScreenHeader
import com.krejci.halo.ui.components.SectionLabel
import com.krejci.halo.ui.metricColor

@Composable
fun DataScreen(vm: RingViewModel, onExportShare: () -> Unit) {
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    ScreenHeader("Data", if (syncStatus.isBlank()) "Sync pulls the ring's logs into the app" else syncStatus,
        "The ring stores several days of readings on-device. \"Sync now\" pulls heart rate, steps, " +
            "SpO₂, sleep, stress and HRV into the app and keeps CSV copies you can share or pull " +
            "off with pull-data.ps1.")

    SectionLabel("Stored")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            for (m in MetricType.entries) {
                val c by vm.count(m).collectAsStateWithLifecycle(0)
                DataRow(m.label, "$c pts", metricColor(m))
            }
            val sc by vm.sleepCount().collectAsStateWithLifecycle(0)
            DataRow("Sleep", "$sc segs", SleepColor)
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { vm.sync() }, enabled = !syncing, modifier = Modifier.weight(1f)) {
            Text(if (syncing) "Syncing…" else "Sync now")
        }
        OutlinedButton(onClick = onExportShare, modifier = Modifier.weight(1f)) { Text("Export & share") }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        if (vm.lastSync > 0L) "Last synced ${fmtSync(vm.lastSync)}" else "Not synced yet",
        Modifier.fillMaxWidth(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
    )

    SectionLabel("Backup & restore")
    BackupCard(vm)

    SectionLabel("Options")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-sync", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Pulls new data about every ${vm.lastInterval} min while the app is open", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.autoSyncEnabled, onCheckedChange = { vm.setAutoSync(it) })
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Continuous HR logging", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Now in the Control tab under \"Background logging\", with keep-alive and battery controls.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BackupCard(vm: RingViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; status = "Backing up…"
        scope.launch {
            val ok = runCatching {
                val json = vm.exportBackup()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    ?: error("no stream")
            }.isSuccess
            busy = false
            status = if (ok) "Backed up ✓ — keep this file safe." else "Backup failed."
        }
    }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingRestore = uri }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Save everything — heart rate, steps, sleep, workouts and your profile — to one JSON " +
                    "file, then restore it after reinstalling. This is the only way your history " +
                    "survives an uninstall.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { saveLauncher.launch("halo-backup-${fmtStamp()}.json") },
                    enabled = !busy, modifier = Modifier.weight(1f),
                ) { Text("Back up") }
                OutlinedButton(
                    onClick = { openLauncher.launch(arrayOf("application/json", "*/*")) },
                    enabled = !busy, modifier = Modifier.weight(1f),
                ) { Text("Restore") }
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    val uri = pendingRestore
    if (uri != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRestore = null },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true; status = "Restoring…"
                    scope.launch {
                        val result = runCatching {
                            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                ?: error("no stream")
                            vm.importBackup(text)
                        }
                        busy = false
                        pendingRestore = null
                        status = result.fold(
                            onSuccess = { "Restored ${it.total} records ✓" },
                            onFailure = { "Restore failed — not a valid Halo backup?" },
                        )
                    }
                }) { Text("Replace & restore") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingRestore = null }) { Text("Cancel") } },
            title = { Text("Restore backup?") },
            text = { Text("This replaces all data currently in the app with the contents of the chosen backup file.") },
        )
    }
}

@Composable
private fun DataRow(name: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

private fun fmtSync(ms: Long) = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.US).format(java.util.Date(ms))

private fun fmtStamp() = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
