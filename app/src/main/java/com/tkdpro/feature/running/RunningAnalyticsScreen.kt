package com.tkdpro.feature.running

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tkdpro.R
import com.tkdpro.core.database.entity.RunningSession
import com.tkdpro.core.database.entity.SessionStatus
import com.tkdpro.core.designsystem.component.BarChart
import com.tkdpro.core.designsystem.component.ChartPoint
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.LineChart
import com.tkdpro.core.designsystem.component.LoadingState
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.StatTile
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.domain.load.TrainingLoad
import com.tkdpro.core.domain.run.Pace
import com.tkdpro.core.domain.run.PersonalRecord
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.data.repository.RunningRepository
import com.tkdpro.data.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class WeekBucket(val weekStart: LocalDate, val runKm: Double, val runs: Int, val tkdMinutes: Int, val tkdSessions: Int)

data class RunningAnalytics(
    val runs: List<RunningSession>,
    val weeks: List<WeekBucket>,
    val months: List<Pair<YearMonth, Double>>,
    val records: List<PersonalRecord>,
    val totalMovingMs: Long,
    val totalDistanceM: Double,
    val runsPerWeek: Double,
)

@HiltViewModel
class RunningAnalyticsViewModel @Inject constructor(running: RunningRepository, training: TrainingRepository) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val firstWeek = LocalDate.ofEpochDay(TrainingLoad.weekStart(today.toEpochDay())).minusWeeks(11)

    val data = combine(
        running.completedRuns,
        running.personalRecords,
        training.sessions(firstWeek.atStartOfDay(zone).toInstant().toEpochMilli(), today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()),
    ) { runs, records, sessions ->
        fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        fun week(d: LocalDate) = LocalDate.ofEpochDay(TrainingLoad.weekStart(d.toEpochDay()))
        val weeks = (0 until 12).map { firstWeek.plusWeeks(it.toLong()) }.map { w ->
            val r = runs.filter { week(date(it.startTime)) == w }
            val s = sessions.filter { it.status == SessionStatus.COMPLETED && week(date(it.startTime)) == w }
            WeekBucket(w, r.sumOf { it.distanceM } / 1000.0, r.size, s.sumOf { it.durationMin }, s.size)
        }
        val thisMonth = YearMonth.from(today)
        val months = (11 downTo 0).map { thisMonth.minusMonths(it.toLong()) }.map { m ->
            m to runs.filter { YearMonth.from(date(it.startTime)) == m }.sumOf { it.distanceM } / 1000.0
        }
        RunningAnalytics(
            runs = runs,
            weeks = weeks,
            months = months,
            records = records,
            totalMovingMs = runs.sumOf { it.movingTimeMs },
            totalDistanceM = runs.sumOf { it.distanceM },
            runsPerWeek = weeks.sumOf { it.runs } / 12.0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun RunningAnalyticsScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: RunningAnalyticsViewModel = hiltViewModel()) {
    val d by viewModel.data.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    Scaffold(topBar = { TkdTopBar(stringResource(R.string.run_analytics), onBack) }) { padding ->
        val a = d
        if (a == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (a.runs.isEmpty()) {
            EmptyState(stringResource(R.string.run_empty_title), stringResource(R.string.run_analytics_empty), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        StatTile(stringResource(R.string.label_total_distance), f.distanceValue(a.totalDistanceM), Modifier.weight(1f), unit = f.distanceUnit)
                        StatTile(stringResource(R.string.label_total_time), f.duration(a.totalMovingMs), Modifier.weight(1f))
                        StatTile(stringResource(R.string.run_frequency), f.number(a.runsPerWeek, 1), Modifier.weight(1f), unit = stringResource(R.string.unit_per_week))
                    }
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.run_weekly_distance), style = MaterialTheme.typography.titleSmall)
                    BarChart(a.weeks.map { ChartPoint(f.shortDate(it.weekStart), it.runKm) })
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.run_monthly_distance), style = MaterialTheme.typography.titleSmall)
                    BarChart(a.months.map { (m, km) -> ChartPoint(m.month.getDisplayName(java.time.format.TextStyle.SHORT, f.locale), km) }, color = TkdTheme.colors.info)
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.run_pace_trend), style = MaterialTheme.typography.titleSmall)
                    val pts = a.runs.sortedBy { it.startTime }.mapNotNull { r ->
                        Pace.secondsPerKm(r.distanceM, r.movingTimeMs)?.let { ChartPoint(f.shortDate(r.startTime), it / 60.0) }
                    }
                    LineChart(pts)
                    ScreenMessage(R.string.run_pace_trend_note)
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.run_personal_records), style = MaterialTheme.typography.titleSmall)
                    a.records.forEach { PrRow(it) { onNavigate(com.tkdpro.navigation.RunSummaryRoute(it.runId)) } }
                    ScreenMessage(R.string.run_pr_note)
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.run_tkd_integration), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.run_tkd_integration_run), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.sm))
                    BarChart(a.weeks.map { ChartPoint(f.shortDate(it.weekStart), it.runKm) }, color = TkdTheme.colors.info)
                    Text(stringResource(R.string.run_tkd_integration_tkd), style = MaterialTheme.typography.labelMedium)
                    BarChart(a.weeks.map { ChartPoint(f.shortDate(it.weekStart), it.tkdMinutes.toDouble()) })
                    ScreenMessage(R.string.run_tkd_disclaimer)
                }
            }
        }
    }
}
