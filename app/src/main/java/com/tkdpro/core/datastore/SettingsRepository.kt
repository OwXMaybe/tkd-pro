package com.tkdpro.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tkdpro.core.database.entity.AnalysisEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val units = stringPreferencesKey("units")
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val appLock = booleanPreferencesKey("app_lock")
        val dashboard = stringPreferencesKey("dashboard")
        val reminder = booleanPreferencesKey("recovery_reminder")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val autoPause = booleanPreferencesKey("gps_auto_pause")
        val keepScreenOn = booleanPreferencesKey("gps_keep_screen_on")
        val maxAccuracy = intPreferencesKey("gps_max_accuracy")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val aiTraining = booleanPreferencesKey("ai_training")
        val aiRunning = booleanPreferencesKey("ai_running")
        val aiMatches = booleanPreferencesKey("ai_matches")
        val aiMotion = booleanPreferencesKey("ai_motion")
        val aiRecovery = booleanPreferencesKey("ai_recovery")
        val aiReaction = booleanPreferencesKey("ai_reaction")
        val aiProfile = booleanPreferencesKey("ai_profile")
        val backendUrl = stringPreferencesKey("backend_url")
        val sync = booleanPreferencesKey("sync_enabled")
        val syncCursor = longPreferencesKey("sync_cursor")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncError = stringPreferencesKey("last_sync_error")
        val engine = stringPreferencesKey("analysis_engine")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val d = AppSettings()
            AppSettings(
                themeMode = p[Keys.theme].toEnum(d.themeMode),
                units = p[Keys.units].toEnum(d.units),
                onboardingCompleted = p[Keys.onboarding] ?: false,
                appLockEnabled = p[Keys.appLock] ?: false,
                dashboard = decodeDashboard(p[Keys.dashboard]),
                recoveryReminderEnabled = p[Keys.reminder] ?: false,
                reminderHour = p[Keys.reminderHour] ?: d.reminderHour,
                reminderMinute = p[Keys.reminderMinute] ?: d.reminderMinute,
                gpsAutoPause = p[Keys.autoPause] ?: d.gpsAutoPause,
                gpsKeepScreenOn = p[Keys.keepScreenOn] ?: d.gpsKeepScreenOn,
                gpsMaxAccuracyM = p[Keys.maxAccuracy] ?: d.gpsMaxAccuracyM,
                ai = AiPermissions(
                    enabled = p[Keys.aiEnabled] ?: false,
                    training = p[Keys.aiTraining] ?: false,
                    running = p[Keys.aiRunning] ?: false,
                    matches = p[Keys.aiMatches] ?: false,
                    motion = p[Keys.aiMotion] ?: false,
                    recovery = p[Keys.aiRecovery] ?: false,
                    reaction = p[Keys.aiReaction] ?: false,
                    profile = p[Keys.aiProfile] ?: false,
                ),
                backendUrl = p[Keys.backendUrl] ?: "",
                syncEnabled = p[Keys.sync] ?: false,
                syncCursor = p[Keys.syncCursor] ?: 0L,
                lastSyncAt = p[Keys.lastSyncAt],
                lastSyncError = p[Keys.lastSyncError],
                analysisEngine = p[Keys.engine].toEnum(d.analysisEngine),
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setUnits(units: UnitSystem) = store.edit { it[Keys.units] = units.name }
    suspend fun setOnboardingCompleted() = store.edit { it[Keys.onboarding] = true }
    suspend fun setAppLock(enabled: Boolean) = store.edit { it[Keys.appLock] = enabled }
    suspend fun setDashboard(cards: List<DashboardCardConfig>) = store.edit { it[Keys.dashboard] = encodeDashboard(cards) }
    suspend fun setRecoveryReminder(enabled: Boolean, hour: Int, minute: Int) = store.edit {
        it[Keys.reminder] = enabled
        it[Keys.reminderHour] = hour
        it[Keys.reminderMinute] = minute
    }
    suspend fun setGpsAutoPause(enabled: Boolean) = store.edit { it[Keys.autoPause] = enabled }
    suspend fun setGpsKeepScreenOn(enabled: Boolean) = store.edit { it[Keys.keepScreenOn] = enabled }
    suspend fun setGpsMaxAccuracy(meters: Int) = store.edit { it[Keys.maxAccuracy] = meters.coerceIn(5, 50) }
    suspend fun setAiPermissions(ai: AiPermissions) = store.edit {
        it[Keys.aiEnabled] = ai.enabled
        it[Keys.aiTraining] = ai.training
        it[Keys.aiRunning] = ai.running
        it[Keys.aiMatches] = ai.matches
        it[Keys.aiMotion] = ai.motion
        it[Keys.aiRecovery] = ai.recovery
        it[Keys.aiReaction] = ai.reaction
        it[Keys.aiProfile] = ai.profile
    }
    suspend fun setBackendUrl(url: String) = store.edit { it[Keys.backendUrl] = url.trim().trimEnd('/') }
    suspend fun setSyncEnabled(enabled: Boolean) = store.edit { it[Keys.sync] = enabled }
    suspend fun setSyncCursor(cursor: Long) = store.edit { it[Keys.syncCursor] = cursor }
    suspend fun setLastSync(at: Long?, error: String?) = store.edit { p ->
        if (at != null) p[Keys.lastSyncAt] = at else p.remove(Keys.lastSyncAt)
        if (error != null) p[Keys.lastSyncError] = error.take(300) else p.remove(Keys.lastSyncError)
    }
    suspend fun setAnalysisEngine(engine: AnalysisEngine) = store.edit { it[Keys.engine] = engine.name }

    /** Resets every preference except language (managed by the platform per-app locale store). */
    suspend fun clear() = store.edit { it.clear() }

    companion object {
        fun encodeDashboard(cards: List<DashboardCardConfig>): String =
            cards.joinToString(",") { "${it.card.name}:${if (it.visible) 1 else 0}" }

        /** Unknown cards are dropped; cards added in newer versions are appended visible. */
        fun decodeDashboard(raw: String?): List<DashboardCardConfig> {
            if (raw.isNullOrBlank()) return AppSettings().dashboard
            val parsed = raw.split(",").mapNotNull { token ->
                val parts = token.split(":")
                val card = DashboardCard.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return@mapNotNull null
                DashboardCardConfig(card, parts.getOrNull(1) != "0")
            }.distinctBy { it.card }
            val missing = DashboardCard.entries.filter { c -> parsed.none { it.card == c } }
                .map { DashboardCardConfig(it, true) }
            return parsed + missing
        }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default
