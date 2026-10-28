package com.tkdpro.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A video copied into app-private storage, so it is protected from other apps. */
@Serializable
@Entity(tableName = "video_assets", indices = [Index("createdAt")])
data class VideoAsset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Absolute path inside the app's private files directory. */
    val path: String,
    val label: String = "",
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
    val createdAt: Long,
)

@Serializable
enum class AnalysisStatus { COMPLETED, INSUFFICIENT_QUALITY, FAILED }

@Serializable
enum class AnalysisEngine { ON_DEVICE, SERVER }

@Serializable
@Entity(
    tableName = "motion_analyses",
    foreignKeys = [
        ForeignKey(entity = VideoAsset::class, parentColumns = ["id"], childColumns = ["videoAssetId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("videoAssetId"), Index("techniqueId"), Index("createdAt")],
)
data class MotionAnalysis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val techniqueId: Long?,
    val techniqueName: String,
    val videoAssetId: Long,
    /** Side selected by the athlete, or null for automatic detection. */
    val side: String? = null,
    val status: AnalysisStatus,
    val engine: AnalysisEngine,
    /** Serialised [com.tkdpro.core.domain.motion.MotionResult]. */
    val resultJson: String,
    /** Path of the serialised pose frames used for the skeleton overlay. */
    val posePath: String? = null,
    val errorMessage: String? = null,
    /** AI-generated interpretation text; null until requested. Always labelled as AI output in the UI. */
    val aiInterpretation: String? = null,
    val aiGeneratedAt: Long? = null,
    val notes: String = "",
    val createdAt: Long,
)
