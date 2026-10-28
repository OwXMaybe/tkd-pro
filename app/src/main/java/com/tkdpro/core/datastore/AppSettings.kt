package com.tkdpro.core.datastore

import com.tkdpro.core.database.entity.AnalysisEngine

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class UnitSystem { METRIC, IMPERIAL }

/** Home dashboard cards; order and visibility are user-configurable. */
enum class DashboardCard {
    TODAY, RECOVERY, WEEKLY_VOLUME, RUNNING, TECHNICAL, REACTION, VIDEO, COMPETITION, GOALS, AI_OBSERVATIONS, TIMELINE
}

data class DashboardCardConfig(val card: DashboardCard, val visible: Boolean)

/** Per-category consent for sending saved data to the AI Coach. Everything is off until the owner enables it. */
data class AiPermissions(
    val enabled: Boolean = false,
    val training: Boolean = false,
    val running: Boolean = false,
    val matches: Boolean = false,
    val motion: Boolean = false,
    val recovery: Boolean = false,
    val reaction: Boolean = false,
    val profile: Boolean = false,
) {
    val anyCategory: Boolean get() = training || running || matches || motion || recovery || reaction || profile
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val units: UnitSystem = UnitSystem.METRIC,
    val onboardingCompleted: Boolean = false,
    val appLockEnabled: Boolean = false,
    val dashboard: List<DashboardCardConfig> = DashboardCard.entries.map { DashboardCardConfig(it, true) },
    val recoveryReminderEnabled: Boolean = false,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    val gpsAutoPause: Boolean = false,
    val gpsKeepScreenOn: Boolean = true,
    val gpsMaxAccuracyM: Int = 25,
    val ai: AiPermissions = AiPermissions(),
    val backendUrl: String = "",
    val syncEnabled: Boolean = false,
    /** Server revision up to which changes from other devices have been applied. */
    val syncCursor: Long = 0,
    val lastSyncAt: Long? = null,
    val lastSyncError: String? = null,
    val analysisEngine: AnalysisEngine = AnalysisEngine.ON_DEVICE,
)
