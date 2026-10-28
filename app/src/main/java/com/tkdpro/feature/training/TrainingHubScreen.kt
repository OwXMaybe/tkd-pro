package com.tkdpro.feature.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.tkdpro.core.database.entity.SessionStatus
import com.tkdpro.core.database.entity.TrainingSession
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.SectionHeader
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.TrainingRepository
import com.tkdpro.navigation.CombinationsRoute
import com.tkdpro.navigation.MatchesRoute
import com.tkdpro.navigation.MotionLabRoute
import com.tkdpro.navigation.PerformanceTestsRoute
import com.tkdpro.navigation.RecoveryRoute
import com.tkdpro.navigation.ReflexLabRoute
import com.tkdpro.navigation.RunningHomeRoute
import com.tkdpro.navigation.SessionDetailRoute
import com.tkdpro.navigation.SessionEditRoute
import com.tkdpro.navigation.TechniqueLibraryRoute
import com.tkdpro.navigation.TimelineRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TrainingHubViewModel @Inject constructor(repo: TrainingRepository) : ViewModel() {
    val recent = repo.recentSessions(30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private data class HubAction(val label: Int, val icon: ImageVector, val route: Any)

private val hubActions = listOf(
    HubAction(R.string.hub_plan_session, Icons.AutoMirrored.Filled.EventNote, SessionEditRoute(planned = true)),
    HubAction(R.string.hub_technique_library, Icons.AutoMirrored.Filled.LibraryBooks, TechniqueLibraryRoute),
    HubAction(R.string.hub_combinations, Icons.Filled.Link, CombinationsRoute),
    HubAction(R.string.hub_motion_lab, Icons.Filled.Videocam, MotionLabRoute),
    HubAction(R.string.hub_matches, Icons.Filled.SportsKabaddi, MatchesRoute),
    HubAction(R.string.hub_running, Icons.AutoMirrored.Filled.DirectionsRun, RunningHomeRoute),
    HubAction(R.string.hub_reflex, Icons.Filled.Bolt, ReflexLabRoute),
    HubAction(R.string.hub_performance, Icons.Filled.FitnessCenter, PerformanceTestsRoute),
    HubAction(R.string.hub_recovery, Icons.Filled.Bedtime, RecoveryRoute),
    HubAction(R.string.hub_timeline, Icons.Filled.Timeline, TimelineRoute),
)

@Composable
fun TrainingHubScreen(onNavigate: (Any) -> Unit, viewModel: TrainingHubViewModel = hiltViewModel()) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigate(SessionEditRoute()) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.action_log_session)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.md, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text(stringResource(R.string.tab_train), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.hub_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(hubActions.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    row.forEach { a ->
                        TkdCard(Modifier.weight(1f), onClick = { onNavigate(a.route) }, contentPadding = PaddingValues(Spacing.md)) {
                            Icon(a.icon, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(Spacing.sm))
                            Text(stringResource(a.label), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { SectionHeader(stringResource(R.string.hub_recent_sessions)) }
            val list = recent
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        stringResource(R.string.sessions_empty_title),
                        stringResource(R.string.sessions_empty_body),
                        icon = Icons.Filled.SportsMartialArts,
                    )
                }
            }
            items(list.orEmpty(), key = { it.id }) { SessionRow(it) { onNavigate(SessionDetailRoute(it.id)) } }
        }
    }
}

@Composable
fun SessionRow(s: TrainingSession, onClick: () -> Unit) {
    val f = LocalFormatter.current
    TkdCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${stringResource(s.category.label())} · ${f.dateTime(s.startTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text(f.minutes(s.durationMin), style = MaterialTheme.typography.labelLarge)
                if (s.status == SessionStatus.PLANNED) {
                    Text(stringResource(R.string.status_planned), style = MaterialTheme.typography.labelSmall, color = TkdTheme.colors.warning)
                } else if (s.rpe != null) {
                    Text("RPE ${s.rpe}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
