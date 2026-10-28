package com.tkdpro.feature.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tkdpro.core.database.entity.BodySide
import com.tkdpro.core.database.entity.Combination
import com.tkdpro.core.database.entity.CombinationPractice
import com.tkdpro.core.database.entity.CombinationStep
import com.tkdpro.core.database.entity.TrainingTechnique
import com.tkdpro.core.database.entity.VideoAsset
import com.tkdpro.core.designsystem.component.ConfirmDialog
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.IntField
import com.tkdpro.core.designsystem.component.LoadingState
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
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.MediaRepository
import com.tkdpro.data.repository.TrainingRepository
import com.tkdpro.navigation.CombinationDetailRoute
import com.tkdpro.navigation.CombinationEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun sideShort(side: BodySide) = when (side) {
    BodySide.RIGHT -> R.string.side_right_short
    BodySide.LEFT -> R.string.side_left_short
    BodySide.BOTH -> R.string.side_both_short
}

// ---------- List ----------

@HiltViewModel
class CombinationsViewModel @Inject constructor(repo: TrainingRepository) : ViewModel() {
    val combinations = repo.combinations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun CombinationsScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: CombinationsViewModel = hiltViewModel()) {
    val list by viewModel.combinations.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TkdTopBar(stringResource(R.string.hub_combinations), onBack) },
        floatingActionButton = { FloatingActionButton(onClick = { onNavigate(CombinationEditRoute()) }) { Icon(Icons.Filled.Add, stringResource(R.string.combination_new)) } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val l = list
            if (l == null) item { LoadingState() }
            else if (l.isEmpty()) item {
                EmptyState(
                    stringResource(R.string.combination_empty_title),
                    stringResource(R.string.combination_empty_body),
                    icon = Icons.Filled.Link,
                    actionLabel = stringResource(R.string.combination_new),
                    onAction = { onNavigate(CombinationEditRoute()) },
                )
            }
            items(l.orEmpty(), key = { it.combination.id }) { c ->
                val sideLabels = BodySide.entries.associateWith { stringResource(sideShort(it)) }
                TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(CombinationDetailRoute(c.combination.id)) }, contentPadding = PaddingValues(Spacing.md)) {
                    Text(c.combination.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        c.steps.sortedBy { it.step.position }.joinToString(" → ") { "${it.technique.name} (${sideLabels[it.step.side]})" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.combination_practice_summary, c.practices.size, c.practices.sumOf { it.reps }),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------- Builder ----------

data class BuilderStep(val technique: TrainingTechnique, val side: BodySide)

@HiltViewModel
class CombinationEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TrainingRepository,
    private val media: MediaRepository,
) : ViewModel() {
    private val id = savedState.toRoute<CombinationEditRoute>().id
    val techniques = repo.techniques.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val name = MutableStateFlow("")
    val notes = MutableStateFlow("")
    val steps = MutableStateFlow<List<BuilderStep>>(emptyList())
    val videoAssetId = MutableStateFlow<Long?>(null)
    val savedId = MutableStateFlow<Long?>(null)
    val error = MutableStateFlow<Int?>(null)
    private var createdAt = 0L

    init {
        if (id != 0L) viewModelScope.launch {
            repo.combination(id).first()?.let { c ->
                name.value = c.combination.name
                notes.value = c.combination.notes
                videoAssetId.value = c.combination.videoAssetId
                createdAt = c.combination.createdAt
                steps.value = c.steps.sortedBy { it.step.position }.map { BuilderStep(it.technique, it.step.side) }
            }
        }
    }

    fun add(t: TrainingTechnique) { steps.value = steps.value + BuilderStep(t, BodySide.RIGHT) }
    fun remove(i: Int) { steps.value = steps.value.toMutableList().also { it.removeAt(i) } }
    fun setSide(i: Int, side: BodySide) { steps.value = steps.value.toMutableList().also { it[i] = it[i].copy(side = side) } }
    fun move(i: Int, delta: Int) {
        val target = i + delta
        val list = steps.value.toMutableList()
        if (target !in list.indices) return
        list.add(target, list.removeAt(i))
        steps.value = list
    }

    fun attachVideo(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { media.importVideo(uri, name.value) }
            .onSuccess { videoAssetId.value = it.id }
            .onFailure { error.value = R.string.error_video_import }
    }

    fun save() = viewModelScope.launch {
        if (name.value.isBlank() || steps.value.isEmpty()) return@launch
        savedId.value = repo.saveCombination(
            Combination(id = id, name = name.value.trim(), notes = notes.value.trim(), videoAssetId = videoAssetId.value, createdAt = createdAt, updatedAt = 0),
            steps.value.mapIndexed { i, s -> CombinationStep(combinationId = id, position = i, techniqueId = s.technique.id, side = s.side) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CombinationEditScreen(onBack: () -> Unit, onSaved: (Long) -> Unit, viewModel: CombinationEditViewModel = hiltViewModel()) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val techniques by viewModel.techniques.collectAsStateWithLifecycle()
    val videoId by viewModel.videoAssetId.collectAsStateWithLifecycle()
    val savedId by viewModel.savedId.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val picker = rememberVideoPicker { viewModel.attachVideo(it) }
    LaunchedEffect(savedId) { savedId?.let(onSaved) }

    Scaffold(topBar = { TkdTopBar(stringResource(R.string.combination_builder), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { TkdTextField(name, { viewModel.name.value = it.take(60) }, stringResource(R.string.combination_name)) }
            item {
                if (steps.isNotEmpty()) {
                    FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        steps.forEachIndexed { i, s ->
                            Text("${s.technique.name} (${stringResource(sideShort(s.side))})", style = MaterialTheme.typography.titleSmall)
                            if (i < steps.lastIndex) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(horizontal = 2.dp))
                        }
                    }
                }
            }
            itemsIndexed(steps) { i, s ->
                TkdCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${s.technique.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.move(i, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.action_move_up)) }
                        IconButton(onClick = { viewModel.move(i, 1) }, enabled = i < steps.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.action_move_down)) }
                        IconButton(onClick = { viewModel.remove(i) }) { Icon(Icons.Filled.Close, stringResource(R.string.action_remove)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(BodySide.RIGHT, BodySide.LEFT).forEach { side ->
                            FilterChip(selected = s.side == side, onClick = { viewModel.setSide(i, side) }, label = { Text(stringResource(side.label())) })
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text(stringResource(R.string.combination_add_technique), modifier = Modifier.padding(start = Spacing.sm))
                }
            }
            item { TkdTextField(notes, { viewModel.notes.value = it.take(1000) }, stringResource(R.string.label_notes), singleLine = false, minLines = 2) }
            item {
                OutlinedButton(onClick = { picker.launch(videoOnlyRequest()) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.VideoLibrary, null)
                    Text(
                        stringResource(if (videoId == null) R.string.combination_attach_demo else R.string.combination_demo_attached),
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
            item {
                Button(onClick = { viewModel.save() }, enabled = name.isNotBlank() && steps.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }

    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }) {
            LazyColumn(contentPadding = PaddingValues(Spacing.lg)) {
                items(techniques, key = { it.id }) { t ->
                    TextButton(onClick = { viewModel.add(t); picking = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(t.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ---------- Detail ----------

@HiltViewModel
class CombinationDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TrainingRepository,
    media: MediaRepository,
) : ViewModel() {
    private val id = savedState.toRoute<CombinationDetailRoute>().id
    val combination = repo.combination(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val video = combination.flatMapLatest { c -> c?.combination?.videoAssetId?.let { media.video(it) } ?: flowOf<VideoAsset?>(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun recordPractice(reps: Int, notes: String) = viewModelScope.launch {
        repo.recordPractice(CombinationPractice(combinationId = id, date = System.currentTimeMillis(), reps = reps, notes = notes, createdAt = 0))
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteCombination(id)
        onDone()
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CombinationDetailScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: CombinationDetailViewModel = hiltViewModel()) {
    val data by viewModel.combination.collectAsStateWithLifecycle()
    val video by viewModel.video.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    var practiceDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val c = data
    Scaffold(
        topBar = {
            TkdTopBar(c?.combination?.name ?: "", onBack) {
                if (c != null) {
                    IconButton(onClick = { onNavigate(CombinationEditRoute(c.combination.id)) }) { Icon(Icons.Filled.Edit, stringResource(R.string.action_edit)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                }
            }
        },
    ) { padding ->
        if (c == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        c.steps.sortedBy { it.step.position }.forEachIndexed { i, s ->
                            AssistChip(onClick = {}, label = { Text("${i + 1}. ${s.technique.name} · ${stringResource(sideShort(s.step.side))}") })
                        }
                    }
                    if (c.combination.notes.isNotBlank()) Text(c.combination.notes, modifier = Modifier.padding(top = Spacing.sm))
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        StatTile(stringResource(R.string.combination_practices), c.practices.size.toString(), Modifier.weight(1f))
                        StatTile(stringResource(R.string.label_total_reps), f.integer(c.practices.sumOf { it.reps }), Modifier.weight(1f))
                    }
                    Button(onClick = { practiceDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
                        Text(stringResource(R.string.combination_record_practice))
                    }
                }
            }
            video?.let { v ->
                item {
                    SectionHeader(stringResource(R.string.combination_demo_video))
                    VideoPlayer(v.path, aspectRatio = ((v.width ?: 9).toFloat() / (v.height ?: 16)).coerceIn(0.4f, 2f))
                }
            }
            item { SectionHeader(stringResource(R.string.combination_history)) }
            if (c.practices.isEmpty()) item { EmptyState(stringResource(R.string.combination_no_practice_title), stringResource(R.string.combination_no_practice_body)) }
            items(c.practices.sortedByDescending { it.date }, key = { it.id }) { p ->
                TkdCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(f.dateTime(p.date), style = MaterialTheme.typography.bodyMedium)
                            if (p.notes.isNotBlank()) Text(p.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(R.string.reps_value, f.integer(p.reps)), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
    if (practiceDialog) {
        var reps by remember { mutableStateOf<Int?>(null) }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { practiceDialog = false },
            title = { Text(stringResource(R.string.combination_record_practice)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    IntField(reps, { reps = it }, stringResource(R.string.label_reps), Modifier.fillMaxWidth(), 1..10_000)
                    TkdTextField(notes, { notes = it.take(500) }, stringResource(R.string.label_notes))
                }
            },
            confirmButton = {
                TextButton(enabled = (reps ?: 0) > 0, onClick = {
                    viewModel.recordPractice(reps ?: 0, notes.trim())
                    practiceDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { practiceDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.combination_delete_title),
            stringResource(R.string.combination_delete_body),
            stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}
