package com.tkdpro.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class TrainingCategory {
    TECHNICAL, TACTICAL, SPARRING, FOOTWORK, REACTION, COMBINATION, CONDITIONING, STRENGTH_POWER, MOBILITY, RECOVERY
}

@Serializable
enum class SessionStatus { PLANNED, COMPLETED }

@Serializable
enum class BodySide { RIGHT, LEFT, BOTH }

@Serializable
enum class TechniqueCategory { KICK, PUNCH, FOOTWORK, BLOCK, OTHER }

@Serializable
@Entity(
    tableName = "training_sessions",
    indices = [Index("startTime"), Index("category"), Index("status")],
)
data class TrainingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: TrainingCategory,
    val status: SessionStatus,
    val startTime: Long,
    val durationMin: Int,
    val location: String? = null,
    /** 1..5, optional. */
    val difficulty: Int? = null,
    /** Rating of perceived exertion 1..10, optional. */
    val rpe: Int? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "techniques",
    indices = [Index(value = ["builtInKey"], unique = true), Index("category")],
)
data class TrainingTechnique(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** User-visible name. For built-in techniques this is the romanised Korean name. */
    val name: String,
    /** Stable key for built-in techniques (e.g. DOLLYO_CHAGI); null for custom techniques. */
    val builtInKey: String? = null,
    val category: TechniqueCategory,
    val difficulty: Int? = null,
    val description: String = "",
    val videoAssetId: Long? = null,
    val notes: String = "",
    val coachFeedback: String = "",
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "technique_records",
    foreignKeys = [
        ForeignKey(entity = TrainingSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrainingTechnique::class, parentColumns = ["id"], childColumns = ["techniqueId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId"), Index("techniqueId")],
)
data class TechniqueRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val techniqueId: Long,
    val sets: Int? = null,
    val reps: Int? = null,
    val rightReps: Int? = null,
    val leftReps: Int? = null,
    val side: BodySide = BodySide.BOTH,
    val durationSec: Int? = null,
    val difficulty: Int? = null,
    val notes: String = "",
    val coachFeedback: String = "",
    val videoAssetId: Long? = null,
    val createdAt: Long,
)

/** Total repetitions as entered: the explicit total, or the sum of the entered side counts. */
fun TechniqueRecord.totalReps(): Int = reps ?: ((rightReps ?: 0) + (leftReps ?: 0))

@Serializable
@Entity(tableName = "combinations")
data class Combination(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val videoAssetId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "combination_steps",
    foreignKeys = [
        ForeignKey(entity = Combination::class, parentColumns = ["id"], childColumns = ["combinationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrainingTechnique::class, parentColumns = ["id"], childColumns = ["techniqueId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("combinationId"), Index("techniqueId")],
)
data class CombinationStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val combinationId: Long,
    val position: Int,
    val techniqueId: Long,
    val side: BodySide,
)

@Serializable
@Entity(
    tableName = "combination_practices",
    foreignKeys = [
        ForeignKey(entity = Combination::class, parentColumns = ["id"], childColumns = ["combinationId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("combinationId"), Index("date")],
)
data class CombinationPractice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val combinationId: Long,
    val sessionId: Long? = null,
    val date: Long,
    val reps: Int,
    val notes: String = "",
    val videoAssetId: Long? = null,
    val createdAt: Long,
)
