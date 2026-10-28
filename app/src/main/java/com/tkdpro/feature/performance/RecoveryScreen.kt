package com.tkdpro.feature.performance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tkdpro.R
import com.tkdpro.core.database.entity.RecoveryEntry
import com.tkdpro.core.database.entity.SessionStatus
import com.tkdpro.core.designsystem.component.BarChart
import com.tkdpro.core.designsystem.component.ChartPoint
import com.tkdpro.core.designsystem.component.DateField
import com.tkdpro.core.designsystem.component.DecimalField
import com.tkdpro.core.designsystem.component.IntField
import com.tkdpro.core.designsystem.component.LineChart
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.ProvenanceBadge
import com.tkdpro.core.designsystem.component.RatingSelector
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.SectionHeader
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.domain.load.TrainingLoad
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.data.repository.PerformanceRepository
import com.tkdpro.data.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val repo: PerformanceRepository,
    training: TrainingRepository,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    val day = MutableStateFlow(today)

    @OptIn(ExperimentalCoroutinesApi::class)
    val entry = day.flatMapLatest { repo.recoveryForDay(it.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history = repo.recoveryRange(today.minusDays(29).toEpochDay(), today.toEpochDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Daily session-RPE load for the same 30 days (estimate). */
    val dailyLoad = training.sessions(
        today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli(),
        today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    ).map { sessions ->
        (0..29L).map { today.minusDays(29 - it) }.map { d ->
            d to sessions.filter { it.status == SessionStatus.COMPLETED && Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() == d }
                .sumOf { TrainingLoad.sessionLoad(it.durationMin, it.rpe) ?: 0 }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val saved = MutableStateFlow(false)

    fun save(e: RecoveryEntry) = viewModelScope.launch {
        repo.saveRecovery(e.copy(epochDay = day.value.toEpochDay()))
        saved.value = true
    }
}

@Composable
fun RecoveryScreen(onBack: () -> Unit, viewModel: RecoveryViewModel = hiltViewModel()) {
    val existing by viewModel.entry.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val load by viewModel.dailyLoad.collectAsStateWithLifecycle()
    val day by viewModel.day.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    var form by remember(existing, day) {
        mutableStateOf(existing ?: RecoveryEntry(epochDay = day.toEpochDay(), createdAt = 0, updatedAt = 0))
    }
    val zone = ZoneId.systemDefault()

    Scaffold(topBar = { TkdTopBar(stringResource(R.string.recovery_title), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                DateField(
                    stringResource(R.string.label_date),
                    day.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli(),
                    { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
                        if (!d.isAfter(LocalDate.now(zone))) viewModel.day.value = d
                    },
                    Modifier.fillMaxWidth(),
                )
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Row { Text(stringResource(R.string.recovery_checkin), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); ProvenanceBadge(Provenance.MANUAL) }
                    DecimalField(form.sleepHours, { form = form.copy(sleepHours = it?.coerceIn(0.0, 24.0)) }, stringResource(R.string.recovery_sleep_hours), Modifier.fillMaxWidth(), suffix = stringResource(R.string.unit_hours))
                    RatingSelector(stringResource(R.string.recovery_sleep_quality), form.sleepQuality, 1..5, { form = form.copy(sleepQuality = it) }, hint = stringResource(R.string.rating_hint_quality))
                    RatingSelector(stringResource(R.string.recovery_energy), form.energy, 1..5, { form = form.copy(energy = it) }, hint = stringResource(R.string.rating_hint_energy))
                    RatingSelector(stringResource(R.string.recovery_fatigue), form.fatigue, 1..5, { form = form.copy(fatigue = it) }, hint = stringResource(R.string.rating_hint_fatigue))
                    RatingSelector(stringResource(R.string.recovery_soreness), form.soreness, 1..5, { form = form.copy(soreness = it) }, hint = stringResource(R.string.rating_hint_soreness))
                    RatingSelector(stringResource(R.string.recovery_perceived), form.recovery, 1..5, { form = form.copy(recovery = it) }, hint = stringResource(R.string.rating_hint_recovery))
                    IntField(form.restingHeartRate, { form = form.copy(restingHeartRate = it) }, stringResource(R.string.recovery_resting_hr), Modifier.fillMaxWidth(), 25..220)
                    TkdTextField(form.notes, { form = form.copy(notes = it.take(1000)) }, stringResource(R.string.label_notes), singleLine = false, minLines = 2)
                    Button(onClick = { viewModel.save(form) }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                        Text(stringResource(if (existing == null) R.string.action_save else R.string.action_update))
                    }
                    if (saved) Text(stringResource(R.string.recovery_saved), color = TkdTheme.colors.success)
                }
            }
            if ((form.soreness ?: 0) >= 4) {
                item { TkdCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) { Text(stringResource(R.string.recovery_pain_note)) } }
            }
            item { SectionHeader(stringResource(R.string.recovery_trends)) }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.recovery_sleep_trend), style = MaterialTheme.typography.titleSmall)
                    LineChart(history.mapNotNull { e -> e.sleepHours?.let { ChartPoint(f.shortDate(LocalDate.ofEpochDay(e.epochDay)), it) } })
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.recovery_perceived_trend), style = MaterialTheme.typography.titleSmall)
                    LineChart(history.mapNotNull { e -> e.recovery?.let { ChartPoint(f.shortDate(LocalDate.ofEpochDay(e.epochDay)), it.toDouble()) } }, color = TkdTheme.colors.success)
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Row { Text(stringResource(R.string.recovery_load_trend), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); ProvenanceBadge(Provenance.ESTIMATE) }
                    BarChart(load.map { (d, v) -> ChartPoint(f.shortDate(d), v.toDouble()) })
                    ScreenMessage(R.string.load_disclaimer)
                }
            }
        }
    }
}
