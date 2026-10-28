package com.tkdpro.feature.motion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tkdpro.R
import com.tkdpro.core.database.entity.AnalysisStatus
import com.tkdpro.core.database.entity.MotionAnalysis
import com.tkdpro.core.database.entity.VideoAsset
import com.tkdpro.core.designsystem.component.ChartPoint
import com.tkdpro.core.designsystem.component.ConfirmDialog
import com.tkdpro.core.designsystem.component.LineChart
import com.tkdpro.core.designsystem.component.LoadingState
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.ProvenanceBadge
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.SectionHeader
import com.tkdpro.core.designsystem.component.StatTile
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.component.UnavailableNotice
import com.tkdpro.core.designsystem.component.VideoController
import com.tkdpro.core.designsystem.component.VideoPlayer
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.domain.motion.MetricDelta
import com.tkdpro.core.domain.motion.MetricStats
import com.tkdpro.core.domain.motion.MotionAnalyzer
import com.tkdpro.core.domain.motion.MotionComparison
import com.tkdpro.core.domain.motion.MotionResult
import com.tkdpro.core.domain.motion.PoseFrame
import com.tkdpro.core.domain.motion.Side
import com.tkdpro.core.domain.motion.SideSummary
import com.tkdpro.core.network.BackendError
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.AiNotPermittedException
import com.tkdpro.data.repository.MediaRepository
import com.tkdpro.navigation.MotionReportRoute
import com.tkdpro.navigation.SettingsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportData(val analysis: MotionAnalysis, val result: MotionResult?, val video: VideoAsset?, val poses: List<PoseFrame>)

sealed interface AiState {
    data object Idle : AiState
    data object Loading : AiState
    data class Error(val message: Int) : AiState
}

@HiltViewModel
class MotionReportViewModel @Inject constructor(savedState: SavedStateHandle, private val media: MediaRepository) : ViewModel() {
    private val id = savedState.toRoute<MotionReportRoute>().id
    val data = MutableStateFlow<ReportData?>(null)
    val ai = MutableStateFlow<AiState>(AiState.Idle)

    init {
        viewModelScope.launch {
            media.analysis(id).filterNotNull().collect { a ->
                val prev = data.value
                data.value = ReportData(
                    analysis = a,
                    result = media.decodeResult(a),
                    video = prev?.video ?: media.videoById(a.videoAssetId),
                    poses = prev?.poses ?: media.loadPoses(a.posePath),
                )
            }
        }
    }

    fun interpret() = viewModelScope.launch {
        ai.value = AiState.Loading
        media.requestInterpretation(id)
            .onSuccess { ai.value = AiState.Idle }
            .onFailure { e ->
                ai.value = AiState.Error(
                    when (e) {
                        is AiNotPermittedException -> R.string.ai_not_permitted
                        is BackendError.NotConfigured -> R.string.ai_not_configured
                        is BackendError.ServiceUnavailable -> R.string.ai_service_unavailable
                        is BackendError.Unauthorized -> R.string.ai_unauthorized
                        is BackendError.Network -> R.string.ai_network_error
                        else -> R.string.ai_generic_error
                    },
                )
            }
    }

    fun saveNotes(notes: String) = viewModelScope.launch { media.saveNotes(id, notes) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        val videoId = data.value?.analysis?.videoAssetId
        media.deleteAnalysis(id)
        // The source video belongs to this analysis only; remove it too.
        videoId?.let { media.deleteVideo(it) }
        onDone()
    }
}

