package com.tkdpro.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.tkdpro.feature.coach.AiReportsScreen
import com.tkdpro.feature.coach.CoachScreen
import com.tkdpro.feature.home.DashboardCustomizeScreen
import com.tkdpro.feature.home.HomeScreen
import com.tkdpro.feature.insights.InsightsScreen
import com.tkdpro.feature.match.MatchDetailScreen
import com.tkdpro.feature.match.MatchEditScreen
import com.tkdpro.feature.match.MatchesScreen
import com.tkdpro.feature.motion.MotionCompareScreen
import com.tkdpro.feature.motion.MotionLabScreen
import com.tkdpro.feature.motion.MotionReportScreen
import com.tkdpro.feature.performance.PerformanceTestScreen
import com.tkdpro.feature.performance.PerformanceTestsScreen
import com.tkdpro.feature.performance.ReactionResultScreen
import com.tkdpro.feature.performance.ReactionRunnerScreen
import com.tkdpro.feature.performance.RecoveryScreen
import com.tkdpro.feature.performance.ReflexLabScreen
import com.tkdpro.feature.profile.CompetitionsScreen
import com.tkdpro.feature.profile.GoalsScreen
import com.tkdpro.feature.profile.ProfileEditScreen
import com.tkdpro.feature.profile.ProfileScreen
import com.tkdpro.feature.running.LiveRunScreen
import com.tkdpro.feature.running.RunSummaryScreen
import com.tkdpro.feature.running.RunningAnalyticsScreen
import com.tkdpro.feature.running.RunningHomeScreen
import com.tkdpro.feature.settings.ScoringPresetsScreen
import com.tkdpro.feature.settings.SettingsScreen
import com.tkdpro.feature.timeline.TimelineScreen
import com.tkdpro.feature.training.CombinationDetailScreen
import com.tkdpro.feature.training.CombinationEditScreen
import com.tkdpro.feature.training.CombinationsScreen
import com.tkdpro.feature.training.SessionDetailScreen
import com.tkdpro.feature.training.SessionEditScreen
import com.tkdpro.feature.training.TechnicalRecordingScreen
import com.tkdpro.feature.training.TechniqueDetailScreen
import com.tkdpro.feature.training.TechniqueEditScreen
import com.tkdpro.feature.training.TechniqueLibraryScreen
import com.tkdpro.feature.training.TrainingHubScreen

fun NavGraphBuilder.tkdGraph(nav: NavHostController) {
    val go: (Any) -> Unit = { route -> nav.navigate(route) { launchSingleTop = true } }
    val back: () -> Unit = { nav.popBackStack() }

    // Tabs
    composable<HomeRoute> { HomeScreen(go) }
    composable<TrainRoute> { TrainingHubScreen(go) }
    composable<CoachRoute> { CoachScreen(go) }
    composable<InsightsRoute> { InsightsScreen(go) }
    composable<ProfileRoute> { ProfileScreen(go) }

    // Training
    composable<SessionEditRoute> {
        SessionEditScreen(onBack = back, onSaved = { id, isNew ->
            if (isNew) {
                nav.navigate(SessionDetailRoute(id)) { popUpTo<SessionEditRoute> { inclusive = true } }
            } else {
                nav.popBackStack()
            }
        })
    }
    composable<SessionDetailRoute> { SessionDetailScreen(back, go) }
    composable<TechnicalRecordingRoute> { TechnicalRecordingScreen(back) }
    composable<TechniqueLibraryRoute> { TechniqueLibraryScreen(back, go) }
    composable<TechniqueDetailRoute> { TechniqueDetailScreen(back, go) }
    composable<TechniqueEditRoute> { TechniqueEditScreen(back) }
    composable<CombinationsRoute> { CombinationsScreen(back, go) }
    composable<CombinationEditRoute> {
        CombinationEditScreen(onBack = back, onSaved = { id ->
            nav.navigate(CombinationDetailRoute(id)) { popUpTo<CombinationEditRoute> { inclusive = true } }
        })
    }
    composable<CombinationDetailRoute> { CombinationDetailScreen(back, go) }

    // Motion lab
    composable<MotionLabRoute> { MotionLabScreen(back, go) }
    composable<MotionReportRoute> { MotionReportScreen(back, go) }
    composable<MotionCompareRoute> { MotionCompareScreen(back) }

    // Matches
    composable<MatchesRoute> { MatchesScreen(back, go) }
    composable<MatchEditRoute> {
        MatchEditScreen(onBack = back, onSaved = { id, isNew ->
            if (isNew) {
                nav.navigate(MatchDetailRoute(id)) { popUpTo<MatchEditRoute> { inclusive = true } }
            } else {
                nav.popBackStack()
            }
        })
    }
    composable<MatchDetailRoute> { MatchDetailScreen(back, go) }

    // Running
    composable<RunningHomeRoute> { RunningHomeScreen(back, go) }
    composable<LiveRunRoute> {
        LiveRunScreen(onBack = back, onFinished = { id ->
            nav.navigate(RunSummaryRoute(id)) { popUpTo<LiveRunRoute> { inclusive = true } }
        })
    }
    composable<RunSummaryRoute> { RunSummaryScreen(back) }
    composable<RunningAnalyticsRoute> { RunningAnalyticsScreen(back, go) }

    // Performance lab
    composable<ReflexLabRoute> { ReflexLabScreen(back, go) }
    composable<ReactionRunnerRoute> {
        ReactionRunnerScreen(onBack = back, onSaved = { id ->
            nav.navigate(ReactionResultRoute(id)) { popUpTo<ReactionRunnerRoute> { inclusive = true } }
        })
    }
    composable<ReactionResultRoute> { ReactionResultScreen(back) }
    composable<PerformanceTestsRoute> { PerformanceTestsScreen(back, go) }
    composable<PerformanceTestRoute> { PerformanceTestScreen(back) }
    composable<RecoveryRoute> { RecoveryScreen(back) }

    // Other
    composable<TimelineRoute> { TimelineScreen(back, go) }
    composable<SettingsRoute> { SettingsScreen(back, go) }
    composable<ProfileEditRoute> { ProfileEditScreen(back) }
    composable<GoalsRoute> { GoalsScreen(back) }
    composable<CompetitionsRoute> { CompetitionsScreen(back) }
    composable<DashboardCustomizeRoute> { DashboardCustomizeScreen(back) }
    composable<ScoringPresetsRoute> { ScoringPresetsScreen(back) }
    composable<AiReportsRoute> { AiReportsScreen(back) }
}
