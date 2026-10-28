package com.tkdpro.feature.running

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tkdpro.R
import com.tkdpro.core.database.entity.RunType
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.ConfirmDialog
import com.tkdpro.core.designsystem.component.RouteMap
import com.tkdpro.core.designsystem.component.StatTile
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.domain.gps.LocationSample
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.feature.running.service.RunTrackingService
import com.tkdpro.feature.running.tracking.GpsSignal
import com.tkdpro.feature.running.tracking.LocationSource
import com.tkdpro.feature.running.tracking.RunTracker
import com.tkdpro.feature.running.tracking.TrackerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveRunViewModel @Inject constructor(
    private val tracker: RunTracker,
    private val locations: LocationSource,
    settings: SettingsRepository,
) : ViewModel() {
    val state = tracker.state
    val keepScreenOn = settings.settings.map { it.gpsKeepScreenOn }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    /** Pre-start GPS fix used to show accuracy before recording begins; not stored. */
    val warmupFix = MutableStateFlow<LocationSample?>(null)
    private var warmupJob: Job? = null

    init {
        viewModelScope.launch { tracker.recoverIfNeeded() }
    }

    fun startWarmup() {
        if (warmupJob?.isActive == true || !locations.hasPermission()) return
        warmupJob = viewModelScope.launch {
            locations.updates().catch { }.collect { warmupFix.value = it }
        }
    }

    fun stopWarmup() {
        warmupJob?.cancel()
    }

    fun start(context: android.content.Context, type: RunType, name: String?) = viewModelScope.launch {
        stopWarmup()
        tracker.start(type, name)
        RunTrackingService.start(context)
    }

    fun pause() = tracker.pause()

    fun resume(context: android.content.Context) {
        tracker.resume()
        RunTrackingService.start(context)
    }

    fun finish(onFinished: (Long) -> Unit) = viewModelScope.launch {
        tracker.finish()?.let(onFinished)
    }

    fun discard(onDone: () -> Unit) = viewModelScope.launch {
        tracker.discard()
        onDone()
    }

    override fun onCleared() {
        stopWarmup()
    }
}

@Composable
fun LiveRunScreen(onBack: () -> Unit, onFinished: (Long) -> Unit, viewModel: LiveRunViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keepOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    var permissionGranted by remember {
        mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        permissionDenied = !permissionGranted
        if (permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(permissionGranted, state.isRecording) {
        if (permissionGranted && !state.isRecording) viewModel.startWarmup() else viewModel.stopWarmup()
    }
    val view = LocalView.current
    DisposableEffect(keepOn, state.isRecording) {
        view.keepScreenOn = keepOn && state.isRecording
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(topBar = { TkdTopBar(stringResource(R.string.run_live_title), onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !permissionGranted -> PermissionRationale(
                    denied = permissionDenied,
                    onRequest = { locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )
                !state.isRecording -> PreStart(viewModel) { type, name -> viewModel.start(context, type, name) }
                else -> Recording(viewModel, onFinished, onBack)
            }
        }
    }
}

@Composable
private fun PermissionRationale(denied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(Spacing.screen), verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.GpsFixed, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(Spacing.md))
        Text(stringResource(R.string.run_permission_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.run_permission_body), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.sm))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.run_permission_grant)) }
        if (denied) {
            Text(stringResource(R.string.run_permission_denied), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.sm))
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings)) }
        }
    }
}

@Composable
private fun PreStart(viewModel: LiveRunViewModel, onStart: (RunType, String?) -> Unit) {
    val fix by viewModel.warmupFix.collectAsStateWithLifecycle()
    var type by remember { mutableStateOf(RunType.EASY) }
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GpsIndicator(fix?.horizontalAccuracyM, if (fix == null) GpsSignal.NONE else GpsSignal.GOOD)
        Text(stringResource(R.string.run_type), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(RunType.entries, type, { type = it }, { stringResource(it.label()) })
        if (type == RunType.CUSTOM) TkdTextField(name, { name = it.take(40) }, stringResource(R.string.run_custom_name))
        Text(stringResource(R.string.run_prestart_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { onStart(type, name.trim().ifBlank { null }) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.action_start), style = MaterialTheme.typography.titleLarge)
        }
        if ((fix?.horizontalAccuracyM ?: Float.MAX_VALUE) > 25f) {
            Text(stringResource(R.string.run_waiting_accuracy), style = MaterialTheme.typography.bodySmall, color = TkdTheme.colors.warning)
        }
    }
}

