package com.tkdpro.data.repository

import com.tkdpro.core.database.dao.MatchDao
import com.tkdpro.core.database.dao.MatchWithDetails
import com.tkdpro.core.database.dao.ScoringPresetDao
import com.tkdpro.core.database.entity.Match
import com.tkdpro.core.database.entity.MatchEvent
import com.tkdpro.core.database.entity.MatchRound
import com.tkdpro.core.database.entity.ScoringPreset
import com.tkdpro.data.sync.SyncTracker
import com.tkdpro.di.Clock
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Aggregates of manually entered round data. Null when no round has that field filled in. */
data class MatchTotals(
    val attackAttempts: Int?,
    val successfulAttacks: Int?,
    val defensiveActions: Int?,
    val counterattacks: Int?,
) {
    val attackSuccessRate: Double?
        get() = if (attackAttempts != null && successfulAttacks != null && attackAttempts > 0) {
            successfulAttacks.toDouble() / attackAttempts
        } else {
            null
        }

    companion object {
        fun of(rounds: List<MatchRound>): MatchTotals {
            fun sum(sel: (MatchRound) -> Int?): Int? = rounds.mapNotNull(sel).takeIf { it.isNotEmpty() }?.sum()
            return MatchTotals(
                attackAttempts = sum { it.attackAttempts },
                successfulAttacks = sum { it.successfulAttacks },
                defensiveActions = sum { it.defensiveActions },
                counterattacks = sum { it.counterattacks },
            )
        }
    }
}

@Singleton
class MatchRepository @Inject constructor(
    private val dao: MatchDao,
    private val presetDao: ScoringPresetDao,
    private val sync: SyncTracker,
    private val clock: Clock,
) {
    val matches: Flow<List<MatchWithDetails>> = dao.observeAll()
    val scoringPresets: Flow<List<ScoringPreset>> = presetDao.observeAll()
    fun match(id: Long): Flow<MatchWithDetails?> = dao.observe(id)
    suspend fun matchesIn(from: Long, to: Long) = dao.inRange(from, to)

    suspend fun saveMatch(m: Match): Long {
        if (dao.countDuplicates(m.competitionName, m.date, m.opponent, m.id) > 0) throw DuplicateRecordException()
        val now = clock.now()
        val toSave = m.copy(updatedAt = now, createdAt = if (m.id == 0L) now else m.createdAt)
        val id = dao.upsert(toSave).let { if (m.id != 0L) m.id else it }
        sync.upsert("matches", id, toSave.copy(id = id), Match.serializer())
        return id
    }

    suspend fun deleteMatch(id: Long) {
        dao.delete(id)
        sync.delete("matches", id)
    }

    suspend fun saveRound(r: MatchRound) {
        val id = dao.upsertRound(r).let { if (r.id != 0L) r.id else it }
        sync.upsert("match_rounds", id, r.copy(id = id), MatchRound.serializer())
    }

    suspend fun deleteRound(r: MatchRound) {
        dao.deleteRound(r)
        sync.delete("match_rounds", r.id)
    }

    suspend fun addEvent(e: MatchEvent) {
        val id = dao.insertEvent(e)
        sync.upsert("match_events", id, e.copy(id = id), MatchEvent.serializer())
    }

    suspend fun deleteEvent(e: MatchEvent) {
        dao.deleteEvent(e)
        sync.delete("match_events", e.id)
    }

    suspend fun savePreset(p: ScoringPreset) {
        val id = presetDao.upsert(p).let { if (p.id != 0L) p.id else it }
        sync.upsert("scoring_presets", id, p.copy(id = id), ScoringPreset.serializer())
    }

    suspend fun deletePreset(p: ScoringPreset) {
        presetDao.delete(p)
        sync.delete("scoring_presets", p.id)
    }
}
