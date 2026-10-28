package com.tkdpro.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class MatchResult { WIN, LOSS, DRAW, NOT_RECORDED }

@Serializable
enum class MatchEventType { ATTACK, POINT_SCORED, POINT_CONCEDED, COUNTER, DEFENSE, PENALTY, NOTE }

@Serializable
@Entity(tableName = "matches", indices = [Index("date")])
data class Match(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val competitionName: String,
    val date: Long,
    val opponent: String = "",
    val category: String = "",
    val stage: String = "",
    val result: MatchResult = MatchResult.NOT_RECORDED,
    val scoreFor: Int? = null,
    val scoreAgainst: Int? = null,
    val durationSec: Int? = null,
    val notes: String = "",
    val strengths: String = "",
    val improvements: String = "",
    val videoAssetId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Manually entered round observations. These are never presented as automatic detections. */
@Serializable
@Entity(
    tableName = "match_rounds",
    foreignKeys = [ForeignKey(entity = Match::class, parentColumns = ["id"], childColumns = ["matchId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["matchId", "number"], unique = true)],
)
data class MatchRound(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val number: Int,
    val attackAttempts: Int? = null,
    val successfulAttacks: Int? = null,
    val defensiveActions: Int? = null,
    val counterattacks: Int? = null,
    val pointsFor: Int? = null,
    val pointsAgainst: Int? = null,
    val combinationsObserved: String = "",
    val tacticalNotes: String = "",
    val timingDistanceNotes: String = "",
)

@Serializable
@Entity(
    tableName = "match_events",
    foreignKeys = [ForeignKey(entity = Match::class, parentColumns = ["id"], childColumns = ["matchId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("matchId")],
)
data class MatchEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val round: Int? = null,
    /** Position in the attached match video, if any. */
    val videoTimestampMs: Long? = null,
    val type: MatchEventType,
    val points: Int? = null,
    val note: String = "",
)

/**
 * User-editable scoring presets. Competition rules change, so nothing is hardcoded:
 * the athlete keeps these aligned with the current official rules.
 */
@Serializable
@Entity(tableName = "scoring_presets")
data class ScoringPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val points: Int,
    val position: Int,
)

@Serializable
@Entity(tableName = "competitions", indices = [Index("date")])
data class Competition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: Long,
    val location: String = "",
    val category: String = "",
    val notes: String = "",
    val createdAt: Long,
)
