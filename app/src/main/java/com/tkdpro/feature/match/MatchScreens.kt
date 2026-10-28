package com.tkdpro.feature.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.tkdpro.core.database.entity.Match
import com.tkdpro.core.database.entity.MatchResult
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.DateField
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.IntField
import com.tkdpro.core.designsystem.component.LoadingState
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.ProvenanceBadge
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.StatTile
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.component.rememberVideoPicker
import com.tkdpro.core.designsystem.component.videoOnlyRequest
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.DuplicateRecordException
import com.tkdpro.data.repository.MatchRepository
import com.tkdpro.data.repository.MediaRepository
import com.tkdpro.navigation.MatchDetailRoute
import com.tkdpro.navigation.MatchEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun MatchResult.color() = when (this) {
    MatchResult.WIN -> TkdTheme.colors.success
    MatchResult.LOSS -> MaterialTheme.colorScheme.error
    MatchResult.DRAW -> TkdTheme.colors.warning
    MatchResult.NOT_RECORDED -> MaterialTheme.colorScheme.onSurfaceVariant
}

// ---------- List ----------

@HiltViewModel
class MatchesViewModel @Inject constructor(repo: MatchRepository) : ViewModel() {
    val matches = repo.matches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun MatchesScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: MatchesViewModel = hiltViewModel()) {
    val list by viewModel.matches.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    Scaffold(
        topBar = { TkdTopBar(stringResource(R.string.hub_matches), onBack) },
        floatingActionButton = { FloatingActionButton(onClick = { onNavigate(MatchEditRoute()) }) { Icon(Icons.Filled.Add, stringResource(R.string.match_new)) } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val l = list
            if (l == null) item { LoadingState() }
            else if (l.isEmpty()) item {
                EmptyState(
                    stringResource(R.string.match_empty_title),
                    stringResource(R.string.match_empty_body),
                    icon = Icons.Filled.SportsKabaddi,
                    actionLabel = stringResource(R.string.match_new),
                    onAction = { onNavigate(MatchEditRoute()) },
                )
            } else item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        StatTile(stringResource(R.string.label_matches), l.size.toString(), Modifier.weight(1f))
                        StatTile(stringResource(R.string.result_win), l.count { it.match.result == MatchResult.WIN }.toString(), Modifier.weight(1f))
                        StatTile(stringResource(R.string.result_loss), l.count { it.match.result == MatchResult.LOSS }.toString(), Modifier.weight(1f))
                        StatTile(stringResource(R.string.result_draw), l.count { it.match.result == MatchResult.DRAW }.toString(), Modifier.weight(1f))
                    }
                }
            }
            items(l.orEmpty(), key = { it.match.id }) { m ->
                TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(MatchDetailRoute(m.match.id)) }, contentPadding = PaddingValues(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.match.competitionName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOf(f.date(m.match.date), m.match.stage, m.match.opponent.takeIf { it.isNotBlank() }?.let { "vs $it" } ?: "")
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(m.match.result.label()), color = m.match.result.color(), style = MaterialTheme.typography.labelLarge)
                            if (m.match.scoreFor != null && m.match.scoreAgainst != null) {
                                Text("${m.match.scoreFor}–${m.match.scoreAgainst}", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Edit ----------

@HiltViewModel
class MatchEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: MatchRepository,
    private val media: MediaRepository,
) : ViewModel() {
    private val id = savedState.toRoute<MatchEditRoute>().id
    val match = MutableStateFlow<Match?>(null)
    val error = MutableStateFlow<Int?>(null)
    val savedId = MutableStateFlow<Long?>(null)
    val importing = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            match.value = if (id == 0L) {
                Match(competitionName = "", date = System.currentTimeMillis(), createdAt = 0, updatedAt = 0)
            } else {
                repo.match(id).first()?.match
            }
        }
    }

    fun update(block: (Match) -> Match) {
        match.update { it?.let(block) }
        error.value = null
    }

    fun attachVideo(uri: android.net.Uri) = viewModelScope.launch {
        importing.value = true
        runCatching { media.importVideo(uri, match.value?.competitionName.orEmpty()) }
            .onSuccess { v -> update { it.copy(videoAssetId = v.id) } }
            .onFailure { error.value = R.string.error_video_import }
        importing.value = false
    }

    fun save() = viewModelScope.launch {
        val m = match.value ?: return@launch
        if (m.competitionName.isBlank()) return@launch
        try {
            savedId.value = repo.saveMatch(m.copy(competitionName = m.competitionName.trim(), opponent = m.opponent.trim()))
        } catch (_: DuplicateRecordException) {
            error.value = R.string.error_duplicate_match
        }
    }
}

