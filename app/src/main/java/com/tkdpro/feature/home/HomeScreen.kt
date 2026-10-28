package com.tkdpro.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tkdpro.R
import com.tkdpro.core.database.entity.SessionStatus
import com.tkdpro.core.datastore.DashboardCard
import com.tkdpro.core.designsystem.component.InlineEmpty
import com.tkdpro.core.designsystem.component.LoadingState
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.ProvenanceBadge
import com.tkdpro.core.designsystem.component.StatTile
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.domain.run.Pace
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.feature.timeline.TimelineRow
import com.tkdpro.navigation.AiReportsRoute
import com.tkdpro.navigation.CoachRoute
import com.tkdpro.navigation.CompetitionsRoute
import com.tkdpro.navigation.DashboardCustomizeRoute
import com.tkdpro.navigation.GoalsRoute
import com.tkdpro.navigation.InsightsRoute
import com.tkdpro.navigation.LiveRunRoute
import com.tkdpro.navigation.MotionLabRoute
import com.tkdpro.navigation.MotionReportRoute
import com.tkdpro.navigation.ProfileRoute
import com.tkdpro.navigation.ReflexLabRoute
import com.tkdpro.navigation.RecoveryRoute
import com.tkdpro.navigation.RunSummaryRoute
import com.tkdpro.navigation.RunningHomeRoute
import com.tkdpro.navigation.SessionDetailRoute
import com.tkdpro.navigation.SessionEditRoute
import com.tkdpro.navigation.TimelineRoute
import com.tkdpro.navigation.TrainRoute
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(onNavigate: (Any) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return LoadingState()
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.statusBarsPadding(),
    ) {
        item { HomeHeader(s, onNavigate) }
        items(s.settings.dashboard.filter { it.visible }.map { it.card }, key = { it.name }) { card ->
            when (card) {
                DashboardCard.TODAY -> TodayCard(s, onNavigate)
                DashboardCard.RECOVERY -> RecoveryCard(s, onNavigate)
                DashboardCard.WEEKLY_VOLUME -> WeeklyVolumeCard(s, onNavigate)
                DashboardCard.RUNNING -> RunningCard(s, onNavigate)
                DashboardCard.TECHNICAL -> TechnicalCard(s, onNavigate)
                DashboardCard.REACTION -> ReactionCard(s, onNavigate)
                DashboardCard.VIDEO -> VideoCard(s, onNavigate)
                DashboardCard.COMPETITION -> CompetitionCard(s, onNavigate)
                DashboardCard.GOALS -> GoalsCard(s, onNavigate)
                DashboardCard.AI_OBSERVATIONS -> AiCard(s, onNavigate)
                DashboardCard.TIMELINE -> RecentCard(s, onNavigate)
            }
        }
        item {
            AssistChip(
                onClick = { onNavigate(DashboardCustomizeRoute) },
                label = { Text(stringResource(R.string.home_customize)) },
                leadingIcon = { Icon(Icons.Filled.Tune, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun HomeHeader(s: HomeState, onNavigate: (Any) -> Unit) {
    val f = LocalFormatter.current
    var showReminders by remember { mutableStateOf(false) }
    val hour = LocalTime.now().hour
    val greeting = stringResource(
        when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        },
    )
    Column(Modifier.padding(top = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { showReminders = true }) {
                    BadgedBox(badge = { if (s.reminders.isNotEmpty()) Badge { Text(s.reminders.size.toString()) } }) {
                        Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.home_reminders))
                    }
                }
                DropdownMenu(expanded = showReminders, onDismissRequest = { showReminders = false }) {
                    if (s.reminders.isEmpty()) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.home_no_reminders)) }, onClick = { showReminders = false })
                    }
                    s.reminders.forEach { r ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (r.kind) {
                                        ReminderKind.RECOVERY_CHECKIN -> stringResource(R.string.reminder_recovery_title)
                                        ReminderKind.PLANNED_SESSION -> stringResource(R.string.reminder_planned_session, r.title)
                                    },
                                )
                            },
                            onClick = {
                                showReminders = false
                                when (r.kind) {
                                    ReminderKind.RECOVERY_CHECKIN -> onNavigate(RecoveryRoute)
                                    ReminderKind.PLANNED_SESSION -> r.sessionId?.let { onNavigate(SessionDetailRoute(it)) }
                                }
                            },
                        )
                    }
                }
            }
            Avatar(s, Modifier.clickable { onNavigate(ProfileRoute) })
        }
        Spacer(Modifier.size(Spacing.md))
        val name = s.profile?.displayName?.takeIf { it.isNotBlank() }
        Text(
            if (name != null) "$greeting, $name" else greeting,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(f.longDate(s.today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Avatar(s: HomeState, modifier: Modifier) {
    val photo = s.profile?.photoPath
    Box(
        modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            AsyncImage(model = java.io.File(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(38.dp))
        } else {
            Text(
                s.profile?.displayName?.firstOrNull()?.uppercase() ?: "T",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CardTitle(text: String, provenance: Provenance? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.sm)) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (provenance != null) ProvenanceBadge(provenance)
    }
}

@Composable
private fun TodayCard(s: HomeState, onNavigate: (Any) -> Unit) {
    val f = LocalFormatter.current
    val planned = s.todaySessions.filter { it.status == SessionStatus.PLANNED }
    val done = s.todaySessions.filter { it.status == SessionStatus.COMPLETED }
    TkdCard(Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.card_today))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            StatTile(stringResource(R.string.home_completed_sessions), done.size.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.home_training_duration), f.minutes(done.sumOf { it.durationMin }), Modifier.weight(1f))
        }
        if (planned.isEmpty() && done.isEmpty()) {
            InlineEmpty(stringResource(R.string.home_today_empty))
        }
        planned.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clickable { onNavigate(SessionDetailRoute(p.id)) }.padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(TkdTheme.colors.warning, CircleShape))
                Spacer(Modifier.width(Spacing.sm))
                Text(p.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.status_planned), style = MaterialTheme.typography.labelMedium, color = TkdTheme.colors.warning)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
            AssistChip(onClick = { onNavigate(SessionEditRoute()) }, label = { Text(stringResource(R.string.action_log_session)) }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { onNavigate(LiveRunRoute) }, label = { Text(stringResource(R.string.action_start_run)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, Modifier.size(18.dp)) })
        }
    }
}

