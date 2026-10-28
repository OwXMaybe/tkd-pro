package com.tkdpro.feature.performance

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tkdpro.R
import com.tkdpro.core.database.entity.ReactionAttempt
import com.tkdpro.core.database.entity.ReactionTest
import com.tkdpro.core.database.entity.ReactionTestType
import com.tkdpro.core.designsystem.component.ConfirmDialog
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.LineChart
import com.tkdpro.core.designsystem.component.ChartPoint
import com.tkdpro.core.designsystem.component.LoadingState
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.ProvenanceBadge
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.SectionHeader
import com.tkdpro.core.designsystem.component.StatTile
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.domain.reaction.InvalidReason
import com.tkdpro.core.domain.reaction.ReactionStats
import com.tkdpro.core.domain.reaction.ReactionTrial
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.description
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.PerformanceRepository
import com.tkdpro.navigation.ReactionResultRoute
import com.tkdpro.navigation.ReactionRunnerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.random.Random

// ---------- Lab ----------

@HiltViewModel
class ReflexLabViewModel @Inject constructor(repo: PerformanceRepository) : ViewModel() {
    val tests = repo.reactionTests.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ReflexLabScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: ReflexLabViewModel = hiltViewModel()) {
    val tests by viewModel.tests.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    Scaffold(topBar = { TkdTopBar(stringResource(R.string.hub_reflex), onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items(ReactionTestType.entries) { type ->
                TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(ReactionRunnerRoute(type.name)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                            Text(stringResource(type.label()), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(type.description()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { ScreenMessage(R.string.reaction_conditions_note) }
            val list = tests
            if (list == null) {
                item { LoadingState() }
                return@LazyColumn
            }
            if (list.isNotEmpty()) {
                item {
                    TkdCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reaction_progress), style = MaterialTheme.typography.titleSmall)
                        LineChart(list.sortedBy { it.date }.mapNotNull { t -> t.averageMs?.let { ChartPoint(f.shortDate(t.date), it) } })
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.reaction_history)) }
            if (list.isEmpty()) item { EmptyState(stringResource(R.string.reaction_empty_title), stringResource(R.string.reaction_empty_body), icon = Icons.Filled.Bolt) }
            items(list, key = { it.id }) { t ->
                TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(ReactionResultRoute(t.id)) }, contentPadding = PaddingValues(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(t.type.label()), style = MaterialTheme.typography.titleSmall)
                            Text(f.dateTime(t.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(t.averageMs?.let { "${Math.round(it)} ms" } ?: "--", style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.reaction_valid_of, t.validTrials, t.totalTrials), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Runner ----------

@HiltViewModel
class ReactionRunnerViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: PerformanceRepository) : ViewModel() {
    val type = ReactionTestType.valueOf(savedState.toRoute<ReactionRunnerRoute>().type)
    val trialCount = if (type == ReactionTestType.REPEATED) 15 else 5

    fun save(attempts: List<RecordedAttempt>, conditions: String, onSaved: (Long) -> Unit) = viewModelScope.launch {
        val summary = ReactionStats.evaluate(attempts.map { ReactionTrial(it.reactionMs, it.falseStart) })
        val test = ReactionTest(
            type = type,
            date = System.currentTimeMillis(),
            totalTrials = summary.totalTrials,
            validTrials = summary.validTrials,
            averageMs = summary.averageMs,
            bestMs = summary.bestMs,
            medianMs = summary.medianMs,
            standardDeviationMs = summary.standardDeviationMs,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            conditions = conditions,
        )
        val rows = summary.trials.mapIndexed { i, t ->
            ReactionAttempt(
                testId = 0, idx = t.index, stimulus = attempts[i].stimulus, foreperiodMs = attempts[i].foreperiodMs,
                reactionMs = t.reactionMs, valid = t.valid, invalidReason = t.invalidReason?.name,
            )
        }
        onSaved(repo.saveReactionTest(test, rows))
    }
}

data class RecordedAttempt(val stimulus: String, val foreperiodMs: Long, val reactionMs: Long?, val falseStart: Boolean)

private enum class Phase { READY, WAITING, STIMULUS, FEEDBACK, DONE }

/**
 * Simple reaction-time test on the phone. Stimulus onset is taken from the first frame drawn after
 * the stimulus appears (visual) or the moment the tone is started (audio); the response time comes from
 * the touch event timestamp. Device display, touch and audio latency are included and disclosed.
 */
@Composable
fun ReactionRunnerScreen(onBack: () -> Unit, onSaved: (Long) -> Unit, viewModel: ReactionRunnerViewModel = hiltViewModel()) {
    val type = viewModel.type
    var phase by remember { mutableStateOf(Phase.READY) }
    var trial by remember { mutableIntStateOf(0) }
    var stimulusKind by remember { mutableStateOf("VISUAL") }
    var stimulusAt by remember { mutableLongStateOf(0L) }
    var foreperiod by remember { mutableLongStateOf(0L) }
    var lastResult by remember { mutableStateOf<RecordedAttempt?>(null) }
    val attempts = remember { mutableStateListOf<RecordedAttempt>() }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }
    DisposableEffect(Unit) { onDispose { tone.release() } }
    val accent = MaterialTheme.colorScheme.primary
    val conditions = stringResource(R.string.reaction_conditions_saved, stringResource(type.label()), ReactionStats.DEFAULT_ANTICIPATION_MS, ReactionStats.DEFAULT_LAPSE_MS)

    fun record(a: RecordedAttempt) {
        attempts += a
        lastResult = a
        phase = if (attempts.size >= viewModel.trialCount) Phase.DONE else Phase.FEEDBACK
    }

    LaunchedEffect(phase, trial) {
        when (phase) {
            Phase.WAITING -> {
                foreperiod = Random.nextLong(1500, 4000)
                delay(foreperiod)
                if (phase != Phase.WAITING) return@LaunchedEffect
                stimulusKind = when (type) {
                    ReactionTestType.VISUAL, ReactionTestType.REPEATED -> "VISUAL"
                    ReactionTestType.AUDIO -> "AUDIO"
                    ReactionTestType.RANDOM -> if (Random.nextBoolean()) "VISUAL" else "AUDIO"
                }
                stimulusAt = 0L
                if (stimulusKind == "AUDIO") {
                    stimulusAt = SystemClock.uptimeMillis()
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                }
                phase = Phase.STIMULUS
                val responded = withTimeoutOrNull(NO_RESPONSE_MS) { while (phase == Phase.STIMULUS) delay(10) }
                if (responded == null && phase == Phase.STIMULUS) record(RecordedAttempt(stimulusKind, foreperiod, null, false))
            }
            Phase.FEEDBACK -> {
                delay(900)
                if (phase == Phase.FEEDBACK) {
                    trial++
                    phase = Phase.WAITING
                }
            }
            else -> Unit
        }
    }

    Scaffold(topBar = { TkdTopBar(stringResource(type.label()), onBack) }) { padding ->
        if (phase == Phase.DONE) {
            DoneSummary(attempts, onSave = { viewModel.save(attempts.toList(), conditions, onSaved) }, onRetry = {
                attempts.clear(); trial = 0; phase = Phase.READY
            })
            return@Scaffold
        }
        val background = if (phase == Phase.STIMULUS && stimulusKind == "VISUAL") accent else MaterialTheme.colorScheme.surfaceVariant
        Box(
            Modifier.fillMaxSize().padding(padding)
                .background(background)
                .drawBehind {
                    if (phase == Phase.STIMULUS && stimulusKind == "VISUAL" && stimulusAt == 0L) stimulusAt = SystemClock.uptimeMillis()
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val t = down.uptimeMillis
                        when (phase) {
                            Phase.READY -> phase = Phase.WAITING
                            Phase.WAITING -> record(RecordedAttempt(stimulusKind, foreperiod, null, true))
                            Phase.STIMULUS -> if (stimulusAt > 0) record(RecordedAttempt(stimulusKind, foreperiod, (t - stimulusAt).coerceAtLeast(0), false))
                            else -> Unit
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.xl)) {
                Text(stringResource(R.string.reaction_trial_of, (attempts.size + 1).coerceAtMost(viewModel.trialCount), viewModel.trialCount), style = MaterialTheme.typography.labelLarge)
                val text = when (phase) {
                    Phase.READY -> stringResource(R.string.reaction_tap_to_begin)
                    Phase.WAITING -> stringResource(R.string.reaction_wait)
                    Phase.STIMULUS -> stringResource(R.string.reaction_tap_now)
                    Phase.FEEDBACK -> lastResult?.let { r ->
                        when {
                            r.falseStart -> stringResource(R.string.invalid_false_start)
                            r.reactionMs == null -> stringResource(R.string.invalid_no_response)
                            else -> "${r.reactionMs} ms"
                        }
                    } ?: ""
                    Phase.DONE -> ""
                }
                Text(text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    color = if (phase == Phase.STIMULUS && stimulusKind == "VISUAL") Color.White else MaterialTheme.colorScheme.onSurface)
                if (phase == Phase.READY) {
                    Text(stringResource(type.description()), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md))
                }
            }
        }
    }
}

private const val NO_RESPONSE_MS = 2000L

@Composable
private fun DoneSummary(attempts: List<RecordedAttempt>, onSave: () -> Unit, onRetry: () -> Unit) {
    val summary = remember(attempts.size) { ReactionStats.evaluate(attempts.map { ReactionTrial(it.reactionMs, it.falseStart) }) }
    val f = LocalFormatter.current
    Column(Modifier.fillMaxSize().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        TkdCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatTile(stringResource(R.string.reaction_average), summary.averageMs?.let { f.integer(Math.round(it)) } ?: "--", Modifier.weight(1f), unit = "ms", provenance = Provenance.MEASURED)
                StatTile(stringResource(R.string.reaction_best), summary.bestMs?.toString() ?: "--", Modifier.weight(1f), unit = "ms")
                StatTile(stringResource(R.string.reaction_sd), summary.standardDeviationMs?.let { f.integer(Math.round(it)) } ?: "--", Modifier.weight(1f), unit = "ms")
            }
            Text(stringResource(R.string.reaction_valid_of, summary.validTrials, summary.totalTrials), modifier = Modifier.padding(top = Spacing.sm))
        }
        summary.trials.forEach { t ->
            Row(Modifier.fillMaxWidth()) {
                Text("#${t.index}", modifier = Modifier.weight(0.2f))
                Text(t.reactionMs?.let { "$it ms" } ?: "--", modifier = Modifier.weight(0.4f))
                Text(t.invalidReason?.let { stringResource(it.label()) } ?: stringResource(R.string.reaction_valid), color = if (t.valid) TkdTheme.colors.success else MaterialTheme.colorScheme.error, modifier = Modifier.weight(0.4f))
            }
        }
        ScreenMessage(R.string.reaction_conditions_note)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        androidx.compose.material3.OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_retry)) }
    }
}

