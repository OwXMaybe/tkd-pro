package com.tkdpro.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tkdpro.R
import com.tkdpro.core.designsystem.component.BarChart
import com.tkdpro.core.designsystem.component.ChartPoint
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.DateField
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.InlineEmpty
import com.tkdpro.core.designsystem.component.LineChart
import com.tkdpro.core.designsystem.component.LoadingState
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.ProvenanceBadge
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.StatTile
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.domain.time.DateRange
import com.tkdpro.core.ui.Formatter
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.InsightsData
import com.tkdpro.data.repository.InsightsRepository
import com.tkdpro.data.repository.SeriesPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class InsightsPeriod { WEEK, MONTH, CUSTOM }

@HiltViewModel
class InsightsViewModel @Inject constructor(private val repo: InsightsRepository) : ViewModel() {
    private val today = LocalDate.now()
    val period = MutableStateFlow(InsightsPeriod.WEEK)
    val range = MutableStateFlow(DateRange.week(today))
    val data = MutableStateFlow<InsightsData?>(null)

    fun setPeriod(p: InsightsPeriod) {
        period.value = p
        range.value = when (p) {
            InsightsPeriod.WEEK -> DateRange.week(today)
            InsightsPeriod.MONTH -> DateRange.month(today)
            InsightsPeriod.CUSTOM -> DateRange.lastDays(today, 30)
        }
        refresh()
    }

    fun setCustom(start: LocalDate?, end: LocalDate?) {
        val s = start ?: range.value.start
        val e = end ?: range.value.endInclusive
        range.value = if (e.isBefore(s)) DateRange(e, s) else DateRange(s, e)
        refresh()
    }

    fun refresh() = viewModelScope.launch { data.value = repo.compute(range.value) }
}

@Composable
fun InsightsScreen(onNavigate: (Any) -> Unit, viewModel: InsightsViewModel = hiltViewModel()) {
    val period by viewModel.period.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    val zone = ZoneId.systemDefault()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.md, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(stringResource(R.string.tab_insights), style = MaterialTheme.typography.headlineMedium)
            Text("${f.date(range.start)} – ${f.date(range.endInclusive)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ChoiceChips(InsightsPeriod.entries, period, viewModel::setPeriod, {
                stringResource(
                    when (it) {
                        InsightsPeriod.WEEK -> R.string.period_week
                        InsightsPeriod.MONTH -> R.string.period_month
                        InsightsPeriod.CUSTOM -> R.string.period_custom
                    },
                )
            })
        }
        if (period == InsightsPeriod.CUSTOM) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DateField(stringResource(R.string.label_from), range.start.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli(), { ms ->
                        viewModel.setCustom(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(), null)
                    }, Modifier.weight(1f))
                    DateField(stringResource(R.string.label_to), range.endInclusive.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli(), { ms ->
                        viewModel.setCustom(null, Instant.ofEpochMilli(ms).atZone(zone).toLocalDate())
                    }, Modifier.weight(1f))
                }
            }
        }
        val d = data
        if (d == null) {
            item { LoadingState() }
            return@LazyColumn
        }
        if (d.isEmpty) {
            item { EmptyState(stringResource(R.string.insights_empty_title), stringResource(R.string.insights_empty_body), icon = Icons.Filled.Insights) }
            return@LazyColumn
        }
        item { OverallCard(d, f) }
        item { TrainingCard(d, f) }
        item { TechnicalCard(d, f) }
        item { ConsistencyCard(d, f) }
        item { MatchCard(d, f) }
        item { RunningCard(d, f) }
        item { ReactionCard(d, f) }
        item { RecoveryCard(d, f) }
    }
}

private fun List<SeriesPoint>.chart(f: Formatter) = map { ChartPoint(f.shortDate(it.date), it.value) }

@Composable
private fun Header(title: String, provenance: Provenance? = null) {
    Row {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        provenance?.let { ProvenanceBadge(it) }
    }
}