@Composable
private fun RecoveryCard(s: HomeState, onNavigate: (Any) -> Unit) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(RecoveryRoute) }) {
        CardTitle(stringResource(R.string.card_recovery), Provenance.MANUAL)
        val r = s.recoveryToday
        if (r == null) {
            InlineEmpty(stringResource(R.string.home_recovery_empty))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                StatTile(stringResource(R.string.recovery_sleep), r.sleepHours?.let { f.number(it, 1) } ?: "--", Modifier.weight(1f), unit = stringResource(R.string.unit_hours))
                StatTile(stringResource(R.string.recovery_energy), r.energy?.let { "$it/5" } ?: "--", Modifier.weight(1f))
                StatTile(stringResource(R.string.recovery_perceived), r.recovery?.let { "$it/5" } ?: "--", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeeklyVolumeCard(s: HomeState, onNavigate: (Any) -> Unit) {
    val f = LocalFormatter.current
    val w = s.week
    TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(InsightsRoute) }) {
        CardTitle(stringResource(R.string.card_weekly_volume))
        if (w.sessions == 0) {
            InlineEmpty(stringResource(R.string.home_week_empty))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                StatTile(stringResource(R.string.label_sessions), w.sessions.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.label_duration), f.minutes(w.minutes), Modifier.weight(1f))
                StatTile(stringResource(R.string.label_load), f.integer(w.load), Modifier.weight(1f), provenance = Provenance.ESTIMATE)
            }
            val caption = buildString {
                append(stringResource(R.string.home_vs_last_week, f.minutes(w.previousMinutes)))
                if (w.sessionsWithoutRpe > 0) append(" · ").append(pluralStringResource(R.plurals.sessions_without_rpe, w.sessionsWithoutRpe, w.sessionsWithoutRpe))
            }
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun RunningCard(s: HomeState, onNavigate: (Any) -> Unit) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(RunningHomeRoute) }) {
        CardTitle(stringResource(R.string.card_running), Provenance.MEASURED)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            StatTile(stringResource(R.string.home_week_distance), f.distanceValue(s.weekRunDistanceM), Modifier.weight(1f), unit = f.distanceUnit)
            StatTile(stringResource(R.string.label_runs), s.weekRuns.toString(), Modifier.weight(1f))
        }
        val last = s.lastRun
        if (last == null) {
            InlineEmpty(stringResource(R.string.home_running_empty))
        } else {
            Text(
                stringResource(
                    R.string.home_last_run,
                    f.shortDate(last.startTime),
                    f.distance(last.distanceM),
                    f.pace(Pace.secondsPerKm(last.distanceM, last.movingTimeMs)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm).clickable { onNavigate(RunSummaryRoute(last.id)) },
            )
        }
    }
}