// ---------- Result ----------

@HiltViewModel
class ReactionResultViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: PerformanceRepository) : ViewModel() {
    private val id = savedState.toRoute<ReactionResultRoute>().id
    val test = repo.reactionTest(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.deleteReactionTest(id); onDone() }
}

@Composable
fun ReactionResultScreen(onBack: () -> Unit, viewModel: ReactionResultViewModel = hiltViewModel()) {
    val data by viewModel.test.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TkdTopBar(data?.test?.type?.let { stringResource(it.label()) } ?: "", onBack) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
            }
        },
    ) { padding ->
        val d = data
        if (d == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.dateTime(d.test.date), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        ProvenanceBadge(Provenance.MEASURED)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
                        StatTile(stringResource(R.string.reaction_average), d.test.averageMs?.let { f.integer(Math.round(it)) } ?: "--", Modifier.weight(1f), unit = "ms")
                        StatTile(stringResource(R.string.reaction_best), d.test.bestMs?.toString() ?: "--", Modifier.weight(1f), unit = "ms")
                        StatTile(stringResource(R.string.reaction_sd), d.test.standardDeviationMs?.let { f.integer(Math.round(it)) } ?: "--", Modifier.weight(1f), unit = "ms")
                    }
                    Text(stringResource(R.string.reaction_valid_of, d.test.validTrials, d.test.totalTrials), modifier = Modifier.padding(top = Spacing.sm))
                    Text(d.test.conditions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
                    Text(stringResource(R.string.reaction_device, d.test.deviceModel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(d.attempts.sortedBy { it.idx }, key = { it.id }) { a ->
                Row(Modifier.fillMaxWidth()) {
                    Text("#${a.idx}", modifier = Modifier.weight(0.15f))
                    Text(a.stimulus, modifier = Modifier.weight(0.25f), style = MaterialTheme.typography.bodySmall)
                    Text(a.reactionMs?.let { "$it ms" } ?: "--", modifier = Modifier.weight(0.25f))
                    Text(
                        a.invalidReason?.let { r -> InvalidReason.entries.firstOrNull { it.name == r }?.let { stringResource(it.label()) } ?: r }
                            ?: stringResource(R.string.reaction_valid),
                        color = if (a.valid) TkdTheme.colors.success else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(0.35f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.reaction_delete_title), stringResource(R.string.reaction_delete_body), stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(onBack) }, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}
