package com.tkdpro.feature.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tkdpro.R
import com.tkdpro.core.database.dao.TechniqueRecordWithTechnique
import com.tkdpro.core.database.entity.SessionStatus
import com.tkdpro.core.database.entity.totalReps
import com.tkdpro.core.designsystem.component.ConfirmDialog
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.LoadingState
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.SectionHeader
import com.tkdpro.core.designsystem.component.StatTile
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.domain.load.TrainingLoad
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.TrainingRepository
import com.tkdpro.navigation.SessionDetailRoute
import com.tkdpro.navigation.SessionEditRoute
import com.tkdpro.navigation.TechnicalRecordingRoute
import com.tkdpro.navigation.TechniqueDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionDetailViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: TrainingRepository) : ViewModel() {
    private val id = savedState.toRoute<SessionDetailRoute>().id
    val session = repo.session(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markCompleted() = viewModelScope.launch {
        session.value?.session?.let { repo.saveSession(it.copy(status = SessionStatus.COMPLETED)) }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteSession(id)
        onDone()
    }

    fun deleteRecord(r: TechniqueRecordWithTechnique) = viewModelScope.launch { repo.deleteRecord(r.record) }
}

@Composable
fun SessionDetailScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: SessionDetailViewModel = hiltViewModel()) {
    val data by viewModel.session.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    var confirmDelete by remember { mutableStateOf(false) }
    val s = data?.session
    Scaffold(
        topBar = {
            TkdTopBar(s?.name ?: "", onBack) {
                if (s != null) {
                    IconButton(onClick = { onNavigate(SessionEditRoute(s.id)) }) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                }
            }
        },
    ) { padding ->
        if (s == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text("${stringResource(s.category.label())} · ${f.dateTime(s.startTime)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.location?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        StatTile(stringResource(R.string.label_duration), f.minutes(s.durationMin), Modifier.weight(1f))
                        StatTile("RPE", s.rpe?.toString() ?: "--", Modifier.weight(1f), provenance = Provenance.MANUAL)
                        StatTile(
                            stringResource(R.string.label_load),
                            TrainingLoad.sessionLoad(s.durationMin, s.rpe)?.let { f.integer(it) } ?: "--",
                            Modifier.weight(1f),
                            provenance = Provenance.ESTIMATE,
                        )
                    }
                    Text(stringResource(R.string.load_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                    s.difficulty?.let { Text(stringResource(R.string.session_difficulty_value, it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.xs)) }
                    if (s.notes.isNotBlank()) Text(s.notes, modifier = Modifier.padding(top = Spacing.sm))
                }
            }
            if (s.status == SessionStatus.PLANNED) {
                item {
                    Button(onClick = viewModel::markCompleted, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.session_mark_completed)) }
                }
            }
            item {
                SectionHeader(stringResource(R.string.session_techniques))
                OutlinedButton(onClick = { onNavigate(TechnicalRecordingRoute(s.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FiberManualRecord, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.session_record_technique), modifier = Modifier.padding(start = Spacing.sm))
                }
            }
            val records = data?.records.orEmpty()
            if (records.isEmpty()) {
                item { EmptyState(stringResource(R.string.session_no_techniques_title), stringResource(R.string.session_no_techniques_body)) }
            }
            items(records, key = { it.record.id }) { r ->
                TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(TechniqueDetailRoute(r.technique.id)) }, contentPadding = PaddingValues(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.technique.name, style = MaterialTheme.typography.titleSmall)
                            Text(recordSummary(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (r.record.notes.isNotBlank()) Text(r.record.notes, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { viewModel.deleteRecord(r) }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.session_delete_title),
            stringResource(R.string.session_delete_body),
            stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@Composable
private fun recordSummary(r: TechniqueRecordWithTechnique): String {
    val f = LocalFormatter.current
    val rec = r.record
    return buildList {
        add(stringResource(R.string.reps_value, f.integer(rec.totalReps())))
        if (rec.rightReps != null || rec.leftReps != null) {
            add("${stringResource(R.string.side_right_short)} ${rec.rightReps ?: 0} / ${stringResource(R.string.side_left_short)} ${rec.leftReps ?: 0}")
        } else {
            add(stringResource(rec.side.label()))
        }
        rec.sets?.let { add(stringResource(R.string.sets_value, it)) }
        rec.durationSec?.let { add(f.duration(it * 1000L)) }
    }.joinToString(" · ")
}
