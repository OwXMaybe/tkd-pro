package com.tkdpro.feature.running.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.tkdpro.R
import com.tkdpro.core.notifications.Notifications
import com.tkdpro.core.ui.Formatter
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.feature.running.tracking.LocationSource
import com.tkdpro.feature.running.tracking.RunTracker
import com.tkdpro.feature.running.tracking.TrackerStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Foreground service that keeps GPS recording alive while the screen is off or the app is in the
 * background. It is started only by an explicit Start/Resume from the athlete and shows an ongoing
 * notification for the entire recording.
 */
@AndroidEntryPoint
class RunTrackingService : LifecycleService() {
    @Inject lateinit var tracker: RunTracker
    @Inject lateinit var locations: LocationSource
    @Inject lateinit var settings: SettingsRepository

    private var locationJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PAUSE -> tracker.pause()
            ACTION_RESUME -> tracker.resume()
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_RUN,
            buildNotification(getString(R.string.run_notification_starting), TrackerStatus.ACTIVE),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        if (locationJob?.isActive == true) return
        locationJob = lifecycleScope.launch {
            locations.updates()
                .catch { stopTracking() }
                .collect { tracker.onLocation(it) }
        }
        lifecycleScope.launch {
            val units = settings.current().units
            val formatter = Formatter(Locale.getDefault(), units, true)
            tracker.state
                .map { s -> Triple(s.status, s.distanceM.toInt() / 10, s.movingMs / 5000) }
                .distinctUntilChanged()
                .collect {
                    val s = tracker.state.value
                    if (s.status == TrackerStatus.IDLE) {
                        stopTracking()
                        return@collect
                    }
                    val text = "${formatter.distance(s.distanceM)} · ${formatter.duration(s.movingMs)} · ${formatter.pace(s.averagePaceSecPerKm)}"
                    if (Notifications.canPost(this@RunTrackingService)) {
                        try {
                            NotificationManagerCompat.from(this@RunTrackingService).notify(Notifications.ID_RUN, buildNotification(text, s.status))
                        } catch (_: SecurityException) {
                            // Notification permission revoked mid-run; tracking continues.
                        }
                    }
                }
        }
    }

    private fun stopTracking() {
        locationJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(text: String, status: TrackerStatus) =
        NotificationCompat.Builder(this, Notifications.CHANNEL_RUN)
            .setSmallIcon(R.drawable.ic_stat_tkd)
            .setContentTitle(
                getString(if (status == TrackerStatus.PAUSED) R.string.run_notification_paused else R.string.run_notification_title),
            )
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(Notifications.openAppIntent(this))
            .addAction(
                0,
                getString(if (status == TrackerStatus.PAUSED) R.string.action_resume else R.string.action_pause),
                actionIntent(if (status == TrackerStatus.PAUSED) ACTION_RESUME else ACTION_PAUSE),
            )
            .build()

    private fun actionIntent(action: String) = PendingIntent.getService(
        this, action.hashCode(), Intent(this, RunTrackingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_START = "com.tkdpro.run.START"
        const val ACTION_PAUSE = "com.tkdpro.run.PAUSE"
        const val ACTION_RESUME = "com.tkdpro.run.RESUME"
        const val ACTION_STOP = "com.tkdpro.run.STOP"

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, RunTrackingService::class.java).setAction(ACTION_START))

        fun stop(context: Context) {
            context.startService(Intent(context, RunTrackingService::class.java).setAction(ACTION_STOP))
        }
    }
}
