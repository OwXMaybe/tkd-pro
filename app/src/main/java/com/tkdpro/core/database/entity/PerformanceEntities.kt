package com.tkdpro.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class ReactionTestType { VISUAL, AUDIO, RANDOM, REPEATED }

@Serializable
@Entity(tableName = "reaction_tests", indices = [Index("date"), Index("type")])
data class ReactionTest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ReactionTestType,
    val date: Long,
    val totalTrials: Int,
    val validTrials: Int,
    val averageMs: Double?,
    val bestMs: Long?,
    val medianMs: Double?,
    val standardDeviationMs: Double?,
    /** Device model: phone latency differs between devices, so results are compared per device. */
    val deviceModel: String,
    /** Test conditions disclosed with every result (stimulus, latency note, filter thresholds). */
    val conditions: String,
    val notes: String = "",
)

@Serializable
@Entity(
    tableName = "reaction_attempts",
    foreignKeys = [ForeignKey(entity = ReactionTest::class, parentColumns = ["id"], childColumns = ["testId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("testId")],
)
data class ReactionAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val testId: Long,
    val idx: Int,
    val stimulus: String,
    val foreperiodMs: Long,
    val reactionMs: Long?,
    val valid: Boolean,
    val invalidReason: String?,
)

@Serializable
enum class PerformanceTestType(val unit: String, val higherIsBetter: Boolean, val inApp: Boolean) {
    SINGLE_LEG_BALANCE("s", true, true),
    SINGLE_LEG_BALANCE_EYES_CLOSED("s", true, true),
    TAPPING_COORDINATION("taps", true, true),
    SIT_AND_REACH("cm", true, false),
    FRONT_SPLIT("cm", false, false),
    SIDE_SPLIT("cm", false, false),
    STANDING_LONG_JUMP("cm", true, false),
    VERTICAL_JUMP("cm", true, false),
    HEXAGON_AGILITY("s", false, false),
}

@Serializable
@Entity(tableName = "performance_metrics", indices = [Index("date"), Index("testType")])
data class PerformanceMetric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val testType: PerformanceTestType,
    val date: Long,
    val value: Double,
    val unit: String,
    val side: BodySide? = null,
    /** How the value was obtained (e.g. "in-app timer", "tape measure", "jump mat"). Required for jump tests. */
    val method: String = "",
    val notes: String = "",
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "recovery_entries", indices = [Index(value = ["epochDay"], unique = true)])
data class RecoveryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val sleepHours: Double? = null,
    /** All subjective ratings are 1..5. */
    val sleepQuality: Int? = null,
    val energy: Int? = null,
    val fatigue: Int? = null,
    val soreness: Int? = null,
    val recovery: Int? = null,
    val restingHeartRate: Int? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)
