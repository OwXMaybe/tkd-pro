package com.tkdpro.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class RunType { EASY, ENDURANCE, TEMPO, INTERVAL, RECOVERY, SPRINT, CUSTOM }

@Serializable
enum class RunStatus { ACTIVE, PAUSED, COMPLETED, DISCARDED }

@Serializable
@Entity(tableName = "running_sessions", indices = [Index("startTime"), Index("status")])
data class RunningSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RunType,
    val customName: String? = null,
    val status: RunStatus,
    val startTime: Long,
    val endTime: Long? = null,
    /** Time spent moving, excluding manual and automatic pauses. */
    val movingTimeMs: Long = 0,
    /** Distance from filtered GPS points only. */
    val distanceM: Double = 0.0,
    val elevationGainM: Double? = null,
    /** Optional, entered manually or from a compatible device; never estimated. */
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val best1kMs: Long? = null,
    val best5kMs: Long? = null,
    val best10kMs: Long? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "gps_points",
    foreignKeys = [ForeignKey(entity = RunningSession::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["runId", "timeMs"], unique = true)],
)
data class GpsPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val timeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val accuracyM: Float,
    val speedMps: Float?,
    val segment: Int,
)

@Serializable
@Entity(
    tableName = "running_splits",
    foreignKeys = [ForeignKey(entity = RunningSession::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["runId", "idx"], unique = true)],
)
data class RunningSplit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val idx: Int,
    val distanceM: Double,
    val durationMs: Long,
    val elevationGainM: Double,
)
