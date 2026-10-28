package com.tkdpro.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "athlete_profile")
data class AthleteProfile(
    /** Single-owner app: there is exactly one profile row. */
    @PrimaryKey val id: Long = OWNER_ID,
    val displayName: String,
    val photoPath: String? = null,
    val startedTrainingYear: Int? = null,
    val birthYear: Int? = null,
    val club: String = "",
    val coach: String = "",
    val currentBelt: String = "",
    val weightCategory: String = "",
    val dominantSide: BodySide? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val OWNER_ID = 1L
    }
}

@Serializable
@Entity(tableName = "belt_records", indices = [Index("achievedOn")])
data class BeltRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val belt: String,
    val achievedOn: Long,
    val notes: String = "",
)

@Serializable
enum class GoalCategory { TECHNICAL, COMPETITION, RUNNING, FITNESS, BELT, OTHER }

@Serializable
enum class GoalStatus { ACTIVE, ACHIEVED, ARCHIVED }

@Serializable
@Entity(tableName = "personal_goals", indices = [Index("status")])
data class PersonalGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: GoalCategory,
    val targetDate: Long? = null,
    val targetValue: Double? = null,
    val currentValue: Double? = null,
    val unit: String = "",
    val status: GoalStatus = GoalStatus.ACTIVE,
    val achievedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
enum class AiReportType { WEEKLY, MOTION, PERIOD }

@Serializable
@Entity(tableName = "ai_reports", indices = [Index("createdAt"), Index("type")])
data class AiReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AiReportType,
    val periodStart: Long? = null,
    val periodEnd: Long? = null,
    val content: String,
    /** Serialised list of data sources (category, record count, range) the report was based on. */
    val dataSourcesJson: String,
    val model: String = "",
    val createdAt: Long,
)

@Serializable
enum class ChatRole { USER, ASSISTANT }

@Serializable
@Entity(tableName = "chat_messages", indices = [Index("createdAt")])
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: ChatRole,
    val content: String,
    val dataSourcesJson: String? = null,
    val failed: Boolean = false,
    val createdAt: Long,
)

@Serializable
enum class SyncOp { UPSERT, DELETE }

/** Pending change to push to the owner's backend. Local data stays authoritative if sync fails. */
@Entity(tableName = "sync_queue", indices = [Index(value = ["entity", "entityId"], unique = true)])
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entity: String,
    val entityId: Long,
    val op: SyncOp,
    val payload: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)
