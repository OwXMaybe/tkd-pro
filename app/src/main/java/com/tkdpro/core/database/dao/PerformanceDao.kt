package com.tkdpro.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.tkdpro.core.database.entity.PerformanceMetric
import com.tkdpro.core.database.entity.PerformanceTestType
import com.tkdpro.core.database.entity.ReactionAttempt
import com.tkdpro.core.database.entity.ReactionTest
import com.tkdpro.core.database.entity.RecoveryEntry
import kotlinx.coroutines.flow.Flow

data class ReactionTestWithAttempts(
    @Embedded val test: ReactionTest,
    @Relation(parentColumn = "id", entityColumn = "testId")
    val attempts: List<ReactionAttempt>,
)

@Dao
interface ReactionDao {
    @Insert
    suspend fun insertTest(t: ReactionTest): Long

    @Insert
    suspend fun insertAttempts(a: List<ReactionAttempt>)

    @Transaction
    suspend fun save(test: ReactionTest, attempts: List<ReactionAttempt>): Long {
        val id = insertTest(test)
        insertAttempts(attempts.map { it.copy(testId = id) })
        return id
    }

    @Query("SELECT * FROM reaction_tests ORDER BY date DESC")
    fun observeAll(): Flow<List<ReactionTest>>

    @Query("SELECT * FROM reaction_tests WHERE date >= :from AND date < :to ORDER BY date")
    suspend fun inRange(from: Long, to: Long): List<ReactionTest>

    @Query("SELECT * FROM reaction_tests")
    suspend fun all(): List<ReactionTest>

    @Query("SELECT * FROM reaction_attempts")
    suspend fun allAttempts(): List<ReactionAttempt>

    @Transaction
    @Query("SELECT * FROM reaction_tests WHERE id = :id")
    fun observe(id: Long): Flow<ReactionTestWithAttempts?>

    @Query("SELECT * FROM reaction_tests ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<ReactionTest?>

    @Query("DELETE FROM reaction_tests WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PerformanceDao {
    @Insert
    suspend fun insert(m: PerformanceMetric): Long

    @Query("SELECT * FROM performance_metrics ORDER BY date DESC")
    fun observeAll(): Flow<List<PerformanceMetric>>

    @Query("SELECT * FROM performance_metrics WHERE testType = :type ORDER BY date")
    fun observeByType(type: PerformanceTestType): Flow<List<PerformanceMetric>>

    @Query("SELECT * FROM performance_metrics WHERE date >= :from AND date < :to")
    suspend fun inRange(from: Long, to: Long): List<PerformanceMetric>

    @Query("SELECT * FROM performance_metrics")
    suspend fun all(): List<PerformanceMetric>

    @Query("DELETE FROM performance_metrics WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecoveryDao {
    @Upsert
    suspend fun upsert(e: RecoveryEntry): Long

    @Query("SELECT * FROM recovery_entries WHERE epochDay = :day")
    suspend fun forDay(day: Long): RecoveryEntry?

    @Query("SELECT * FROM recovery_entries WHERE epochDay = :day")
    fun observeDay(day: Long): Flow<RecoveryEntry?>

    @Query("SELECT * FROM recovery_entries WHERE epochDay >= :fromDay AND epochDay <= :toDay ORDER BY epochDay")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<RecoveryEntry>>

    @Query("SELECT * FROM recovery_entries WHERE epochDay >= :fromDay AND epochDay <= :toDay ORDER BY epochDay")
    suspend fun range(fromDay: Long, toDay: Long): List<RecoveryEntry>

    @Query("SELECT * FROM recovery_entries ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<RecoveryEntry>>

    @Query("SELECT * FROM recovery_entries")
    suspend fun all(): List<RecoveryEntry>

    @Query("DELETE FROM recovery_entries WHERE id = :id")
    suspend fun delete(id: Long)
}
