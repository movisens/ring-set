package com.krejci.halo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krejci.halo.ble.Conn
import com.krejci.halo.ble.ClockSyncState
import com.krejci.halo.ui.RingViewModel
import com.krejci.halo.ui.components.ScreenHeader
import com.krejci.halo.ui.components.SectionLabel
import java.text.DateFormat
import java.util.Date

private val CARD = RoundedCornerShape(18.dp)

@Composable
fun ControlScreen(vm: RingViewModel) {
    ScreenHeader("Control", "Interval, alerts & camera",
        "How often the ring records a heart-rate reading in the background. Lower = more detail " +
            "but more battery use. The ring can drift back to its old value after you change it — " +
            "leave \"Reconnect after setting\" on so the new interval sticks.")

    Spacer(Modifier.height(14.dp))
    StatusStrip(vm)

    // ---- logging interval: presets + custom under one heading ----
    SectionLabel("Logging interval")
    val presets = listOf(1, 3, 5, 10, 30, 60)
    var custom by remember { mutableStateOf("") }
    val customMin = custom.toIntOrNull()
    val customOk = customMin != null && customMin in 1..255
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in presets.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (p in row) Preset(p, p == vm.lastInterval, Modifier.weight(1f)) { vm.setInterval(p) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Custom (1–255 min)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Button(onClick = { if (customOk) { vm.setInterval(customMin!!); custom = "" } }, enabled = customOk) {
                Text("Set")
            }
        }
        OutlinedButton(onClick = { vm.resetMeasurement() }, modifier = Modifier.fillMaxWidth()) {
            Text("Reset measurement")
        }
        Text("Stuck at 0 min / OFF, or a new interval won't take? This reconnects and re-applies " +
            "your interval (${vm.lastInterval} min) from scratch, then reads it back to confirm.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    BackgroundLoggingSection(vm)

    MeasurementTogglesSection(vm)

    RingTimeSection(vm)

    // ---- connection: reconnect option + actions in one card ----
    SectionLabel("Connection")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = CARD) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reconnect after setting", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Makes a new interval stick", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.autoReconnect, onCheckedChange = { vm.updateAuto(it) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.readInterval() }, modifier = Modifier.weight(1f)) { Text("Check ring") }
                Button(onClick = { vm.reconnect() }, modifier = Modifier.weight(1f)) { Text("Reconnect") }
            }
        }
    }

    // ---- health alerts ----
    SectionLabel("Health alerts")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = CARD) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Heart-rate alerts", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Notify on a spike or prolonged high HR with no activity logged", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.hrAlertsEnabled, onCheckedChange = { vm.setHrAlerts(it) })
            }
            if (vm.hrAlertsEnabled) {
                var thr by remember { mutableStateOf(vm.hrSpike.toString()) }
                OutlinedTextField(
                    value = thr,
                    onValueChange = { thr = it.filter(Char::isDigit).take(3); thr.toIntOrNull()?.let { v -> vm.updateHrSpike(v) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Spike threshold (bpm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("Checked after each sync. Mark an activity to mute alerts during exercise.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    CameraShutterSection(vm)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun RingTimeSection(vm: RingViewModel) {
    val clock by vm.clockSync.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val busy = clock.state == ClockSyncState.CONNECTING || clock.state == ClockSyncState.SETTING
    val detail = when (clock.state) {
        ClockSyncState.NOT_CHECKED -> "Not checked in this app session yet"
        ClockSyncState.CONNECTING -> "Connecting to update the ring clock…"
        ClockSyncState.SETTING -> "Writing the phone's current local time…"
        ClockSyncState.CONFIRMED -> {
            val whenText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                .format(Date(clock.confirmedAtMs))
            val why = when (clock.source) {
                "connection" -> "after connection"
                "sync" -> "after data sync"
                else -> "manually"
            }
            "Confirmed by the ring $whenText · $why"
        }
        ClockSyncState.FAILED -> "No confirmation received — wake the ring and try again"
    }

    SectionLabel("Ring time")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = CARD) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Automatic clock sync", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "The phone's local date and time are sent after every connection and completed data sync.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                detail,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (clock.state == ClockSyncState.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = { vm.checkAndSetTime() },
                enabled = !busy && !syncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Checking & setting…" else "Check & set now")
            }
            Text(
                "The ring has no read-clock command. This writes the current phone time and waits " +
                    "for the ring to confirm it accepted the command.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Slim connection/status line: a state-coloured dot, the latest status text, and battery %. */
@Composable
private fun StatusStrip(vm: RingViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val conn by vm.conn.collectAsStateWithLifecycle()
    val battery by vm.battery.collectAsStateWithLifecycle()
    val dot = when (conn) {
        Conn.CONNECTED -> MaterialTheme.colorScheme.primary
        Conn.CONNECTING -> Color(0xFFFBBF24)
        Conn.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = CARD) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(11.dp))
            Text(status, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, maxLines = 2)
            battery?.let {
                Spacer(Modifier.width(10.dp))
                Text((if (it.charging) "⚡ " else "") + "${it.level}%", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CameraShutterSection(vm: RingViewModel) {
    val ctx = LocalContext.current
    val a11y by vm.a11yConnected.collectAsStateWithLifecycle()
    val camOn = vm.cameraGestureOn

    SectionLabel("Camera shutter")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = CARD) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Shake or tap your ring to take a photo", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("Turn this on, open your camera, then shake or firmly tap the ring — Halo presses " +
                "the shutter of whatever camera is open.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 1) accessibility permission
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (a11y) "Accessibility service: on" else "Accessibility service: off",
                        fontWeight = FontWeight.SemiBold,
                        color = if (a11y) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text("Lets Halo press the shutter in other apps", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text(if (a11y) "Settings" else "Enable")
                }
            }

            // 2) master toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Camera gesture", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (camOn) "On — shake or tap the ring to shoot" else "Off", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = camOn, onCheckedChange = { vm.setCameraGesture(it) })
            }

            // 3) jump to the camera, or test the shutter tap (opens the camera, then taps after ~2.5s)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    runCatching { ctx.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, modifier = Modifier.weight(1f), enabled = camOn) { Text("Open camera") }
                OutlinedButton(onClick = {
                    runCatching { ctx.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    vm.testCameraShutter()
                }, modifier = Modifier.weight(1f), enabled = a11y) { Text("Test tap") }
            }

            if (!a11y) Text("Turn on the accessibility service first, or the shutter tap won't work.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** SpO₂ / stress / HRV are on/off switches stored on the ring itself; while off the ring records
 *  nothing for that metric, so the charts and CSVs stay empty. These switches push the command to
 *  the ring immediately and are re-applied on every connect. */
@Composable
private fun MeasurementTogglesSection(vm: RingViewModel) {
    SectionLabel("Ring monitoring")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = CARD) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("All-day monitoring", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("These toggles live on the ring. If one is off the ring never records that metric, " +
                "so its chart and CSV stay empty no matter how often you sync. They sample alongside " +
                "the HR cycle (on = more detail, more battery).",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SpO₂ (blood oxygen)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (vm.spo2Enabled) "On — recorded while worn" else "Off — nothing recorded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.spo2Enabled, onCheckedChange = { vm.updateSpo2(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Stress", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (vm.stressEnabled) "On — recorded while worn" else "Off — nothing recorded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.stressEnabled, onCheckedChange = { vm.updateStress(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Heart-rate variability (HRV)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (vm.hrvEnabled) "On — recorded while worn" else "Off — nothing recorded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.hrvEnabled, onCheckedChange = { vm.updateHrv(it) })
            }
        }
    }
}

/**
 * Full control over the keep-alive: the continuous-logging toggle, whether its foreground service is
 * actually live, a battery-optimisation exemption (so Android stops killing it), and a button to
 * bring the notification back if it was dismissed or the service was killed.
 */
@Composable
private fun BackgroundLoggingSection(vm: RingViewModel) {
    val ctx = LocalContext.current
    val serviceRunning by vm.loggingServiceRunning.collectAsStateWithLifecycle()
    val logging = vm.passiveHrEnabled
    // Recomputed whenever the toggle or service state changes, and each time Control is re-entered
    // (e.g. returning from the system battery-optimisation screen).
    val ignoringBattery = remember(logging, serviceRunning) {
        (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
    }

    SectionLabel("Background logging")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = CARD) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Continuous HR logging", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Streams the sensor while the app runs and saves a reading every minute " +
                        "(uses more battery). Bypasses the ring's unreliable HR log.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = logging, onCheckedChange = { vm.setPassiveHr(it) })
            }

            // Live service/notification status.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dot = if (serviceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        serviceRunning -> "Keep-alive running — notification active"
                        logging -> "Logging on, but the keep-alive isn't running"
                        else -> "Keep-alive off"
                    },
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium,
                )
            }

            // Battery-optimisation exemption — the usual reason the notification vanishes in a pocket.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (ignoringBattery) "Battery optimisation: off ✓" else "Battery optimisation: on",
                        fontWeight = FontWeight.SemiBold,
                        color = if (ignoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text("If Android keeps killing the logger in the background, exempt Halo so it can keep running.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { openBatterySettings(ctx) }, enabled = !ignoringBattery) {
                    Text(if (ignoringBattery) "Done" else "Fix")
                }
            }

            OutlinedButton(onClick = { vm.restartLoggingService() }, modifier = Modifier.fillMaxWidth()) {
                Text("Re-show notification")
            }
            Text("Lost the keep-alive notification? This brings it back and restarts the logger.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ask Android to exempt us from battery optimisation, falling back to the general settings list. */
private fun openBatterySettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
private fun Preset(min: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$min min", fontWeight = FontWeight.Bold, fontSize = 16.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}