@Composable
fun MotionReportScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: MotionReportViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val ai by viewModel.ai.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    val controller = remember { VideoController() }
    var confirmDelete by remember { mutableStateOf(false) }
    val d = data
    Scaffold(
        topBar = {
            TkdTopBar(d?.analysis?.techniqueName ?: "", onBack) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
            }
        },
    ) { padding ->
        if (d == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val r = d.result
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item {
                Text("${stringResource(d.analysis.status.label())} · ${f.dateTime(d.analysis.createdAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProvenanceLegend()
            }
            d.video?.let { v ->
                item {
                    VideoPlayer(
                        v.path,
                        aspectRatio = ((v.width ?: 9).toFloat() / (v.height ?: 16)).coerceIn(0.4f, 2f),
                        poses = if (d.analysis.status == AnalysisStatus.COMPLETED) d.poses else emptyList(),
                        controller = controller,
                    )
                    ScreenMessage(R.string.motion_overlay_note)
                }
            }
            if (r != null) {
                item { QualityCard(r) }
                if (r.quality.passed) {
                    item { SummaryCard(r) }
                    if (r.keyframes.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.motion_keyframes))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(r.keyframes) { k ->
                                    AssistChip(onClick = { controller.seekTo(k.timestampMs) }, label = {
                                        Text("#${k.repetition} ${keyframeLabel(k.label)} · ${f.duration(k.timestampMs)}")
                                    })
                                }
                            }
                        }
                    }
                    items(r.sides) { s -> SideCard(s) }
                    val lr = MotionComparison.leftRight(r)
                    if (lr.isNotEmpty()) item { DeltaCard(stringResource(R.string.motion_left_vs_right), lr, stringResource(R.string.side_left), stringResource(R.string.side_right)) }
                    item { TimelineCard(r) }
                }
            } else {
                item { Text(stringResource(R.string.motion_result_unreadable), color = MaterialTheme.colorScheme.error) }
            }
            item { AiCard(d.analysis, ai, onRequest = viewModel::interpret, onSettings = { onNavigate(SettingsRoute) }) }
            item {
                var notes by remember(d.analysis.id) { mutableStateOf(d.analysis.notes) }
                TkdTextField(notes, { notes = it.take(2000) }, stringResource(R.string.label_notes), singleLine = false, minLines = 2)
                OutlinedButton(onClick = { viewModel.saveNotes(notes.trim()) }, enabled = notes != d.analysis.notes) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.motion_delete_title), stringResource(R.string.motion_delete_body), stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(onBack) }, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}

