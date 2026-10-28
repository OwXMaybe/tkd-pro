package com.tkdpro.navigation

import kotlinx.serialization.Serializable

// Top-level tabs
@Serializable object HomeRoute
@Serializable object TrainRoute
@Serializable object CoachRoute
@Serializable object InsightsRoute
@Serializable object ProfileRoute

// Training
@Serializable data class SessionEditRoute(val id: Long = 0, val planned: Boolean = false)
@Serializable data class SessionDetailRoute(val id: Long)
@Serializable data class TechnicalRecordingRoute(val sessionId: Long)
@Serializable object TechniqueLibraryRoute
@Serializable data class TechniqueDetailRoute(val id: Long)
@Serializable data class TechniqueEditRoute(val id: Long = 0)
@Serializable object CombinationsRoute
@Serializable data class CombinationEditRoute(val id: Long = 0)
@Serializable data class CombinationDetailRoute(val id: Long)

// Motion lab
@Serializable object MotionLabRoute
@Serializable data class MotionReportRoute(val id: Long)
@Serializable data class MotionCompareRoute(val first: Long, val second: Long)

// Matches
@Serializable object MatchesRoute
@Serializable data class MatchEditRoute(val id: Long = 0)
@Serializable data class MatchDetailRoute(val id: Long)

// Running
@Serializable object RunningHomeRoute
@Serializable object LiveRunRoute
@Serializable data class RunSummaryRoute(val id: Long)
@Serializable object RunningAnalyticsRoute

// Performance lab
@Serializable object ReflexLabRoute
@Serializable data class ReactionRunnerRoute(val type: String)
@Serializable data class ReactionResultRoute(val id: Long)
@Serializable object PerformanceTestsRoute
@Serializable data class PerformanceTestRoute(val type: String)
@Serializable object RecoveryRoute

// Other
@Serializable object TimelineRoute
@Serializable object SettingsRoute
@Serializable object ProfileEditRoute
@Serializable object GoalsRoute
@Serializable object CompetitionsRoute
@Serializable object DashboardCustomizeRoute
@Serializable object ScoringPresetsRoute
@Serializable object AiReportsRoute