@Composable
fun MatchEditScreen(onBack: () -> Unit, onSaved: (Long, Boolean) -> Unit, viewModel: MatchEditViewModel = hiltViewModel()) {
    val m by viewModel.match.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val savedId by viewModel.savedId.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val picker = rememberVideoPicker { viewModel.attachVideo(it) }
    LaunchedEffect(savedId) { savedId?.let { onSaved(it, m?.id == 0L) } }
    val match = m
    Scaffold(topBar = { TkdTopBar(stringResource(if (match?.id == 0L) R.string.match_new else R.string.match_edit), onBack) }) { padding ->
        if (match == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            TkdTextField(match.competitionName, { v -> viewModel.update { it.copy(competitionName = v.take(80)) } }, stringResource(R.string.match_competition))
            DateField(stringResource(R.string.label_date), match.date, { v -> viewModel.update { it.copy(date = v) } }, Modifier.fillMaxWidth())
            TkdTextField(match.opponent, { v -> viewModel.update { it.copy(opponent = v.take(60)) } }, stringResource(R.string.match_opponent))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TkdTextField(match.category, { v -> viewModel.update { it.copy(category = v.take(40)) } }, stringResource(R.string.match_category), Modifier.weight(1f))
                TkdTextField(match.stage, { v -> viewModel.update { it.copy(stage = v.take(40)) } }, stringResource(R.string.match_stage), Modifier.weight(1f))
            }
            Text(stringResource(R.string.match_result), style = MaterialTheme.typography.labelLarge)
            ChoiceChips(MatchResult.entries, match.result, { r -> viewModel.update { it.copy(result = r) } }, { stringResource(it.label()) })
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                IntField(match.scoreFor, { v -> viewModel.update { it.copy(scoreFor = v) } }, stringResource(R.string.match_score_for), Modifier.weight(1f), 0..999)
                IntField(match.scoreAgainst, { v -> viewModel.update { it.copy(scoreAgainst = v) } }, stringResource(R.string.match_score_against), Modifier.weight(1f), 0..999)
            }
            IntField(match.durationSec, { v -> viewModel.update { it.copy(durationSec = v) } }, stringResource(R.string.match_duration_sec), Modifier.fillMaxWidth(), 0..7200)
            TkdTextField(match.strengths, { v -> viewModel.update { it.copy(strengths = v.take(1000)) } }, stringResource(R.string.match_strengths), singleLine = false, minLines = 2)
            TkdTextField(match.improvements, { v -> viewModel.update { it.copy(improvements = v.take(1000)) } }, stringResource(R.string.match_improvements), singleLine = false, minLines = 2)
            TkdTextField(match.notes, { v -> viewModel.update { it.copy(notes = v.take(2000)) } }, stringResource(R.string.label_notes), singleLine = false, minLines = 3)
            OutlinedButton(onClick = { picker.launch(videoOnlyRequest()) }, enabled = !importing, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.VideoLibrary, null)
                Text(
                    stringResource(if (match.videoAssetId == null) R.string.action_attach_video else R.string.action_replace_video),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            ScreenMessage(R.string.match_manual_note)
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = { viewModel.save() }, enabled = match.competitionName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
fun ManualDataHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        ProvenanceBadge(Provenance.MANUAL)
    }
}
