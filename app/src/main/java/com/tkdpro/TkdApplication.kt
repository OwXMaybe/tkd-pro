package com.tkdpro

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tkdpro.core.notifications.Notifications
import com.tkdpro.core.notifications.ReminderScheduler
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.data.seed.Seeder
import com.tkdpro.data.sync.SyncScheduler
import com.tkdpro.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TkdApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var seeder: Seeder
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var reminders: ReminderScheduler
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch {
            seeder.run()
            val s = settings.current()
            reminders.apply(s.recoveryReminderEnabled, s.reminderHour, s.reminderMinute)
            syncScheduler.schedulePeriodic(s.syncEnabled)
        }
    }
}