@Composable
private fun compare(current: Number, previous: Number, f: Formatter, format: (Number) -> String = { f.integer(it) }): String =
    stringResource(R.string.insights_previous, format(previous)).let { base ->
        val diff = current.toDouble() - previous.toDouble()
        if (diff == 0.0) base else "$base (${if (diff > 0) "+" else "−"}${format(kotlin.math.abs(diff))})"
    }

@Composable
private fun OverallCard(d: InsightsData, f: Formatter) {
    TkdCard(Modifier.fillMaxWidth()) {
        Header(stringResource(R.string.insights_overall))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
            StatTile(stringResource(R.string.label_sessions), d.current.trainingSessions.toString(), Modifier.weight(1f), caption = compare(d.current.trainingSessions, d.previous.trainingSessions, f))
            StatTile(stringResource(R.string.label_runs), d.current.runs.toString(), Modifier.weight(1f), caption = compare(d.current.runs, d.previous.runs, f))
            StatTile(stringResource(R.string.label_active_days), d.current.activeDays.toString(), Modifier.weight(1f), caption = compare(d.current.activeDays, d.previous.activeDays, f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
            StatTile(stringResource(R.string.label_matches), d.current.matches.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.label_analyses), d.current.analyses.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.label_reaction_tests), d.current.reactionTests.toString(), Modifier.weight(1f))
        }
        ScreenMessage(R.string.insights_no_single_score, Modifier.padding(top = Spacing.sm))
    }
}

