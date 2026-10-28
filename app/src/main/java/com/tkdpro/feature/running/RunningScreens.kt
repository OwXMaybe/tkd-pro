package com.tkdpro.feature.running

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tkdpro.R
import com.tkdpro.core.database.entity.RunningSession
import com.tkdpro.core.designsystem.component.ConfirmDialog
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.IntField
import com.tkdpro.core.designsystem.component.LoadingState
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.ProvenanceBadge
import com.tkdpro.core.designsystem.component.RouteMap
import com.tkdpro.core.designsystem.component.RouteSketch
import com.tkdpro.core.designsystem.component.SectionHeader
import com.tkdpro.core.designsystem.component.StatTile
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.domain.run.Pace
import com.tkdpro.core.domain.run.PersonalRecord
import com.tkdpro.core.domain.run.RoutePoint
import com.tkdpro.core.domain.time.DateRange
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.RunningRepository
import com.tkdpro.data.repository.toRoutePoint
import com.tkdpro.feature.running.tracking.RunTracker
import com.tkdpro.navigation.LiveRunRoute
import com.tkdpro.navigation.RunSummaryRoute
import com.tkdpro.navigation.RunningAnalyticsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

// ---------- Running home ----------

@HiltViewModel
class RunningHomeViewModel @Inject constructor(repo: RunningRepository, tracker: RunTracker) : ViewModel() {
    val runs = repo.completedRuns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val records = repo.personalRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val live = tracker.state
}

@Composable
fun RunningHomeScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: RunningHomeViewModel = hiltViewModel()) {
    val runs by viewModel.runs.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    val zone = ZoneId.systemDefault()
    val week = DateRange.week(LocalDate.now(zone))
    val month = DateRange.month(LocalDate.now(zone))
    Scaffold(topBar = { TkdTopBar(stringResource(R.string.hub_running), onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item {
                Button(onClick = { onNavigate(LiveRunRoute) }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Text(stringResource(if (live.isRecording) R.string.run_return_live else R.string.action_start_run), style = MaterialTheme.typography.titleMedium)
                }
            }
            val list = runs
            if (list == null) {
                item { LoadingState() }
                return@LazyColumn
            }
            item {
                val inWeek = list.filter { it.startTime >= week.startMillis(zone) && it.startTime < week.endMillisExclusive(zone) }
                val inMonth = list.filter { it.startTime >= month.startMillis(zone) && it.startTime < month.endMillisExclusive(zone) }
                TkdCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.run_summary_period), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        ProvenanceBadge(Provenance.MEASURED)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
                        StatTile(stringResource(R.string.label_this_week), f.distanceValue(inWeek.sumOf { it.distanceM }), Modifier.weight(1f), unit = f.distanceUnit)
                        StatTile(stringResource(R.string.label_this_month), f.distanceValue(inMonth.sumOf { it.distanceM }), Modifier.weight(1f), unit = f.distanceUnit)
                        StatTile(stringResource(R.string.label_runs), list.size.toString(), Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = { onNavigate(RunningAnalyticsRoute) }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
                        Text(stringResource(R.string.run_analytics))
                    }
                }
            }
            if (records.isNotEmpty()) {
                item {
                    TkdCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.run_personal_records), style = MaterialTheme.typography.titleSmall)
                        records.forEach { pr -> PrRow(pr) { onNavigate(RunSummaryRoute(pr.runId)) } }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.run_history)) }
            if (list.isEmpty()) {
                item {
                    EmptyState(stringResource(R.string.run_empty_title), stringResource(R.string.run_empty_body), icon = Icons.AutoMirrored.Filled.DirectionsRun)
                }
            }
            items(list, key = { it.id }) { r -> RunRow(r) { onNavigate(RunSummaryRoute(r.id)) } }
        }
    }
}