@Composable
private fun Recording(viewModel: LiveRunViewModel, onFinished: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val s by viewModel.state.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        RouteMap(s.points, Modifier.fillMaxWidth().height(240.dp), followLast = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GpsIndicator(s.lastAccuracyM, s.signal, Modifier.weight(1f))
                val statusText = when (s.status) {
                    TrackerStatus.PAUSED -> stringResource(if (s.recovered) R.string.run_recovered else R.string.run_paused)
                    TrackerStatus.AUTO_PAUSED -> stringResource(R.string.run_auto_paused)
                    else -> null
                }
                statusText?.let { Text(it, color = TkdTheme.colors.warning, style = MaterialTheme.typography.labelLarge) }
            }
            Text(f.distanceValue(s.distanceM), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
            Text(f.distanceUnit, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TkdCard(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatTile(stringResource(R.string.run_elapsed), f.duration(s.movingMs), Modifier.weight(1f))
                    StatTile(stringResource(R.string.run_current_pace), f.paceValue(s.currentPaceSecPerKm), Modifier.weight(1f), unit = f.paceUnit)
                    StatTile(stringResource(R.string.run_avg_pace), f.paceValue(s.averagePaceSecPerKm), Modifier.weight(1f), unit = f.paceUnit)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.md)) {
                    StatTile(stringResource(R.string.run_current_speed), f.speedValue(s.currentSpeedKmh), Modifier.weight(1f), unit = f.speedUnit)
                    StatTile(stringResource(R.string.run_avg_speed), f.speedValue(s.averageSpeedKmh), Modifier.weight(1f), unit = f.speedUnit)
                    StatTile(stringResource(R.string.run_elevation_gain), f.elevation(s.elevationGainM), Modifier.weight(1f))
                }
            }
            if (s.splits.isNotEmpty()) {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.run_splits), style = MaterialTheme.typography.titleSmall)
                    s.splits.takeLast(5).forEach { sp ->
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text("${sp.index}", modifier = Modifier.width(32.dp))
                            Text(f.distance(sp.distanceM), modifier = Modifier.weight(1f))
                            Text(f.pace(sp.paceSecPerKm))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (s.status == TrackerStatus.PAUSED) {
                OutlinedButton(onClick = { confirmDiscard = true }, modifier = Modifier.weight(1f).height(56.dp)) { Text(stringResource(R.string.action_discard)) }
                Button(onClick = { viewModel.resume(context) }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Text(stringResource(R.string.action_resume))
                }
                Button(
                    onClick = { confirmFinish = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(Icons.Filled.Stop, null)
                    Text(stringResource(R.string.action_finish))
                }
            } else {
                FilledTonalButton(onClick = { viewModel.pause() }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Icon(Icons.Filled.Pause, null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.action_pause), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
    if (confirmDiscard) {
        ConfirmDialog(
            stringResource(R.string.run_discard_title), stringResource(R.string.run_discard_body), stringResource(R.string.action_discard),
            onConfirm = { viewModel.discard(onBack) }, onDismiss = { confirmDiscard = false }, destructive = true,
        )
    }
    if (confirmFinish) {
        ConfirmDialog(
            stringResource(R.string.run_finish_title), stringResource(R.string.run_finish_body), stringResource(R.string.action_finish),
            onConfirm = { viewModel.finish(onFinished) }, onDismiss = { confirmFinish = false },
        )
    }
}

@Composable
fun GpsIndicator(accuracyM: Float?, signal: GpsSignal, modifier: Modifier = Modifier) {
    val f = LocalFormatter.current
    val (color, icon, label) = when {
        signal == GpsSignal.LOST || signal == GpsSignal.NONE && accuracyM == null ->
            Triple(MaterialTheme.colorScheme.error, Icons.Filled.GpsOff, stringResource(if (signal == GpsSignal.LOST) R.string.gps_lost else R.string.gps_searching))
        signal == GpsSignal.WEAK || (accuracyM ?: 99f) > 25f -> Triple(TkdTheme.colors.warning, Icons.Filled.GpsNotFixed, stringResource(R.string.gps_weak))
        else -> Triple(TkdTheme.colors.success, Icons.Filled.GpsFixed, stringResource(R.string.gps_good))
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label + (accuracyM?.takeIf { it < 1000f }?.let { " · ±${f.integer(it.toInt())} m" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        )
    }
}