@Composable
private fun TrainingCard(d: InsightsData, f: Formatter) {
    TkdCard(Modifier.fillMaxWidth()) {
        Header(stringResource(R.string.insights_training_volume))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
            StatTile(stringResource(R.string.label_duration), f.minutes(d.current.trainingMinutes), Modifier.weight(1f), caption = compare(d.current.trainingMinutes, d.previous.trainingMinutes, f) { f.minutes(it.toInt()) })
            StatTile(stringResource(R.string.label_load), f.integer(d.current.trainingLoad), Modifier.weight(1f), provenance = Provenance.ESTIMATE)
        }
        if (d.current.sessionsWithoutRpe > 0) Text(stringResource(R.string.insights_without_rpe, d.current.sessionsWithoutRpe), style = MaterialTheme.typography.bodySmall, color = TkdTheme.colors.warning)
        Text(stringResource(if (d.weeklyBuckets) R.string.insights_minutes_per_week else R.string.insights_minutes_per_day), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.sm))
        BarChart(d.trainingMinutes.chart(f))
        Text(stringResource(R.string.insights_load_series), style = MaterialTheme.typography.labelMedium)
        BarChart(d.trainingLoad.chart(f), color = TkdTheme.colors.warning)
        ScreenMessage(R.string.load_disclaimer)
        if (d.categoryMinutes.isNotEmpty()) {
            Text(stringResource(R.string.insights_by_category), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.sm))
            d.categoryMinutes.entries.sortedByDescending { it.value }.forEach { (c, m) ->
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text(stringResource(c.label()), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(f.minutes(m), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TechnicalCard(d: InsightsData, f: Formatter) {
    TkdCard(Modifier.fillMaxWidth()) {
        Header(stringResource(R.string.insights_technical))
        if (d.topTechniques.isEmpty()) {
            InlineEmpty(stringResource(R.string.insights_technical_empty))
        } else {
            BarChart(d.topTechniques.map { ChartPoint(it.name.take(10), it.totalReps.toDouble()) }, color = TkdTheme.colors.info)
            d.topTechniques.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text(t.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.insights_technique_row, f.integer(t.totalReps), f.integer(t.rightReps), f.integer(t.leftReps), t.sessions),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Text(stringResource(R.string.insights_analyses_count, d.current.analyses), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
    }
}

@Composable
private fun ConsistencyCard(d: InsightsData, f: Formatter) {
    TkdCard(Modifier.fillMaxWidth()) {
        Header(stringResource(R.string.insights_consistency))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
            StatTile(stringResource(R.string.label_active_days), "${d.current.activeDays}/${d.range.days()}", Modifier.weight(1f))
            StatTile(stringResource(R.string.insights_longest_streak), d.longestStreakDays.toString(), Modifier.weight(1f), unit = stringResource(R.string.unit_days))
            StatTile(
                stringResource(R.string.insights_sessions_per_week),
                f.number(d.current.trainingSessions / (d.range.days() / 7.0).coerceAtLeast(1.0), 1),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MatchCard(d: InsightsData, f: Formatter) {
    TkdCard(Modifier.fillMaxWidth()) {
        Header(stringResource(R.string.insights_matches), Provenance.MANUAL)
        if (d.current.matches == 0) {
            InlineEmpty(stringResource(R.string.insights_matches_empty))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
                StatTile(stringResource(R.string.result_win), d.wins.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.result_loss), d.losses.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.result_draw), d.draws.toString(), Modifier.weight(1f))
            }
            Text(stringResource(R.string.insights_attack_success), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.sm))
            LineChart(d.attackSuccess.chart(f))
        }
    }
}

@Composable
private fun RunningCard(d: InsightsData, f: Formatter) {
    TkdCard(Modifier.fillMaxWidth()) {
        Header(stringResource(R.string.insights_running), Provenance.MEASURED)
        if (d.current.runs == 0) {
            InlineEmpty(stringResource(R.string.insights_running_empty))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
                StatTile(stringResource(R.string.label_distance), f.distanceValue(d.current.runDistanceM), Modifier.weight(1f), unit = f.distanceUnit,
                    caption = compare(d.current.runDistanceM, d.previous.runDistanceM, f) { f.distance(it.toDouble()) })
                StatTile(stringResource(R.string.label_duration), f.duration(d.current.runMovingMs), Modifier.weight(1f))
            }
            Text(stringResource(R.string.insights_distance_series), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.sm))
            BarChart(d.runDistance.chart(f), color = TkdTheme.colors.info)
            Text(stringResource(R.string.run_pace_trend), style = MaterialTheme.typography.labelMedium)
            LineChart(d.runPace.map { ChartPoint(f.shortDate(it.date), it.value / 60.0) })
            ScreenMessage(R.string.run_pace_trend_note)
        }
    }
}

@Composable
private fun ReactionCard(d: InsightsData, f: Formatter) {
    TkdCard(Modifier.fillMaxWidth()) {
        Header(stringResource(R.string.insights_reaction), Provenance.MEASURED)
        if (d.reactionAverage.isEmpty()) InlineEmpty(stringResource(R.string.insights_reaction_empty))
        else LineChart(d.reactionAverage.chart(f), color = TkdTheme.colors.ai)
    }
}

@Composable
private fun RecoveryCard(d: InsightsData, f: Formatter) {
    TkdCard(Modifier.fillMaxWidth()) {
        Header(stringResource(R.string.insights_recovery), Provenance.MANUAL)
        if (d.current.recoveryEntries == 0) {
            InlineEmpty(stringResource(R.string.insights_recovery_empty))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.sm)) {
                StatTile(stringResource(R.string.recovery_sleep), d.averageSleepHours?.let { f.number(it, 1) } ?: "--", Modifier.weight(1f), unit = stringResource(R.string.unit_hours))
                StatTile(stringResource(R.string.recovery_energy), d.averageEnergy?.let { f.number(it, 1) } ?: "--", Modifier.weight(1f), unit = "/5")
                StatTile(stringResource(R.string.recovery_soreness), d.averageSoreness?.let { f.number(it, 1) } ?: "--", Modifier.weight(1f), unit = "/5")
            }
            Text(stringResource(R.string.recovery_sleep_trend), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.sm))
            LineChart(d.sleepHours.chart(f))
            Text(stringResource(R.string.recovery_perceived_trend), style = MaterialTheme.typography.labelMedium)
            LineChart(d.perceivedRecovery.chart(f), color = TkdTheme.colors.success)
        }
    }
}
