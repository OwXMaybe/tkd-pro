package com.tkdpro.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tkdpro.core.database.entity.MotionAnalysis
import com.tkdpro.core.database.entity.VideoAsset
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Insert
    suspend fun insert(v: VideoAsset): Long

    @Query("SELECT * FROM video_assets WHERE id = :id")
    suspend fun get(id: Long): VideoAsset?

    @Query("SELECT * FROM video_assets WHERE id = :id")
    fun observe(id: Long): Flow<VideoAsset?>

    @Query("SELECT * FROM video_assets")
    suspend fun all(): List<VideoAsset>

    @Query("DELETE FROM video_assets WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MotionDao {
    @Insert
    suspend fun insert(a: MotionAnalysis): Long

    @Update
    suspend fun update(a: MotionAnalysis)

    @Query("SELECT * FROM motion_analyses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MotionAnalysis>>

    @Query("SELECT * FROM motion_analyses")
    suspend fun all(): List<MotionAnalysis>

    @Query("SELECT * FROM motion_analyses WHERE createdAt >= :from AND createdAt < :to ORDER BY createdAt DESC")
    suspend fun inRange(from: Long, to: Long): List<MotionAnalysis>

    @Query("SELECT * FROM motion_analyses WHERE id = :id")
    fun observe(id: Long): Flow<MotionAnalysis?>

    @Query("SELECT * FROM motion_analyses WHERE id = :id")
    suspend fun get(id: Long): MotionAnalysis?

    @Query("SELECT * FROM motion_analyses ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(): Flow<MotionAnalysis?>

    @Query("DELETE FROM motion_analyses WHERE id = :id")
    suspend fun delete(id: Long)
}