@Composable
private fun ProvenanceLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
        ProvenanceBadge(Provenance.MEASURED)
        Text(stringResource(R.string.legend_measured), style = MaterialTheme.typography.labelSmall)
        ProvenanceBadge(Provenance.ESTIMATE)
        Text(stringResource(R.string.legend_estimate), style = MaterialTheme.typography.labelSmall)
        ProvenanceBadge(Provenance.AI)
        Text(stringResource(R.string.legend_ai), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun keyframeLabel(label: String) = stringResource(
    when (label) {
        MotionAnalyzer.KF_START -> R.string.kf_start
        MotionAnalyzer.KF_PEAK -> R.string.kf_peak
        else -> R.string.kf_end
    },
)

@Composable
private fun QualityCard(r: MotionResult) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.motion_quality), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            ProvenanceBadge(Provenance.MEASURED)
        }
        Text(
            stringResource(R.string.motion_video_facts, f.duration(r.video.durationMs), "${r.video.width}×${r.video.height}", f.number(r.video.sampledFps, 0), r.video.sampledFrames),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(R.string.motion_detection_facts, f.percent(r.quality.poseDetectionRate), f.percent(r.quality.meanKeyLandmarkVisibility)),
            style = MaterialTheme.typography.bodySmall,
        )
        if (!r.quality.passed) {
            Text(stringResource(R.string.motion_cannot_analyze), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
            r.quality.blocking.forEach { Text("• " + stringResource(it.label()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.motion_quality_advice), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
        }
        if (r.quality.warnings.isNotEmpty()) {
            Text(stringResource(R.string.motion_quality_warnings), color = TkdTheme.colors.warning, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
            r.quality.warnings.forEach { Text("• " + stringResource(it.label()), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SummaryCard(r: MotionResult) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.motion_summary), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            ProvenanceBadge(Provenance.ESTIMATE)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
            StatTile(stringResource(R.string.motion_repetitions), r.repetitions.size.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.motion_hip_sway), r.hipSwayStd?.let { f.number(it, 2) } ?: "--", Modifier.weight(1f), caption = stringResource(R.string.unit_torso_lengths))
        }
        r.notes.forEach { n ->
            Text(
                stringResource(if (n == MotionAnalyzer.NOTE_NO_REPETITIONS) R.string.motion_note_no_reps else R.string.motion_note_single_rep),
                style = MaterialTheme.typography.bodySmall,
                color = TkdTheme.colors.warning,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun SideCard(s: SideSummary) {
    val sideName = stringResource(if (s.side == Side.LEFT.name) R.string.side_left else R.string.side_right)
    TkdCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.motion_side_metrics, sideName, s.repetitions), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            ProvenanceBadge(Provenance.ESTIMATE)
        }
        MetricRow(stringResource(R.string.metric_peak_ankle), s.peakAnkleHeight, 2, stringResource(R.string.unit_torso_lengths))
        MetricRow(stringResource(R.string.metric_kick_knee), s.kickKneeAngleAtPeak, 0, "°")
        MetricRow(stringResource(R.string.metric_support_knee), s.supportKneeAngleAtPeak, 0, "°")
        MetricRow(stringResource(R.string.metric_trunk_lean), s.trunkLeanAtPeak, 0, "°")
        MetricRow(stringResource(R.string.metric_time_to_peak), s.timeToPeakMs, 0, "ms")
        Text(stringResource(R.string.motion_cv_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
    }
}

@Composable
private fun MetricRow(label: String, stats: MetricStats?, decimals: Int, unit: String) {
    val f = LocalFormatter.current
    Row(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (stats == null) {
            Text("--")
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text("${f.number(stats.mean, decimals)} $unit", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${f.number(stats.min, decimals)}–${f.number(stats.max, decimals)}" + (stats.cv?.let { " · CV ${f.percent(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun metricLabel(metric: String): String = stringResource(
    when (metric) {
        MotionComparison.PEAK_ANKLE_HEIGHT -> R.string.metric_peak_ankle
        MotionComparison.KICK_KNEE_ANGLE -> R.string.metric_kick_knee
        MotionComparison.SUPPORT_KNEE_ANGLE -> R.string.metric_support_knee
        MotionComparison.TRUNK_LEAN -> R.string.metric_trunk_lean
        else -> R.string.metric_time_to_peak
    },
)

@Composable
fun DeltaCard(title: String, deltas: List<MetricDelta>, beforeLabel: String, afterLabel: String) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            ProvenanceBadge(Provenance.ESTIMATE)
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            Text("", modifier = Modifier.weight(1.4f))
            Text(beforeLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text(afterLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text("Δ", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium)
        }
        deltas.forEach { d ->
            val decimals = if (d.metric == MotionComparison.PEAK_ANKLE_HEIGHT) 2 else 0
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(metricLabel(d.metric) + if (d.side == Side.LEFT.name || d.side == Side.RIGHT.name) " (${d.side.first()})" else "", modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall)
                Text(f.number(d.before, decimals), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(f.number(d.after, decimals), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text((if (d.delta >= 0) "+" else "") + f.number(d.delta, decimals), modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
            }
        }
        ScreenMessage(R.string.motion_compare_note)
    }
}

@Composable
private fun TimelineCard(r: MotionResult) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.motion_timeline), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            ProvenanceBadge(Provenance.ESTIMATE)
        }
        val step = (r.timeline.size / 60).coerceAtLeast(1)
        val sampled = r.timeline.filterIndexed { i, _ -> i % step == 0 }
        Text(stringResource(R.string.motion_timeline_right_ankle), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.sm))
        LineChart(sampled.mapNotNull { p -> p.rightAnkleHeight?.let { ChartPoint(f.duration(p.timestampMs), it) } }, height = 140.dp)
        Text(stringResource(R.string.motion_timeline_left_ankle), style = MaterialTheme.typography.labelMedium)
        LineChart(sampled.mapNotNull { p -> p.leftAnkleHeight?.let { ChartPoint(f.duration(p.timestampMs), it) } }, height = 140.dp, color = TkdTheme.colors.success)
        Text(stringResource(R.string.motion_timeline_trunk), style = MaterialTheme.typography.labelMedium)
        LineChart(sampled.mapNotNull { p -> p.trunkLean?.let { ChartPoint(f.duration(p.timestampMs), it) } }, height = 140.dp, color = TkdTheme.colors.warning)
    }
}

@Composable
private fun AiCard(a: MotionAnalysis, state: AiState, onRequest: () -> Unit, onSettings: () -> Unit) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = TkdTheme.colors.ai)
            Text(stringResource(R.string.motion_ai_interpretation), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = Spacing.sm))
            ProvenanceBadge(Provenance.AI)
        }
        val text = a.aiInterpretation
        if (text != null) {
            Text(text, modifier = Modifier.padding(top = Spacing.sm))
            a.aiGeneratedAt?.let { Text(stringResource(R.string.ai_generated_on, f.date(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            ScreenMessage(R.string.ai_disclaimer)
        } else {
            Text(stringResource(R.string.motion_ai_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (state) {
            AiState.Loading -> CircularProgressIndicator(Modifier.padding(top = Spacing.sm))
            is AiState.Error -> {
                UnavailableNotice(stringResource(R.string.ai_unavailable_title), stringResource(state.message), Modifier.padding(top = Spacing.sm),
                    actionLabel = stringResource(R.string.action_open_settings), onAction = onSettings)
            }
            AiState.Idle -> if (a.status == AnalysisStatus.COMPLETED) {
                Button(onClick = onRequest, modifier = Modifier.padding(top = Spacing.sm)) {
                    Text(stringResource(if (text == null) R.string.motion_ai_request else R.string.motion_ai_regenerate))
                }
            }
        }
    }
}

// ---------- Compare ----------

@HiltViewModel
class MotionCompareViewModel @Inject constructor(savedState: SavedStateHandle, private val media: MediaRepository) : ViewModel() {
    private val route = savedState.toRoute<com.tkdpro.navigation.MotionCompareRoute>()
    val pair = MutableStateFlow<Pair<ReportData, ReportData>?>(null)

    init {
        viewModelScope.launch {
            suspend fun load(id: Long): ReportData? {
                val a = media.analysis(id).first() ?: return null
                return ReportData(a, media.decodeResult(a), media.videoById(a.videoAssetId), emptyList())
            }
            val first = load(route.first)
            val second = load(route.second)
            if (first != null && second != null) {
                // Older recording first, so deltas read as change over time.
                pair.value = if (first.analysis.createdAt <= second.analysis.createdAt) first to second else second to first
            }
        }
    }
}

@Composable
fun MotionCompareScreen(onBack: () -> Unit, viewModel: MotionCompareViewModel = hiltViewModel()) {
    val pair by viewModel.pair.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    Scaffold(topBar = { TkdTopBar(stringResource(R.string.motion_compare), onBack) }) { padding ->
        val p = pair
        if (p == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val (a, b) = p
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(a, b).forEach { d ->
                        Column(Modifier.weight(1f)) {
                            Text(d.analysis.techniqueName, style = MaterialTheme.typography.titleSmall)
                            Text(f.date(d.analysis.createdAt), style = MaterialTheme.typography.bodySmall)
                            d.video?.let { v -> VideoPlayer(v.path, aspectRatio = ((v.width ?: 9).toFloat() / (v.height ?: 16)).coerceIn(0.4f, 2f)) }
                        }
                    }
                }
            }
            if (a.analysis.techniqueName != b.analysis.techniqueName) {
                item { Text(stringResource(R.string.motion_compare_different_technique), color = TkdTheme.colors.warning) }
            }
            val ra = a.result
            val rb = b.result
            if (ra == null || rb == null || !ra.quality.passed || !rb.quality.passed) {
                item { Text(stringResource(R.string.motion_compare_unavailable), color = MaterialTheme.colorScheme.error) }
            } else {
                val deltas = MotionComparison.compare(ra, rb)
                if (deltas.isEmpty()) item { Text(stringResource(R.string.motion_compare_no_shared)) }
                else item { DeltaCard(stringResource(R.string.motion_compare_metrics), deltas, f.shortDate(a.analysis.createdAt), f.shortDate(b.analysis.createdAt)) }
            }
        }
    }
}
