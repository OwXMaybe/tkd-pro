package com.tkdpro.feature.performance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tkdpro.R
import com.tkdpro.core.database.entity.BodySide
import com.tkdpro.core.database.entity.PerformanceMetric
import com.tkdpro.core.database.entity.PerformanceTestType
import com.tkdpro.core.designsystem.component.ChartPoint
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.DecimalField
import com.tkdpro.core.designsystem.component.LineChart
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.ProvenanceBadge
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.SectionHeader
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.instructions
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.PerformanceRepository
import com.tkdpro.navigation.PerformanceTestRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val SIDED = setOf(PerformanceTestType.SINGLE_LEG_BALANCE, PerformanceTestType.SINGLE_LEG_BALANCE_EYES_CLOSED)
private val JUMPS = setOf(PerformanceTestType.STANDING_LONG_JUMP, PerformanceTestType.VERTICAL_JUMP)

@HiltViewModel
class PerformanceTestsViewModel @Inject constructor(repo: PerformanceRepository) : ViewModel() {
    val latest = repo.performanceMetrics.map { list -> list.groupBy { it.testType } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

@Composable
fun PerformanceTestsScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: PerformanceTestsViewModel = hiltViewModel()) {
    val grouped by viewModel.latest.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    Scaffold(topBar = { TkdTopBar(stringResource(R.string.hub_performance), onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item { ScreenMessage(R.string.ptest_disclaimer) }
            items(PerformanceTestType.entries) { type ->
                val results = grouped[type].orEmpty()
                TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(PerformanceTestRoute(type.name)) }, contentPadding = PaddingValues(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                            Text(stringResource(type.label()), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(if (type.inApp) R.string.ptest_in_app else R.string.ptest_manual),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        results.firstOrNull()?.let { Text("${f.number(it.value, 1)} ${it.unit}", style = MaterialTheme.typography.titleSmall) }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class PerformanceTestViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: PerformanceRepository) : ViewModel() {
    val type = PerformanceTestType.valueOf(savedState.toRoute<PerformanceTestRoute>().type)
    val history = repo.metricsOf(type).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(value: Double, side: BodySide?, method: String, notes: String) = viewModelScope.launch {
        repo.saveMetric(
            PerformanceMetric(testType = type, date = System.currentTimeMillis(), value = value, unit = type.unit, side = side, method = method, notes = notes, createdAt = 0),
        )
    }

    fun delete(id: Long) = viewModelScope.launch { repo.deleteMetric(id) }
}

@Composable
fun PerformanceTestScreen(onBack: () -> Unit, viewModel: PerformanceTestViewModel = hiltViewModel()) {
    val type = viewModel.type
    val history by viewModel.history.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    var side by remember { mutableStateOf(if (type in SIDED) BodySide.RIGHT else null) }
    val inAppMethod = stringResource(if (type == PerformanceTestType.TAPPING_COORDINATION) R.string.ptest_method_tapping else R.string.ptest_method_stopwatch)
    Scaffold(topBar = { TkdTopBar(stringResource(type.label()), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            TkdCard(Modifier.fillMaxWidth()) { Text(stringResource(type.instructions())) }
            if (type in SIDED) ChoiceChips(listOf(BodySide.RIGHT, BodySide.LEFT), side, { side = it }, { stringResource(it.label()) })
            when (type) {
                PerformanceTestType.SINGLE_LEG_BALANCE, PerformanceTestType.SINGLE_LEG_BALANCE_EYES_CLOSED ->
                    StopwatchTest { seconds -> viewModel.save(seconds, side, inAppMethod, "") }
                PerformanceTestType.TAPPING_COORDINATION ->
                    TappingTest { taps -> viewModel.save(taps.toDouble(), null, inAppMethod, "") }
                else -> ManualEntry(type) { value, method, notes -> viewModel.save(value, null, method, notes) }
            }
            SectionHeader(stringResource(R.string.ptest_progress))
            LineChart(history.map { ChartPoint(f.shortDate(it.date), it.value) })
            history.sortedByDescending { it.date }.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${f.number(m.value, 1)} ${m.unit}" + (m.side?.let { " · " + stringResource(it.label()) } ?: ""), style = MaterialTheme.typography.bodyMedium)
                        Text(listOf(f.date(m.date), m.method).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ProvenanceBadge(if (type.inApp) Provenance.MEASURED else Provenance.MANUAL)
                    IconButton(onClick = { viewModel.delete(m.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}

@Composable
private fun StopwatchTest(onResult: (Double) -> Unit) {
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var result by remember { mutableStateOf<Double?>(null) }
    val f = LocalFormatter.current
    LaunchedEffect(running) {
        val start = System.nanoTime() - elapsed * 1_000_000
        while (running) {
            elapsed = (System.nanoTime() - start) / 1_000_000
            if (elapsed >= MAX_BALANCE_MS) running = false
            delay(50)
        }
    }
    TkdCard(Modifier.fillMaxWidth()) {
        Text(f.number(elapsed / 1000.0, 1) + " s", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = {
                if (running) {
                    running = false
                    result = elapsed / 1000.0
                } else {
                    elapsed = 0
                    result = null
                    running = true
                }
            }, modifier = Modifier.weight(1f).height(56.dp)) { Text(stringResource(if (running) R.string.action_stop else R.string.action_start)) }
        }
        result?.let { r ->
            Button(onClick = { onResult(r); result = null; elapsed = 0 }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Text(stringResource(R.string.ptest_save_value, f.number(r, 1) + " s"))
            }
        }
    }
}

private const val MAX_BALANCE_MS = 120_000L
private const val TAPPING_MS = 10_000L

/** 10-second alternating two-target tapping test; only correctly alternating taps count. */
@Composable
private fun TappingTest(onResult: (Int) -> Unit) {
    var running by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    var expectLeft by remember { mutableStateOf(true) }
    var remaining by remember { mutableLongStateOf(TAPPING_MS) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = System.nanoTime()
        while (running) {
            remaining = (TAPPING_MS - (System.nanoTime() - start) / 1_000_000).coerceAtLeast(0)
            if (remaining == 0L) {
                running = false
                finished = true
            }
            delay(30)
        }
    }
    TkdCard(Modifier.fillMaxWidth()) {
        Text("${taps}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
        Text(LocalFormatter.current.number(remaining / 1000.0, 1) + " s", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!running && !finished) {
            Button(onClick = { taps = 0; expectLeft = true; remaining = TAPPING_MS; running = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_start))
            }
        }
        if (running) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
                Button(onClick = { if (expectLeft) { taps++; expectLeft = false } }, modifier = Modifier.weight(1f).height(120.dp), enabled = true) {
                    Text(stringResource(R.string.side_left_short), style = MaterialTheme.typography.headlineMedium)
                }
                Button(onClick = { if (!expectLeft) { taps++; expectLeft = true } }, modifier = Modifier.weight(1f).height(120.dp)) {
                    Text(stringResource(R.string.side_right_short), style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        if (finished) {
            Button(onClick = { onResult(taps); finished = false; taps = 0 }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ptest_save_value, "$taps"))
            }
        }
    }
}

@Composable
private fun ManualEntry(type: PerformanceTestType, onSave: (Double, String, String) -> Unit) {
    var value by remember { mutableStateOf<Double?>(null) }
    var method by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val methodRequired = type in JUMPS
    TkdCard(Modifier.fillMaxWidth()) {
        DecimalField(value, { value = it }, stringResource(R.string.label_value), Modifier.fillMaxWidth(), suffix = type.unit)
        TkdTextField(
            method,
            { method = it.take(120) },
            stringResource(if (methodRequired) R.string.ptest_method_required else R.string.ptest_method_optional),
            isError = methodRequired && method.isBlank(),
        )
        if (methodRequired) ScreenMessage(R.string.ptest_jump_note)
        TkdTextField(notes, { notes = it.take(500) }, stringResource(R.string.label_notes))
        Button(
            onClick = { value?.let { onSave(it, method.trim(), notes.trim()) }; value = null; notes = "" },
            enabled = value != null && (!methodRequired || method.isNotBlank()),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        ) { Text(stringResource(R.string.action_save)) }
    }
}
