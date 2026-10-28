package com.tkdpro.feature.training

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tkdpro.core.database.entity.TechniqueCategory
import com.tkdpro.core.database.entity.TrainingTechnique
import com.tkdpro.core.database.entity.VideoAsset
import com.tkdpro.core.database.entity.totalReps
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.LoadingState
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.ProvenanceBadge
import com.tkdpro.core.designsystem.component.RatingSelector
import com.tkdpro.core.designsystem.component.SectionHeader
import com.tkdpro.core.designsystem.component.StatTile
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.component.VideoPlayer
import com.tkdpro.core.designsystem.component.rememberVideoPicker
import com.tkdpro.core.designsystem.component.videoOnlyRequest
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.builtInTechniqueDescription
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.DuplicateRecordException
import com.tkdpro.data.repository.MediaRepository
import com.tkdpro.data.repository.TrainingRepository
import com.tkdpro.navigation.SessionDetailRoute
import com.tkdpro.navigation.TechniqueDetailRoute
import com.tkdpro.navigation.TechniqueEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------- Library ----------

@HiltViewModel
class TechniqueLibraryViewModel @Inject constructor(repo: TrainingRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val category = MutableStateFlow<TechniqueCategory?>(null)
    val items = combine(repo.techniques, query, category) { list, q, c ->
        list.filter { (c == null || it.category == c) && (q.isBlank() || it.name.contains(q.trim(), ignoreCase = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun TechniqueLibraryScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: TechniqueLibraryViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TkdTopBar(stringResource(R.string.hub_technique_library), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate(TechniqueEditRoute()) }) { Icon(Icons.Filled.Add, stringResource(R.string.technique_add)) }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.query.value = it },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text(stringResource(R.string.action_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                )
            }
            item {
                ChoiceChips(
                    listOf<TechniqueCategory?>(null) + TechniqueCategory.entries,
                    category,
                    { viewModel.category.value = it },
                    { it?.let { c -> stringResource(c.label()) } ?: stringResource(R.string.filter_all) },
                )
            }
            val list = items
            if (list == null) item { LoadingState() }
            else if (list.isEmpty()) item { EmptyState(stringResource(R.string.technique_none_title), stringResource(R.string.technique_none_body)) }
            items(list.orEmpty(), key = { it.id }) { t ->
                TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(TechniqueDetailRoute(t.id)) }, contentPadding = PaddingValues(Spacing.md)) {
                    Text(t.name, style = MaterialTheme.typography.titleSmall)
                    val desc = builtInTechniqueDescription(t.builtInKey)?.let { stringResource(it) } ?: t.description
                    Text(
                        listOf(stringResource(t.category.label()), desc).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------- Detail ----------

@HiltViewModel
class TechniqueDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TrainingRepository,
    private val media: MediaRepository,
) : ViewModel() {
    private val id = savedState.toRoute<TechniqueDetailRoute>().id
    val technique = repo.technique(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history = repo.techniqueHistory(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val video = technique.flatMapLatest { t -> t?.videoAssetId?.let { media.video(it) } ?: flowOf<VideoAsset?>(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val error = MutableStateFlow<Int?>(null)

    fun attachVideo(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { media.importVideo(uri, technique.value?.name.orEmpty()) }
            .onSuccess { v -> technique.value?.let { repo.saveTechnique(it.copy(videoAssetId = v.id)) } }
            .onFailure { error.value = R.string.error_video_import }
    }
}

@Composable
fun TechniqueDetailScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: TechniqueDetailViewModel = hiltViewModel()) {
    val t by viewModel.technique.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val video by viewModel.video.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    val picker = rememberVideoPicker { viewModel.attachVideo(it) }
    val tech = t
    Scaffold(
        topBar = {
            TkdTopBar(tech?.name ?: "", onBack) {
                if (tech != null) IconButton(onClick = { onNavigate(TechniqueEditRoute(tech.id)) }) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit)) }
            }
        },
    ) { padding ->
        if (tech == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(tech.category.label()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    builtInTechniqueDescription(tech.builtInKey)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
                    if (tech.description.isNotBlank()) Text(tech.description)
                    tech.difficulty?.let { Text(stringResource(R.string.session_difficulty_value, it), style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                val totalReps = history.sumOf { it.record.totalReps() }
                val right = history.sumOf { it.record.rightReps ?: 0 }
                val left = history.sumOf { it.record.leftReps ?: 0 }
                TkdCard(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        StatTile(stringResource(R.string.label_total_reps), f.integer(totalReps), Modifier.weight(1f))
                        StatTile(stringResource(R.string.side_right), f.integer(right), Modifier.weight(1f))
                        StatTile(stringResource(R.string.side_left), f.integer(left), Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.technique_totals_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                }
            }
            item {
                SectionHeader(stringResource(R.string.technique_video_reference))
                val v = video
                if (v != null) VideoPlayer(v.path, aspectRatio = ((v.width ?: 9).toFloat() / (v.height ?: 16)).coerceIn(0.4f, 2f))
                OutlinedButton(onClick = { picker.launch(videoOnlyRequest()) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.VideoLibrary, null)
                    Text(stringResource(if (v == null) R.string.action_attach_video else R.string.action_replace_video), modifier = Modifier.padding(start = Spacing.sm))
                }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
            if (tech.notes.isNotBlank() || tech.coachFeedback.isNotBlank()) {
                item {
                    TkdCard(Modifier.fillMaxWidth()) {
                        if (tech.notes.isNotBlank()) {
                            Text(stringResource(R.string.label_personal_notes), style = MaterialTheme.typography.labelLarge)
                            Text(tech.notes)
                        }
                        if (tech.coachFeedback.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
                                Text(stringResource(R.string.label_coach_feedback), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                ProvenanceBadge(Provenance.MANUAL)
                            }
                            Text(tech.coachFeedback)
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.technique_history)) }
            if (history.isEmpty()) item { EmptyState(stringResource(R.string.technique_no_history_title), stringResource(R.string.technique_no_history_body)) }
            items(history, key = { it.record.id }) { h ->
                TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(SessionDetailRoute(h.record.sessionId)) }, contentPadding = PaddingValues(Spacing.md)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(h.sessionName, style = MaterialTheme.typography.titleSmall)
                            Text(f.date(h.sessionDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(R.string.reps_value, f.integer(h.record.totalReps())), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// ---------- Edit ----------

@HiltViewModel
class TechniqueEditViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: TrainingRepository) : ViewModel() {
    private val id = savedState.toRoute<TechniqueEditRoute>().id
    val technique = MutableStateFlow<TrainingTechnique?>(null)
    val error = MutableStateFlow<Int?>(null)
    val saved = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            technique.value = if (id == 0L) {
                TrainingTechnique(name = "", category = TechniqueCategory.KICK, createdAt = 0, updatedAt = 0)
            } else {
                repo.technique(id).first()
            }
        }
    }

    fun update(t: TrainingTechnique) {
        technique.value = t
        error.value = null
    }

    fun save() = viewModelScope.launch {
        val t = technique.value ?: return@launch
        if (t.name.isBlank()) return@launch
        try {
            repo.saveTechnique(t)
            saved.value = true
        } catch (_: DuplicateRecordException) {
            error.value = R.string.error_duplicate_technique
        }
    }
}

@Composable
fun TechniqueEditScreen(onBack: () -> Unit, viewModel: TechniqueEditViewModel = hiltViewModel()) {
    val t by viewModel.technique.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    LaunchedEffect(saved) { if (saved) onBack() }
    val tech = t
    Scaffold(topBar = { TkdTopBar(stringResource(if (tech?.id == 0L) R.string.technique_add else R.string.technique_edit), onBack) }) { padding ->
        if (tech == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Built-in technique names are fixed so statistics stay consistent; everything else is editable.
            if (tech.builtInKey == null) {
                TkdTextField(tech.name, { viewModel.update(tech.copy(name = it.take(60))) }, stringResource(R.string.technique_name))
                Text(stringResource(R.string.label_category), style = MaterialTheme.typography.labelLarge)
                ChoiceChips(TechniqueCategory.entries, tech.category, { viewModel.update(tech.copy(category = it)) }, { stringResource(it.label()) })
                TkdTextField(tech.description, { viewModel.update(tech.copy(description = it.take(500))) }, stringResource(R.string.label_description), singleLine = false, minLines = 2)
            } else {
                Text(tech.name, style = MaterialTheme.typography.titleLarge)
            }
            RatingSelector(stringResource(R.string.session_difficulty), tech.difficulty, 1..5, { viewModel.update(tech.copy(difficulty = it)) })
            TkdTextField(tech.notes, { viewModel.update(tech.copy(notes = it.take(2000))) }, stringResource(R.string.label_personal_notes), singleLine = false, minLines = 3)
            TkdTextField(tech.coachFeedback, { viewModel.update(tech.copy(coachFeedback = it.take(2000))) }, stringResource(R.string.label_coach_feedback_optional), singleLine = false, minLines = 3)
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = { viewModel.save() }, enabled = tech.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        }
    }
}
