package com.tkdpro.data.repository

import com.tkdpro.core.database.dao.PerformanceDao
import com.tkdpro.core.database.dao.ReactionDao
import com.tkdpro.core.database.dao.ReactionTestWithAttempts
import com.tkdpro.core.database.dao.RecoveryDao
import com.tkdpro.core.database.entity.PerformanceMetric
import com.tkdpro.core.database.entity.PerformanceTestType
import com.tkdpro.core.database.entity.ReactionAttempt
import com.tkdpro.core.database.entity.ReactionTest
import com.tkdpro.core.database.entity.RecoveryEntry
import com.tkdpro.data.sync.SyncTracker
import com.tkdpro.di.Clock
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformanceRepository @Inject constructor(
    private val reactionDao: ReactionDao,
    private val performanceDao: PerformanceDao,
    private val recoveryDao: RecoveryDao,
    private val sync: SyncTracker,
    private val clock: Clock,
) {
    // Reaction
    val reactionTests: Flow<List<ReactionTest>> = reactionDao.observeAll()
    val latestReaction: Flow<ReactionTest?> = reactionDao.observeLatest()
    fun reactionTest(id: Long): Flow<ReactionTestWithAttempts?> = reactionDao.observe(id)
    suspend fun reactionIn(from: Long, to: Long) = reactionDao.inRange(from, to)

    suspend fun saveReactionTest(test: ReactionTest, attempts: List<ReactionAttempt>): Long {
        val id = reactionDao.save(test, attempts)
        sync.upsert("reaction_tests", id, test.copy(id = id), ReactionTest.serializer())
        return id
    }

    suspend fun deleteReactionTest(id: Long) {
        reactionDao.delete(id)
        sync.delete("reaction_tests", id)
    }

    // Performance tests
    val performanceMetrics: Flow<List<PerformanceMetric>> = performanceDao.observeAll()
    fun metricsOf(type: PerformanceTestType): Flow<List<PerformanceMetric>> = performanceDao.observeByType(type)
    suspend fun metricsIn(from: Long, to: Long) = performanceDao.inRange(from, to)

    suspend fun saveMetric(m: PerformanceMetric): Long {
        val toSave = m.copy(createdAt = clock.now())
        val id = performanceDao.insert(toSave)
        sync.upsert("performance_metrics", id, toSave.copy(id = id), PerformanceMetric.serializer())
        return id
    }

    suspend fun deleteMetric(id: Long) {
        performanceDao.delete(id)
        sync.delete("performance_metrics", id)
    }

    // Recovery
    val recoveryEntries: Flow<List<RecoveryEntry>> = recoveryDao.observeAll()
    fun recoveryForDay(day: Long): Flow<RecoveryEntry?> = recoveryDao.observeDay(day)
    fun recoveryRange(fromDay: Long, toDay: Long): Flow<List<RecoveryEntry>> = recoveryDao.observeRange(fromDay, toDay)
    suspend fun recoveryIn(fromDay: Long, toDay: Long) = recoveryDao.range(fromDay, toDay)

    /** One check-in per day: saving again for the same day updates that day's entry. */
    suspend fun saveRecovery(entry: RecoveryEntry) {
        val now = clock.now()
        val existing = recoveryDao.forDay(entry.epochDay)
        val toSave = entry.copy(
            id = existing?.id ?: 0,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val id = recoveryDao.upsert(toSave).let { if (toSave.id != 0L) toSave.id else it }
        sync.upsert("recovery_entries", id, toSave.copy(id = id), RecoveryEntry.serializer())
    }

    suspend fun deleteRecovery(id: Long) {
        recoveryDao.delete(id)
        sync.delete("recovery_entries", id)
    }
}
