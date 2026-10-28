package com.tkdpro.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.tkdpro.core.database.entity.Combination
import com.tkdpro.core.database.entity.CombinationPractice
import com.tkdpro.core.database.entity.CombinationStep
import com.tkdpro.core.database.entity.TechniqueRecord
import com.tkdpro.core.database.entity.TrainingSession
import com.tkdpro.core.database.entity.TrainingTechnique
import kotlinx.coroutines.flow.Flow

data class TechniqueRecordWithTechnique(
    @Embedded val record: TechniqueRecord,
    @Relation(parentColumn = "techniqueId", entityColumn = "id")
    val technique: TrainingTechnique,
)

data class SessionWithRecords(
    @Embedded val session: TrainingSession,
    @Relation(entity = TechniqueRecord::class, parentColumn = "id", entityColumn = "sessionId")
    val records: List<TechniqueRecordWithTechnique>,
)

data class TechniqueUsage(
    val techniqueId: Long,
    val name: String,
    val builtInKey: String?,
    val sessions: Int,
    val totalReps: Int,
    val rightReps: Int,
    val leftReps: Int,
)

data class TechniqueHistoryRow(
    @Embedded val record: TechniqueRecord,
    val sessionName: String,
    val sessionDate: Long,
)

@Dao
interface TrainingDao {
    @Query("SELECT * FROM training_sessions WHERE startTime >= :from AND startTime < :to ORDER BY startTime DESC")
    fun observeSessions(from: Long, to: Long): Flow<List<TrainingSession>>

    @Query("SELECT * FROM training_sessions WHERE startTime >= :from AND startTime < :to ORDER BY startTime DESC")
    suspend fun sessions(from: Long, to: Long): List<TrainingSession>

    @Query("SELECT * FROM training_sessions ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TrainingSession>>

    @Query("SELECT * FROM training_sessions")
    suspend fun all(): List<TrainingSession>

    @Transaction
    @Query("SELECT * FROM training_sessions WHERE id = :id")
    fun observeSessionWithRecords(id: Long): Flow<SessionWithRecords?>

    @Query("SELECT * FROM training_sessions WHERE id = :id")
    suspend fun session(id: Long): TrainingSession?

    @Insert
    suspend fun insertSession(session: TrainingSession): Long

    @Update
    suspend fun updateSession(session: TrainingSession)

    @Query("DELETE FROM training_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query(
        "SELECT COUNT(*) FROM training_sessions WHERE name = :name AND startTime = :startTime AND category = :category",
    )
    suspend fun countDuplicates(name: String, startTime: Long, category: String): Int

    @Insert
    suspend fun insertRecord(record: TechniqueRecord): Long

    @Update
    suspend fun updateRecord(record: TechniqueRecord)

    @Delete
    suspend fun deleteRecord(record: TechniqueRecord)

    @Query("SELECT * FROM technique_records")
    suspend fun allRecords(): List<TechniqueRecord>

    @Query(
        """
        SELECT t.id AS techniqueId, t.name AS name, t.builtInKey AS builtInKey,
               COUNT(DISTINCT r.sessionId) AS sessions,
               COALESCE(SUM(COALESCE(r.reps, COALESCE(r.rightReps,0) + COALESCE(r.leftReps,0))), 0) AS totalReps,
               COALESCE(SUM(r.rightReps), 0) AS rightReps,
               COALESCE(SUM(r.leftReps), 0) AS leftReps
        FROM technique_records r
        JOIN techniques t ON t.id = r.techniqueId
        JOIN training_sessions s ON s.id = r.sessionId
        WHERE s.status = 'COMPLETED' AND s.startTime >= :from AND s.startTime < :to
        GROUP BY t.id ORDER BY totalReps DESC, sessions DESC
        """,
    )
    fun observeTechniqueUsage(from: Long, to: Long): Flow<List<TechniqueUsage>>

    @Query(
        """
        SELECT r.*, s.name AS sessionName, s.startTime AS sessionDate
        FROM technique_records r JOIN training_sessions s ON s.id = r.sessionId
        WHERE r.techniqueId = :techniqueId ORDER BY s.startTime DESC
        """,
    )
    fun observeTechniqueHistory(techniqueId: Long): Flow<List<TechniqueHistoryRow>>
}

@Dao
interface TechniqueDao {
    @Query("SELECT * FROM techniques WHERE archived = 0 ORDER BY builtInKey IS NULL, name COLLATE NOCASE")
    fun observeActive(): Flow<List<TrainingTechnique>>

    @Query("SELECT * FROM techniques")
    suspend fun all(): List<TrainingTechnique>

    @Query("SELECT * FROM techniques WHERE id = :id")
    fun observe(id: Long): Flow<TrainingTechnique?>

    @Query("SELECT * FROM techniques WHERE id = :id")
    suspend fun get(id: Long): TrainingTechnique?

    @Query("SELECT builtInKey FROM techniques WHERE builtInKey IS NOT NULL")
    suspend fun builtInKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<TrainingTechnique>)

    @Upsert
    suspend fun upsert(item: TrainingTechnique): Long

    @Query("SELECT COUNT(*) FROM techniques WHERE name = :name COLLATE NOCASE AND id != :exceptId")
    suspend fun countByName(name: String, exceptId: Long): Int
}

data class CombinationStepWithTechnique(
    @Embedded val step: CombinationStep,
    @Relation(parentColumn = "techniqueId", entityColumn = "id")
    val technique: TrainingTechnique,
)

data class CombinationWithSteps(
    @Embedded val combination: Combination,
    @Relation(entity = CombinationStep::class, parentColumn = "id", entityColumn = "combinationId")
    val steps: List<CombinationStepWithTechnique>,
    @Relation(parentColumn = "id", entityColumn = "combinationId")
    val practices: List<CombinationPractice>,
)

@Dao
interface CombinationDao {
    @Transaction
    @Query("SELECT * FROM combinations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CombinationWithSteps>>

    @Transaction
    @Query("SELECT * FROM combinations WHERE id = :id")
    fun observe(id: Long): Flow<CombinationWithSteps?>

    @Transaction
    @Query("SELECT * FROM combinations WHERE id = :id")
    suspend fun get(id: Long): CombinationWithSteps?

    @Query("SELECT * FROM combinations")
    suspend fun allCombinations(): List<Combination>

    @Query("SELECT * FROM combination_steps")
    suspend fun allSteps(): List<CombinationStep>

    @Query("SELECT * FROM combination_practices")
    suspend fun allPractices(): List<CombinationPractice>

    @Upsert
    suspend fun upsert(c: Combination): Long

    @Query("DELETE FROM combination_steps WHERE combinationId = :id")
    suspend fun clearSteps(id: Long)

    @Insert
    suspend fun insertSteps(steps: List<CombinationStep>)

    @Query("DELETE FROM combinations WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertPractice(p: CombinationPractice): Long

    @Query("SELECT * FROM combination_practices WHERE date >= :from AND date < :to")
    suspend fun practices(from: Long, to: Long): List<CombinationPractice>

    @Transaction
    suspend fun saveWithSteps(c: Combination, steps: List<CombinationStep>): Long {
        val id = if (c.id == 0L) upsert(c) else { upsert(c); c.id }
        clearSteps(id)
        insertSteps(steps.mapIndexed { i, s -> s.copy(id = 0, combinationId = id, position = i) })
        return id
    }
}
