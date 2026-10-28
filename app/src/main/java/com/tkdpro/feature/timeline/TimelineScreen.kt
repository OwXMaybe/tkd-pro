package com.tkdpro.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tkdpro.R
import com.tkdpro.core.database.entity.TrainingCategory
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.domain.time.DateRange
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.TimelineDetail
import com.tkdpro.data.repository.TimelineItem
import com.tkdpro.data.repository.TimelineRepository
import com.tkdpro.data.repository.TimelineType
import com.tkdpro.navigation.MatchDetailRoute
import com.tkdpro.navigation.MotionReportRoute
import com.tkdpro.navigation.ReactionResultRoute
import com.tkdpro.navigation.RecoveryRoute
import com.tkdpro.navigation.RunSummaryRoute
import com.tkdpro.navigation.SessionDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class TimelinePeriod(val days: Long) { WEEK(7), MONTH(30), QUARTER(90), YEAR(365) }

@HiltViewModel
class TimelineViewModel @Inject constructor(repo: TimelineRepository) : ViewModel() {
    val period = MutableStateFlow(TimelinePeriod.MONTH)
    val filter = MutableStateFlow<Set<TimelineType>>(emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val items = period.flatMapLatest { p ->
        val zone = ZoneId.systemDefault()
        val range = DateRange.lastDays(LocalDate.now(zone), p.days)
        repo.timeline(range.startMillis(zone), range.endMillisExclusive(zone), zone)
    }

    val visible = combine(items, filter) { list, f -> if (f.isEmpty()) list else list.filter { it.type in f } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(type: TimelineType) {
        filter.value = filter.value.let { if (type in it) it - type else it + type }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TimelineScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: TimelineViewModel = hiltViewModel()) {
    val items by viewModel.visible.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    Scaffold(topBar = { TkdTopBar(stringResource(R.string.timeline_title), onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item {
                ChoiceChips(TimelinePeriod.entries, period, { viewModel.period.value = it }, {
                    stringResource(
                        when (it) {
                            TimelinePeriod.WEEK -> R.string.period_7d
                            TimelinePeriod.MONTH -> R.string.period_30d
                            TimelinePeriod.QUARTER -> R.string.period_90d
                            TimelinePeriod.YEAR -> R.string.period_365d
                        },
                    )
                })
            }
            item {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimelineType.entries.forEach { t ->
                        FilterChip(selected = t in filter, onClick = { viewModel.toggle(t) }, label = { Text(stringResource(t.label())) })
                    }
                }
            }
            val list = items
            if (list != null && list.isEmpty()) {
                item { EmptyState(stringResource(R.string.timeline_empty_title), stringResource(R.string.timeline_empty_body), icon = Icons.Filled.Timeline) }
            }
            items(list.orEmpty(), key = { "${it.type}-${it.id}-${it.timeMs}" }) { TimelineRow(it, onNavigate) }
        }
    }
}

fun TimelineType.icon(): ImageVector = when (this) {
    TimelineType.TRAINING -> Icons.Filled.SportsMartialArts
    TimelineType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
    TimelineType.MOTION -> Icons.Filled.Videocam
    TimelineType.MATCH -> Icons.Filled.SportsKabaddi
    TimelineType.REACTION -> Icons.Filled.Bolt
    TimelineType.RECOVERY -> Icons.Filled.Bedtime
    TimelineType.ACHIEVEMENT -> Icons.Filled.EmojiEvents
}

fun TimelineItem.route(): Any? = when (type) {
    TimelineType.TRAINING -> SessionDetailRoute(id)
    TimelineType.RUN -> RunSummaryRoute(id)
    TimelineType.MOTION -> MotionReportRoute(id)
    TimelineType.MATCH -> MatchDetailRoute(id)
    TimelineType.REACTION -> ReactionResultRoute(id)
    TimelineType.RECOVERY -> RecoveryRoute
    TimelineType.ACHIEVEMENT -> null
}

@Composable
fun TimelineRow(item: TimelineItem, onNavigate: (Any) -> Unit, compact: Boolean = false) {
    val f = LocalFormatter.current
    val route = item.route()
    Row(
        Modifier.fillMaxWidth()
            .let { m -> if (route != null) m.clickable { onNavigate(route) } else m }
            .padding(vertical = if (compact) Spacing.xs else Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(item.type.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(timelineTitle(item), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(timelineDetail(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(f.shortDate(item.timeMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun timelineTitle(item: TimelineItem): String = when (val d = item.detail) {
    is TimelineDetail.Run -> runCatching { com.tkdpro.core.database.entity.RunType.valueOf(item.title) }
        .map { stringResource(it.label()) }.getOrDefault(item.title)
    is TimelineDetail.Reaction -> runCatching { com.tkdpro.core.database.entity.ReactionTestType.valueOf(item.title) }
        .map { stringResource(it.label()) }.getOrDefault(item.title)
    is TimelineDetail.Recovery -> stringResource(R.string.tl_recovery)
    is TimelineDetail.Achievement -> if (d.kind == "BELT") stringResource(R.string.tl_belt, item.title) else stringResource(R.string.tl_goal_achieved, item.title)
    else -> item.title
}

@Composable
private fun timelineDetail(item: TimelineItem): String {
    val f = LocalFormatter.current
    return when (val d = item.detail) {
        is TimelineDetail.Training -> buildString {
            append(stringResource(TrainingCategory.valueOf(d.category).label()))
            append(" · ").append(f.minutes(d.durationMin))
            d.rpe?.let { append(" · RPE ").append(it) }
            if (d.planned) append(" · ").append(stringResource(R.string.status_planned))
        }
        is TimelineDetail.Run -> "${f.distance(d.distanceM)} · ${f.duration(d.movingTimeMs)}"
        is TimelineDetail.Motion -> stringResource(d.status.label())
        is TimelineDetail.MatchInfo -> buildString {
            append(stringResource(d.result.label()))
            if (d.scoreFor != null && d.scoreAgainst != null) append(" · ${d.scoreFor}–${d.scoreAgainst}")
        }
        is TimelineDetail.Reaction -> d.averageMs?.let { stringResource(R.string.reaction_avg_value, Math.round(it)) }
            ?: stringResource(R.string.reaction_no_valid)
        is TimelineDetail.Recovery -> listOfNotNull(
            d.sleepHours?.let { "${f.number(it, 1)} ${stringResource(R.string.unit_hours)}" },
            d.recovery?.let { "${stringResource(R.string.recovery_perceived)} $it/5" },
        ).joinToString(" · ")
        is TimelineDetail.Achievement -> stringResource(R.string.tl_achievement)
    }.ifBlank { "—" }
}