@Composable
private fun TechnicalCard(s: HomeState, onNavigate: (Any) -> Unit) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(TrainRoute) }) {
        CardTitle(stringResource(R.string.card_technical))
        StatTile(stringResource(R.string.home_technical_sessions_week), s.week.technicalSessions.toString())
        if (s.topTechniques.isEmpty()) {
            InlineEmpty(stringResource(R.string.home_technical_empty))
        } else {
            s.topTechniques.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                    Text(t.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.reps_value, f.integer(t.totalReps)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReactionCard(s: HomeState, onNavigate: (Any) -> Unit) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(ReflexLabRoute) }) {
        CardTitle(stringResource(R.string.card_reaction), Provenance.MEASURED)
        val t = s.latestReaction
        if (t == null) {
            InlineEmpty(stringResource(R.string.home_reaction_empty))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                StatTile(stringResource(R.string.reaction_average), t.averageMs?.let { f.integer(Math.round(it)) } ?: "--", Modifier.weight(1f), unit = "ms")
                StatTile(stringResource(R.string.reaction_best), t.bestMs?.toString() ?: "--", Modifier.weight(1f), unit = "ms")
            }
            Text(
                "${stringResource(t.type.label())} · ${f.shortDate(t.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun VideoCard(s: HomeState, onNavigate: (Any) -> Unit) {
    val f = LocalFormatter.current
    val a = s.latestAnalysis
    TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(if (a != null) MotionReportRoute(a.id) else MotionLabRoute) }) {
        CardTitle(stringResource(R.string.card_video), Provenance.ESTIMATE)
        if (a == null) {
            InlineEmpty(stringResource(R.string.home_video_empty))
        } else {
            Text(a.techniqueName, style = MaterialTheme.typography.titleSmall)
            Text(
                "${stringResource(a.status.label())} · ${f.shortDate(a.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompetitionCard(s: HomeState, onNavigate: (Any) -> Unit) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(CompetitionsRoute) }) {
        CardTitle(stringResource(R.string.card_competition))
        val c = s.nextCompetition
        if (c == null) {
            InlineEmpty(stringResource(R.string.home_competition_empty))
        } else {
            val days = ChronoUnit.DAYS.between(s.today, java.time.Instant.ofEpochMilli(c.date).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    Text(listOf(f.date(c.date), c.location).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatTile(stringResource(R.string.label_days_left), days.toString())
            }
        }
    }
}

@Composable
private fun GoalsCard(s: HomeState, onNavigate: (Any) -> Unit) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(GoalsRoute) }) {
        CardTitle(stringResource(R.string.card_goals))
        if (s.goals.isEmpty()) InlineEmpty(stringResource(R.string.home_goals_empty))
        s.goals.forEach { g ->
            Column(Modifier.padding(vertical = Spacing.xs)) {
                Row {
                    Text(g.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    g.targetDate?.let { Text(f.shortDate(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                val target = g.targetValue
                val current = g.currentValue
                if (target != null && current != null && target > 0) {
                    LinearProgressIndicator(
                        progress = { (current / target).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiCard(s: HomeState, onNavigate: (Any) -> Unit) {
    val f = LocalFormatter.current
    val r = s.weeklyReport
    TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(if (r != null) AiReportsRoute else CoachRoute) }) {
        CardTitle(stringResource(R.string.card_ai), Provenance.AI)
        if (r == null) {
            InlineEmpty(stringResource(R.string.home_ai_empty))
        } else {
            Text(r.content, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.ai_generated_on, f.date(r.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun RecentCard(s: HomeState, onNavigate: (Any) -> Unit) {
    TkdCard(Modifier.fillMaxWidth(), onClick = { onNavigate(TimelineRoute) }) {
        CardTitle(stringResource(R.string.card_timeline))
        if (s.recent.isEmpty()) InlineEmpty(stringResource(R.string.home_timeline_empty))
        s.recent.forEach { item -> TimelineRow(item, onNavigate, compact = true) }
    }
}
