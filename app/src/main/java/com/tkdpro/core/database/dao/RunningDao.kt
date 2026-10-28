package com.tkdpro.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tkdpro.core.database.entity.GpsPoint
import com.tkdpro.core.database.entity.RunningSession
import com.tkdpro.core.database.entity.RunningSplit
import kotlinx.coroutines.flow.Flow

@Dao
interface RunningDao {
    @Insert
    suspend fun insert(run: RunningSession): Long

    @Update
    suspend fun update(run: RunningSession)

    @Query("SELECT * FROM running_sessions WHERE id = :id")
    suspend fun get(id: Long): RunningSession?

    @Query("SELECT * FROM running_sessions WHERE id = :id")
    fun observe(id: Long): Flow<RunningSession?>

    @Query("SELECT * FROM running_sessions WHERE status IN ('ACTIVE','PAUSED') ORDER BY startTime DESC LIMIT 1")
    suspend fun unfinished(): RunningSession?

    @Query("SELECT * FROM running_sessions WHERE status = 'COMPLETED' ORDER BY startTime DESC")
    fun observeCompleted(): Flow<List<RunningSession>>

    @Query("SELECT * FROM running_sessions WHERE status = 'COMPLETED' AND startTime >= :from AND startTime < :to ORDER BY startTime DESC")
    suspend fun completedInRange(from: Long, to: Long): List<RunningSession>

    @Query("SELECT * FROM running_sessions WHERE status = 'COMPLETED'")
    suspend fun allCompleted(): List<RunningSession>

    @Query("DELETE FROM running_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    /** IGNORE keeps the (runId, timeMs) unique index from creating duplicate points. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPoint(p: GpsPoint): Long

    @Query("SELECT * FROM gps_points WHERE runId = :runId ORDER BY timeMs")
    suspend fun points(runId: Long): List<GpsPoint>

    @Query("SELECT * FROM gps_points WHERE runId = :runId ORDER BY timeMs DESC LIMIT 1")
    suspend fun lastPoint(runId: Long): GpsPoint?

    @Query("SELECT * FROM running_splits WHERE runId = :runId ORDER BY idx")
    fun observeSplits(runId: Long): Flow<List<RunningSplit>>

    @Query("SELECT * FROM running_splits")
    suspend fun allSplits(): List<RunningSplit>

    @Query("SELECT * FROM gps_points")
    suspend fun allPoints(): List<GpsPoint>

    @Query("DELETE FROM running_splits WHERE runId = :runId")
    suspend fun clearSplits(runId: Long)

    @Insert
    suspend fun insertSplits(s: List<RunningSplit>)

    @Transaction
    suspend fun replaceSplits(runId: Long, splits: List<RunningSplit>) {
        clearSplits(runId)
        insertSplits(splits)
    }
}
