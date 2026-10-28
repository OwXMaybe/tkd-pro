package com.tkdpro.feature.motion

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tkdpro.R
import com.tkdpro.core.database.entity.AnalysisEngine
import com.tkdpro.core.database.entity.TrainingTechnique
import com.tkdpro.core.database.entity.VideoAsset
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.SectionHeader
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.component.rememberVideoPicker
import com.tkdpro.core.designsystem.component.videoOnlyRequest
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.domain.motion.Side
import com.tkdpro.core.network.BackendError
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.data.motion.VideoTooLongException
import com.tkdpro.data.repository.MediaRepository
import com.tkdpro.data.repository.TrainingRepository
import com.tkdpro.navigation.MotionCompareRoute
import com.tkdpro.navigation.MotionReportRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface AnalysisProgress {
    data object Idle : AnalysisProgress
    data object Importing : AnalysisProgress
    data class Processing(val fraction: Float) : AnalysisProgress
    data class Failed(val message: Int) : AnalysisProgress
    data class Done(val analysisId: Long) : AnalysisProgress
}

@HiltViewModel
class MotionLabViewModel @Inject constructor(
    private val media: MediaRepository,
    training: TrainingRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val techniques = training.techniques.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val analyses = media.analyses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val engine = settings.settings.map { it.analysisEngine }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisEngine.ON_DEVICE)
    val video = MutableStateFlow<VideoAsset?>(null)
    val progress = MutableStateFlow<AnalysisProgress>(AnalysisProgress.Idle)

    fun import(uri: Uri, cleanup: File? = null) = viewModelScope.launch {
        progress.value = AnalysisProgress.Importing
        runCatching { media.importVideo(uri) }
            .onSuccess {
                video.value = it
                progress.value = AnalysisProgress.Idle
            }
            .onFailure { progress.value = AnalysisProgress.Failed(R.string.error_video_import) }
        cleanup?.delete()
    }

    fun analyze(technique: TrainingTechnique, side: Side?) = viewModelScope.launch {
        val v = video.value ?: return@launch
        progress.value = AnalysisProgress.Processing(0f)
        runCatching {
            media.analyze(v, technique.id, technique.name, side) { p -> progress.value = AnalysisProgress.Processing(p) }
        }.onSuccess { id ->
            progress.value = AnalysisProgress.Done(id)
            video.value = null
        }.onFailure { e ->
            progress.value = AnalysisProgress.Failed(
                when (e) {
                    is VideoTooLongException -> R.string.motion_error_too_long
                    is BackendError.NotConfigured -> R.string.motion_error_server_not_configured
                    is BackendError -> R.string.motion_error_server
                    else -> R.string.motion_error_generic
                },
            )
        }
    }

    fun consumeDone() {
        progress.value = AnalysisProgress.Idle
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotionLabScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: MotionLabViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val techniques by viewModel.techniques.collectAsStateWithLifecycle()
    val analyses by viewModel.analyses.collectAsStateWithLifecycle()
    val video by viewModel.video.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    var technique by remember { mutableStateOf<TrainingTechnique?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var side by remember { mutableStateOf<Side?>(null) }
    val selected = remember { mutableStateListOf<Long>() }

    val picker = rememberVideoPicker { viewModel.import(it) }
    var captureFile by remember { mutableStateOf<File?>(null) }
    val recorder = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val file = captureFile
        if (ok && file != null) viewModel.import(Uri.fromFile(file), cleanup = file) else file?.delete()
    }
    LaunchedEffect(progress) {
        (progress as? AnalysisProgress.Done)?.let {
            viewModel.consumeDone()
            onNavigate(MotionReportRoute(it.analysisId))
        }
    }

    Scaffold(topBar = { TkdTopBar(stringResource(R.string.hub_motion_lab), onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.motion_step_technique), style = MaterialTheme.typography.titleSmall)
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = technique?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.label_technique)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            techniques.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { technique = t; expanded = false }) }
                        }
                    }
                    Text(stringResource(R.string.motion_side), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
                    ChoiceChips(listOf(null, Side.RIGHT, Side.LEFT), side, { side = it }, {
                        when (it) {
                            null -> stringResource(R.string.motion_side_auto)
                            Side.RIGHT -> stringResource(R.string.side_right)
                            Side.LEFT -> stringResource(R.string.side_left)
                        }
                    })
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.motion_step_video), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.motion_recording_tips), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                        OutlinedButton(onClick = { picker.launch(videoOnlyRequest()) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.VideoLibrary, null)
                            Text(stringResource(R.string.motion_upload), modifier = Modifier.padding(start = Spacing.xs))
                        }
                        OutlinedButton(onClick = {
                            val dir = File(context.cacheDir, "captures").apply { mkdirs() }
                            val file = File(dir, "capture_${System.currentTimeMillis()}.mp4")
                            captureFile = file
                            recorder.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Videocam, null)
                            Text(stringResource(R.string.motion_record), modifier = Modifier.padding(start = Spacing.xs))
                        }
                    }
                    video?.let { v ->
                        Text(
                            stringResource(
                                R.string.motion_video_selected,
                                f.duration(v.durationMs ?: 0),
                                "${v.width ?: "?"}×${v.height ?: "?"}",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.motion_step_analyze), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (engine == AnalysisEngine.ON_DEVICE) R.string.motion_engine_device else R.string.motion_engine_server),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (val p = progress) {
                        is AnalysisProgress.Processing -> {
                            LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm))
                            Text(stringResource(R.string.motion_processing, (p.fraction * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                        }
                        is AnalysisProgress.Importing -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = Spacing.sm))
                        is AnalysisProgress.Failed -> Text(stringResource(p.message), color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                    val busy = progress is AnalysisProgress.Processing || progress is AnalysisProgress.Importing
                    Button(
                        onClick = { technique?.let { viewModel.analyze(it, side) } },
                        enabled = technique != null && video != null && !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    ) { Text(stringResource(R.string.motion_analyze)) }
                    ScreenMessage(R.string.motion_limits)
                }
            }
            item {
                SectionHeader(
                    stringResource(R.string.motion_history),
                    actionLabel = if (selected.size == 2) stringResource(R.string.motion_compare) else null,
                    onAction = if (selected.size == 2) ({ onNavigate(MotionCompareRoute(selected[0], selected[1])) }) else null,
                )
                if ((analyses?.size ?: 0) >= 2) ScreenMessage(R.string.motion_compare_hint)
            }
            val list = analyses.orEmpty()
            if (analyses != null && list.isEmpty()) item { EmptyState(stringResource(R.string.motion_empty_title), stringResource(R.string.motion_empty_body), icon = Icons.Filled.Videocam) }
            items(list, key = { it.id }) { a ->
                TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(MotionReportRoute(a.id)) }, contentPadding = PaddingValues(Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = a.id in selected,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (selected.size == 2) selected.removeAt(0)
                                    selected += a.id
                                } else {
                                    selected -= a.id
                                }
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(a.techniqueName, style = MaterialTheme.typography.titleSmall)
                            Text("${stringResource(a.status.label())} · ${f.dateTime(a.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (a.id in selected) Icon(Icons.AutoMirrored.Filled.CompareArrows, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