@Composable
fun PrRow(pr: PersonalRecord, onClick: () -> Unit) {
    val f = LocalFormatter.current
    val value = when (pr.kind) {
        PersonalRecord.Kind.LONGEST_DISTANCE -> f.distance(pr.value)
        else -> f.duration(pr.value.toLong())
    }
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Text(stringResource(pr.kind.label()), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun RunRow(r: RunningSession, onClick: () -> Unit) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.customName ?: stringResource(r.type.label()), style = MaterialTheme.typography.titleSmall)
                Text(f.dateTime(r.startTime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(f.distance(r.distanceM), style = MaterialTheme.typography.titleSmall)
                Text("${f.duration(r.movingTimeMs)} · ${f.pace(Pace.secondsPerKm(r.distanceM, r.movingTimeMs))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------- Run summary ----------

@HiltViewModel
class RunSummaryViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: RunningRepository) : ViewModel() {
    private val id = savedState.toRoute<RunSummaryRoute>().id
    val run = repo.run(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val splits = repo.splits(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val points = MutableStateFlow<List<RoutePoint>?>(null)

    init {
        viewModelScope.launch { points.value = repo.points(id).map { it.toRoutePoint() } }
    }

    fun saveDetails(notes: String, avgHr: Int?, maxHr: Int?) = viewModelScope.launch {
        run.value?.let { repo.updateDetails(it.copy(notes = notes, avgHeartRate = avgHr, maxHeartRate = maxHr)) }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteRun(id)
        onDone()
    }
}

@Composable
fun RunSummaryScreen(onBack: () -> Unit, viewModel: RunSummaryViewModel = hiltViewModel()) {
    val run by viewModel.run.collectAsStateWithLifecycle()
    val splits by viewModel.splits.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val r = run
    Scaffold(
        topBar = {
            TkdTopBar(r?.let { it.customName ?: stringResource(it.type.label()) } ?: "", onBack) {
                if (r != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
            }
        },
    ) { padding ->
        if (r == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item {
                val pts = points.orEmpty()
                RouteMap(pts, Modifier.fillMaxWidth().height(260.dp))
                Text(stringResource(R.string.map_attribution_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.dateTime(r.startTime), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        ProvenanceBadge(Provenance.MEASURED)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
                        StatTile(stringResource(R.string.label_distance), f.distanceValue(r.distanceM), Modifier.weight(1f), unit = f.distanceUnit)
                        StatTile(stringResource(R.string.label_duration), f.duration(r.movingTimeMs), Modifier.weight(1f))
                        StatTile(stringResource(R.string.run_avg_pace), f.paceValue(Pace.secondsPerKm(r.distanceM, r.movingTimeMs)), Modifier.weight(1f), unit = f.paceUnit)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.md)) {
                        StatTile(stringResource(R.string.run_avg_speed), f.speedValue(Pace.kmh(r.distanceM, r.movingTimeMs)), Modifier.weight(1f), unit = f.speedUnit)
                        StatTile(stringResource(R.string.run_elevation_gain), f.elevation(r.elevationGainM), Modifier.weight(1f))
                        StatTile(stringResource(R.string.run_heart_rate), r.avgHeartRate?.let { "$it" } ?: "--", Modifier.weight(1f), unit = "bpm", provenance = r.avgHeartRate?.let { Provenance.MANUAL })
                    }
                    Text(stringResource(R.string.run_gps_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.run_splits), style = MaterialTheme.typography.titleSmall)
                    if (splits.isEmpty()) Text(stringResource(R.string.run_no_splits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    splits.forEach { sp ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text("${sp.idx}", modifier = Modifier.width(32.dp), style = MaterialTheme.typography.labelLarge)
                            Text(f.distance(sp.distanceM), modifier = Modifier.weight(1f))
                            Text(f.pace(Pace.secondsPerKm(sp.distanceM, sp.durationMs)), modifier = Modifier.weight(1f))
                            Text("+${f.elevation(sp.elevationGainM)}")
                        }
                    }
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth(), onClick = { editing = true }) {
                    Text(stringResource(R.string.label_notes), style = MaterialTheme.typography.titleSmall)
                    Text(r.notes.ifBlank { stringResource(R.string.run_add_notes_hint) }, color = if (r.notes.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            }
            if (points.isNullOrEmpty().not()) {
                item {
                    Text(stringResource(R.string.run_route_sketch), style = MaterialTheme.typography.labelLarge)
                    RouteSketch(points.orEmpty(), Modifier.fillMaxWidth().height(120.dp))
                }
            }
        }
    }
    if (editing && r != null) {
        var notes by remember { mutableStateOf(r.notes) }
        var avg by remember { mutableStateOf(r.avgHeartRate) }
        var max by remember { mutableStateOf(r.maxHeartRate) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.run_edit_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TkdTextField(notes, { notes = it.take(2000) }, stringResource(R.string.label_notes), singleLine = false, minLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        IntField(avg, { avg = it }, stringResource(R.string.run_avg_hr), Modifier.weight(1f), 30..250)
                        IntField(max, { max = it }, stringResource(R.string.run_max_hr), Modifier.weight(1f), 30..250)
                    }
                    Text(stringResource(R.string.run_hr_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.saveDetails(notes.trim(), avg, max); editing = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.run_delete_title), stringResource(R.string.run_delete_body), stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(onBack) }, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}
