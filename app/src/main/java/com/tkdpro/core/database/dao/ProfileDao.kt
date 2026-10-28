package com.tkdpro.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.tkdpro.core.database.entity.AiReport
import com.tkdpro.core.database.entity.AthleteProfile
import com.tkdpro.core.database.entity.BeltRecord
import com.tkdpro.core.database.entity.ChatMessage
import com.tkdpro.core.database.entity.PersonalGoal
import com.tkdpro.core.database.entity.SyncQueueItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM athlete_profile WHERE id = 1")
    fun observe(): Flow<AthleteProfile?>

    @Query("SELECT * FROM athlete_profile WHERE id = 1")
    suspend fun get(): AthleteProfile?

    @Upsert
    suspend fun upsert(p: AthleteProfile)

    @Query("SELECT * FROM belt_records ORDER BY achievedOn DESC")
    fun observeBelts(): Flow<List<BeltRecord>>

    @Query("SELECT * FROM belt_records")
    suspend fun allBelts(): List<BeltRecord>

    @Upsert
    suspend fun upsertBelt(b: BeltRecord): Long

    @Delete
    suspend fun deleteBelt(b: BeltRecord)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM personal_goals ORDER BY status, targetDate IS NULL, targetDate")
    fun observeAll(): Flow<List<PersonalGoal>>

    @Query("SELECT * FROM personal_goals WHERE status = 'ACTIVE' ORDER BY targetDate IS NULL, targetDate")
    fun observeActive(): Flow<List<PersonalGoal>>

    @Query("SELECT * FROM personal_goals")
    suspend fun all(): List<PersonalGoal>

    @Upsert
    suspend fun upsert(g: PersonalGoal): Long

    @Query("DELETE FROM personal_goals WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AiDao {
    @Insert
    suspend fun insertReport(r: AiReport): Long

    @Query("SELECT * FROM ai_reports ORDER BY createdAt DESC")
    fun observeReports(): Flow<List<AiReport>>

    @Query("SELECT * FROM ai_reports WHERE type = 'WEEKLY' ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestWeekly(): Flow<AiReport?>

    @Query("SELECT * FROM ai_reports")
    suspend fun allReports(): List<AiReport>

    @Query("DELETE FROM ai_reports WHERE id = :id")
    suspend fun deleteReport(id: Long)

    @Insert
    suspend fun insertMessage(m: ChatMessage): Long

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    fun observeMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recentMessages(limit: Int): List<ChatMessage>

    @Query("DELETE FROM chat_messages")
    suspend fun clearChat()

    @Query("SELECT id FROM chat_messages")
    suspend fun messageIds(): List<Long>
}

@Dao
interface SyncDao {
    /** REPLACE collapses repeated edits of the same row into a single pending change. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueItem)

    @Query("SELECT * FROM sync_queue ORDER BY createdAt LIMIT :limit")
    suspend fun pending(limit: Int): List<SyncQueueItem>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM sync_queue WHERE id IN (:ids)")
    suspend fun remove(ids: List<Long>)

    @Query("UPDATE sync_queue SET attempts = attempts + 1, lastError = :error WHERE id IN (:ids)")
    suspend fun markFailed(ids: List<Long>, error: String)

    /** "entity:id" of every change not yet pushed; incoming copies of these rows are skipped. */
    @Query("SELECT entity || ':' || entityId FROM sync_queue")
    suspend fun pendingKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueAll(items: List<SyncQueueItem>)
}
