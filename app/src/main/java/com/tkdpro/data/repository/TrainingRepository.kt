package com.tkdpro.data.repository

import com.tkdpro.core.database.dao.CombinationDao
import com.tkdpro.core.database.dao.CombinationWithSteps
import com.tkdpro.core.database.dao.SessionWithRecords
import com.tkdpro.core.database.dao.TechniqueDao
import com.tkdpro.core.database.dao.TechniqueHistoryRow
import com.tkdpro.core.database.dao.TechniqueUsage
import com.tkdpro.core.database.dao.TrainingDao
import com.tkdpro.core.database.entity.Combination
import com.tkdpro.core.database.entity.CombinationPractice
import com.tkdpro.core.database.entity.CombinationStep
import com.tkdpro.core.database.entity.TechniqueRecord
import com.tkdpro.core.database.entity.TrainingSession
import com.tkdpro.core.database.entity.TrainingTechnique
import com.tkdpro.data.sync.CombinationStepsPayload
import com.tkdpro.data.sync.SyncTracker
import com.tkdpro.data.sync.of
import com.tkdpro.di.Clock
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

class DuplicateRecordException : IllegalStateException("A record with the same details already exists")

@Singleton
class TrainingRepository @Inject constructor(
    private val trainingDao: TrainingDao,
    private val techniqueDao: TechniqueDao,
    private val combinationDao: CombinationDao,
    private val sync: SyncTracker,
    private val clock: Clock,
) {
    // Sessions
    fun sessions(from: Long, to: Long): Flow<List<TrainingSession>> = trainingDao.observeSessions(from, to)
    fun recentSessions(limit: Int = 20): Flow<List<TrainingSession>> = trainingDao.observeRecent(limit)
    fun session(id: Long): Flow<SessionWithRecords?> = trainingDao.observeSessionWithRecords(id)
    suspend fun sessionsIn(from: Long, to: Long) = trainingDao.sessions(from, to)

    /**
     * Saves a session. New sessions with the same name, category and start time as an existing one
     * are rejected to prevent accidental duplicates (e.g. double taps on Save).
     */
    suspend fun saveSession(session: TrainingSession): Long {
        val now = clock.now()
        return if (session.id == 0L) {
            if (trainingDao.countDuplicates(session.name, session.startTime, session.category.name) > 0) {
                throw DuplicateRecordException()
            }
            val toSave = session.copy(createdAt = now, updatedAt = now)
            val id = trainingDao.insertSession(toSave)
            sync.upsert("training_sessions", id, toSave.copy(id = id), TrainingSession.serializer())
            id
        } else {
            val toSave = session.copy(updatedAt = now)
            trainingDao.updateSession(toSave)
            sync.upsert("training_sessions", toSave.id, toSave, TrainingSession.serializer())
            session.id
        }
    }

    suspend fun deleteSession(id: Long) {
        trainingDao.deleteSession(id)
        sync.delete("training_sessions", id)
    }

    suspend fun saveRecord(record: TechniqueRecord): Long {
        val id = if (record.id == 0L) {
            trainingDao.insertRecord(record.copy(createdAt = clock.now()))
        } else {
            trainingDao.updateRecord(record)
            record.id
        }
        sync.upsert("technique_records", id, record.copy(id = id), TechniqueRecord.serializer())
        return id
    }

    suspend fun deleteRecord(record: TechniqueRecord) {
        trainingDao.deleteRecord(record)
        sync.delete("technique_records", record.id)
    }

    fun techniqueUsage(from: Long, to: Long): Flow<List<TechniqueUsage>> = trainingDao.observeTechniqueUsage(from, to)

    // Techniques
    val techniques: Flow<List<TrainingTechnique>> = techniqueDao.observeActive()
    fun technique(id: Long): Flow<TrainingTechnique?> = techniqueDao.observe(id)
    fun techniqueHistory(id: Long): Flow<List<TechniqueHistoryRow>> = trainingDao.observeTechniqueHistory(id)

    suspend fun saveTechnique(t: TrainingTechnique): Long {
        if (techniqueDao.countByName(t.name.trim(), t.id) > 0) throw DuplicateRecordException()
        val now = clock.now()
        val toSave = t.copy(name = t.name.trim(), updatedAt = now, createdAt = if (t.id == 0L) now else t.createdAt)
        val id = techniqueDao.upsert(toSave).let { if (t.id != 0L) t.id else it }
        sync.upsert("techniques", id, toSave.copy(id = id), TrainingTechnique.serializer())
        return id
    }

    // Combinations
    val combinations: Flow<List<CombinationWithSteps>> = combinationDao.observeAll()
    fun combination(id: Long): Flow<CombinationWithSteps?> = combinationDao.observe(id)

    suspend fun saveCombination(c: Combination, steps: List<CombinationStep>): Long {
        require(steps.isNotEmpty()) { "A combination needs at least one technique" }
        val now = clock.now()
        val toSave = c.copy(updatedAt = now, createdAt = if (c.id == 0L) now else c.createdAt)
        val id = combinationDao.saveWithSteps(toSave, steps)
        sync.upsert("combinations", id, toSave.copy(id = id), Combination.serializer())
        sync.upsert("combination_steps", id, CombinationStepsPayload.of(steps.mapIndexed { i, s -> s.copy(position = i) }), CombinationStepsPayload.serializer())
        return id
    }

    suspend fun deleteCombination(id: Long) {
        combinationDao.delete(id)
        sync.delete("combinations", id)
    }

    suspend fun recordPractice(p: CombinationPractice): Long {
        val toSave = p.copy(createdAt = clock.now())
        val id = combinationDao.insertPractice(toSave)
        sync.upsert("combination_practices", id, toSave.copy(id = id), CombinationPractice.serializer())
        return id
    }
}
