package com.tkdpro.feature.match

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tkdpro.R
import com.tkdpro.core.database.entity.MatchEvent
import com.tkdpro.core.database.entity.MatchEventType
import com.tkdpro.core.database.entity.MatchRound
import com.tkdpro.core.database.entity.ScoringPreset
import com.tkdpro.core.database.entity.VideoAsset
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.ConfirmDialog
import com.tkdpro.core.designsystem.component.InlineEmpty
import com.tkdpro.core.designsystem.component.IntField
import com.tkdpro.core.designsystem.component.LoadingState
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.SectionHeader
import com.tkdpro.core.designsystem.component.StatTile
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.component.VideoController
import com.tkdpro.core.designsystem.component.VideoPlayer
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.MatchRepository
import com.tkdpro.data.repository.MatchTotals
import com.tkdpro.data.repository.MediaRepository
import com.tkdpro.navigation.MatchDetailRoute
import com.tkdpro.navigation.MatchEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Average attack success of earlier matches, for "compared with previous matches". */
data class MatchComparison(val previousCount: Int, val previousSuccessRate: Double?, val previousAvgAttempts: Double?)

@HiltViewModel
class MatchDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: MatchRepository,
    media: MediaRepository,
) : ViewModel() {
    private val id = savedState.toRoute<MatchDetailRoute>().id
    val match = repo.match(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val presets = repo.scoringPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val video = match.flatMapLatest { m -> m?.match?.videoAssetId?.let { media.video(it) } ?: flowOf<VideoAsset?>(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val comparison = combine(match, repo.matches) { current, all ->
        val c = current ?: return@combine null
        val earlier = all.filter { it.match.date < c.match.date }.take(5)
        val totals = earlier.map { MatchTotals.of(it.rounds) }
        MatchComparison(
            previousCount = earlier.size,
            previousSuccessRate = totals.mapNotNull { it.attackSuccessRate }.takeIf { it.isNotEmpty() }?.average(),
            previousAvgAttempts = totals.mapNotNull { it.attackAttempts?.toDouble() }.takeIf { it.isNotEmpty() }?.average(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveRound(r: MatchRound) = viewModelScope.launch { repo.saveRound(r.copy(matchId = id)) }
    fun deleteRound(r: MatchRound) = viewModelScope.launch { repo.deleteRound(r) }
    fun addEvent(e: MatchEvent) = viewModelScope.launch { repo.addEvent(e.copy(matchId = id)) }
    fun deleteEvent(e: MatchEvent) = viewModelScope.launch { repo.deleteEvent(e) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteMatch(id)
        onDone()
    }
}

@Composable
fun MatchDetailScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: MatchDetailViewModel = hiltViewModel()) {
    val data by viewModel.match.collectAsStateWithLifecycle()
    val video by viewModel.video.collectAsStateWithLifecycle()
    val comparison by viewModel.comparison.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    val controller = remember { VideoController() }
    var editingRound by remember { mutableStateOf<MatchRound?>(null) }
    var addingEvent by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val d = data
    Scaffold(
        topBar = {
            TkdTopBar(d?.match?.competitionName ?: "", onBack) {
                if (d != null) {
                    IconButton(onClick = { onNavigate(MatchEditRoute(d.match.id)) }) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                }
            }
        },
    ) { padding ->
        if (d == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val m = d.match
        val totals = MatchTotals.of(d.rounds)
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(m.result.label()), color = m.result.color(), style = MaterialTheme.typography.titleLarge)
                            Text(
                                listOf(f.date(m.date), m.category, m.stage, m.opponent.takeIf { it.isNotBlank() }?.let { "vs $it" } ?: "")
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (m.scoreFor != null && m.scoreAgainst != null) {
                            Text("${m.scoreFor}–${m.scoreAgainst}", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    m.durationSec?.let { Text(stringResource(R.string.match_duration_value, f.duration(it * 1000L)), style = MaterialTheme.typography.bodySmall) }
                    if (m.notes.isNotBlank()) Text(m.notes, modifier = Modifier.padding(top = Spacing.sm))
                }
            }
            item {
                ManualDataHeader(stringResource(R.string.match_summary))
                TkdCard(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        StatTile(stringResource(R.string.match_attacks), totals.attackAttempts?.toString() ?: "--", Modifier.weight(1f))
                        StatTile(stringResource(R.string.match_successful), totals.successfulAttacks?.toString() ?: "--", Modifier.weight(1f))
                        StatTile(stringResource(R.string.match_success_rate), totals.attackSuccessRate?.let { f.percent(it) } ?: "--", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
                        StatTile(stringResource(R.string.match_defensive), totals.defensiveActions?.toString() ?: "--", Modifier.weight(1f))
                        StatTile(stringResource(R.string.match_counters), totals.counterattacks?.toString() ?: "--", Modifier.weight(1f))
                        StatTile(stringResource(R.string.match_rounds), d.rounds.size.toString(), Modifier.weight(1f))
                    }
                }
            }
            comparison?.let { c ->
                item {
                    TkdCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.match_comparison), style = MaterialTheme.typography.titleSmall)
                        if (c.previousCount == 0) {
                            InlineEmpty(stringResource(R.string.match_comparison_none))
                        } else {
                            Text(
                                stringResource(
                                    R.string.match_comparison_body,
                                    c.previousCount,
                                    c.previousSuccessRate?.let { f.percent(it) } ?: "--",
                                    c.previousAvgAttempts?.let { f.number(it, 1) } ?: "--",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (m.strengths.isNotBlank() || m.improvements.isNotBlank()) {
                item {
                    TkdCard(Modifier.fillMaxWidth()) {
                        if (m.strengths.isNotBlank()) {
                            Text(stringResource(R.string.match_strengths), style = MaterialTheme.typography.labelLarge)
                            Text(m.strengths)
                        }
                        if (m.improvements.isNotBlank()) {
                            Text(stringResource(R.string.match_improvements), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
                            Text(m.improvements)
                        }
                    }
                }
            }
            video?.let { v ->
                item {
                    SectionHeader(stringResource(R.string.match_video))
                    VideoPlayer(v.path, aspectRatio = ((v.width ?: 16).toFloat() / (v.height ?: 9)).coerceIn(0.4f, 2f), controller = controller)
                }
            }
            item {
                SectionHeader(stringResource(R.string.match_rounds), actionLabel = stringResource(R.string.match_add_round), onAction = {
                    editingRound = MatchRound(matchId = m.id, number = (d.rounds.maxOfOrNull { it.number } ?: 0) + 1)
                })
            }
            if (d.rounds.isEmpty()) item { InlineEmpty(stringResource(R.string.match_no_rounds)) }
            items(d.rounds.sortedBy { it.number }, key = { it.id }) { r -> RoundCard(r, onEdit = { editingRound = r }, onDelete = { viewModel.deleteRound(r) }) }
            item {
                SectionHeader(stringResource(R.string.match_moments), actionLabel = stringResource(R.string.match_add_moment), onAction = { addingEvent = true })
            }
            if (d.events.isEmpty()) item { InlineEmpty(stringResource(R.string.match_no_moments)) }
            items(d.events.sortedWith(compareBy({ it.round ?: 0 }, { it.videoTimestampMs ?: 0 })), key = { it.id }) { e ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = e.videoTimestampMs != null) { e.videoTimestampMs?.let(controller::seekTo) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            buildString {
                                append(stringResource(e.type.label()))
                                e.points?.let { append(" · ").append(stringResource(R.string.points_value, it)) }
                                e.round?.let { append(" · R").append(it) }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val ts = e.videoTimestampMs?.let { f.duration(it) }
                        Text(listOfNotNull(ts, e.note.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.deleteEvent(e) }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                }
            }
        }
        editingRound?.let { r -> RoundDialog(r, onDismiss = { editingRound = null }, onSave = { viewModel.saveRound(it); editingRound = null }) }
        if (addingEvent) {
            EventDialog(
                presets = presets,
                rounds = d.rounds.map { it.number },
                currentVideoMs = { controller.currentPosition() },
                hasVideo = video != null,
                onDismiss = { addingEvent = false },
                onSave = { viewModel.addEvent(it); addingEvent = false },
            )
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.match_delete_title),
            stringResource(R.string.match_delete_body),
            stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

private fun VideoController.currentPosition(): Long? = player?.currentPosition

@Composable
private fun RoundCard(r: MatchRound, onEdit: () -> Unit, onDelete: () -> Unit) {
    TkdCard(Modifier.fillMaxWidth(), onClick = onEdit, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.match_round_n, r.number), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (r.pointsFor != null || r.pointsAgainst != null) Text("${r.pointsFor ?: "-"}–${r.pointsAgainst ?: "-"}", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
        }
        Text(
            listOfNotNull(
                r.attackAttempts?.let { stringResource(R.string.match_attacks) + " $it" },
                r.successfulAttacks?.let { stringResource(R.string.match_successful) + " $it" },
                r.defensiveActions?.let { stringResource(R.string.match_defensive) + " $it" },
                r.counterattacks?.let { stringResource(R.string.match_counters) + " $it" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (r.combinationsObserved.isNotBlank()) Text(stringResource(R.string.match_combinations_observed) + ": " + r.combinationsObserved, style = MaterialTheme.typography.bodySmall)
        if (r.tacticalNotes.isNotBlank()) Text(stringResource(R.string.match_tactical) + ": " + r.tacticalNotes, style = MaterialTheme.typography.bodySmall)
        if (r.timingDistanceNotes.isNotBlank()) Text(stringResource(R.string.match_timing) + ": " + r.timingDistanceNotes, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RoundDialog(initial: MatchRound, onDismiss: () -> Unit, onSave: (MatchRound) -> Unit) {
    var r by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.match_round_n, r.number)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    IntField(r.attackAttempts, { r = r.copy(attackAttempts = it) }, stringResource(R.string.match_attacks), Modifier.weight(1f), 0..999)
                    IntField(r.successfulAttacks, { r = r.copy(successfulAttacks = it) }, stringResource(R.string.match_successful), Modifier.weight(1f), 0..999)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    IntField(r.defensiveActions, { r = r.copy(defensiveActions = it) }, stringResource(R.string.match_defensive), Modifier.weight(1f), 0..999)
                    IntField(r.counterattacks, { r = r.copy(counterattacks = it) }, stringResource(R.string.match_counters), Modifier.weight(1f), 0..999)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    IntField(r.pointsFor, { r = r.copy(pointsFor = it) }, stringResource(R.string.match_score_for), Modifier.weight(1f), 0..999)
                    IntField(r.pointsAgainst, { r = r.copy(pointsAgainst = it) }, stringResource(R.string.match_score_against), Modifier.weight(1f), 0..999)
                }
                TkdTextField(r.combinationsObserved, { r = r.copy(combinationsObserved = it.take(500)) }, stringResource(R.string.match_combinations_observed), singleLine = false)
                TkdTextField(r.tacticalNotes, { r = r.copy(tacticalNotes = it.take(500)) }, stringResource(R.string.match_tactical), singleLine = false)
                TkdTextField(r.timingDistanceNotes, { r = r.copy(timingDistanceNotes = it.take(500)) }, stringResource(R.string.match_timing), singleLine = false)
                val invalid = (r.successfulAttacks ?: 0) > (r.attackAttempts ?: Int.MAX_VALUE)
                if (invalid) Text(stringResource(R.string.match_success_exceeds), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            val invalid = (r.successfulAttacks ?: 0) > (r.attackAttempts ?: Int.MAX_VALUE)
            TextButton(enabled = !invalid, onClick = { onSave(r) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EventDialog(
    presets: List<ScoringPreset>,
    rounds: List<Int>,
    currentVideoMs: () -> Long?,
    hasVideo: Boolean,
    onDismiss: () -> Unit,
    onSave: (MatchEvent) -> Unit,
) {
    var type by remember { mutableStateOf(MatchEventType.ATTACK) }
    var round by remember { mutableStateOf(rounds.maxOrNull()) }
    var points by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    var ts by remember { mutableStateOf(if (hasVideo) currentVideoMs() else null) }
    val f = LocalFormatter.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.match_add_moment)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ChoiceChips(MatchEventType.entries, type, { type = it }, { stringResource(it.label()) })
                if (rounds.isNotEmpty()) ChoiceChips(rounds, round, { round = it }, { "R$it" })
                if (type == MatchEventType.POINT_SCORED || type == MatchEventType.POINT_CONCEDED) {
                    if (presets.isNotEmpty()) ChoiceChips(presets, presets.firstOrNull { it.points == points }, { points = it.points }, { "${it.label} (${it.points})" })
                    IntField(points, { points = it }, stringResource(R.string.label_points), Modifier.fillMaxWidth(), 0..20)
                }
                TkdTextField(note, { note = it.take(300) }, stringResource(R.string.label_note))
                if (hasVideo) {
                    OutlinedButton(onClick = { ts = currentVideoMs() }) {
                        Icon(Icons.Filled.Add, null)
                        Text(stringResource(R.string.match_use_video_time, ts?.let { f.duration(it) } ?: "--"))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(MatchEvent(matchId = 0, round = round, videoTimestampMs = ts, type = type, points = points, note = note.trim()))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
