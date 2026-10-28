package com.tkdpro.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.tkdpro.core.database.entity.Competition
import com.tkdpro.core.database.entity.Match
import com.tkdpro.core.database.entity.MatchEvent
import com.tkdpro.core.database.entity.MatchRound
import com.tkdpro.core.database.entity.ScoringPreset
import kotlinx.coroutines.flow.Flow

data class MatchWithDetails(
    @Embedded val match: Match,
    @Relation(parentColumn = "id", entityColumn = "matchId")
    val rounds: List<MatchRound>,
    @Relation(parentColumn = "id", entityColumn = "matchId")
    val events: List<MatchEvent>,
)

@Dao
interface MatchDao {
    @Transaction
    @Query("SELECT * FROM matches ORDER BY date DESC")
    fun observeAll(): Flow<List<MatchWithDetails>>

    @Transaction
    @Query("SELECT * FROM matches WHERE date >= :from AND date < :to ORDER BY date DESC")
    suspend fun inRange(from: Long, to: Long): List<MatchWithDetails>

    @Transaction
    @Query("SELECT * FROM matches WHERE id = :id")
    fun observe(id: Long): Flow<MatchWithDetails?>

    @Transaction
    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun get(id: Long): MatchWithDetails?

    @Query("SELECT * FROM matches")
    suspend fun allMatches(): List<Match>

    @Query("SELECT * FROM match_rounds")
    suspend fun allRounds(): List<MatchRound>

    @Query("SELECT * FROM match_events")
    suspend fun allEvents(): List<MatchEvent>

    @Upsert
    suspend fun upsert(m: Match): Long

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun delete(id: Long)

    @Upsert
    suspend fun upsertRound(r: MatchRound): Long

    @Delete
    suspend fun deleteRound(r: MatchRound)

    @Insert
    suspend fun insertEvent(e: MatchEvent): Long

    @Delete
    suspend fun deleteEvent(e: MatchEvent)

    @Query("SELECT COUNT(*) FROM matches WHERE competitionName = :name AND date = :date AND opponent = :opponent AND id != :exceptId")
    suspend fun countDuplicates(name: String, date: Long, opponent: String, exceptId: Long): Int
}

@Dao
interface CompetitionDao {
    @Query("SELECT * FROM competitions WHERE date >= :from ORDER BY date ASC")
    fun observeUpcoming(from: Long): Flow<List<Competition>>

    @Query("SELECT * FROM competitions ORDER BY date DESC")
    fun observeAll(): Flow<List<Competition>>

    @Query("SELECT * FROM competitions")
    suspend fun all(): List<Competition>

    @Upsert
    suspend fun upsert(c: Competition): Long

    @Query("DELETE FROM competitions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ScoringPresetDao {
    @Query("SELECT * FROM scoring_presets ORDER BY position")
    fun observeAll(): Flow<List<ScoringPreset>>

    @Query("SELECT * FROM scoring_presets")
    suspend fun all(): List<ScoringPreset>

    @Upsert
    suspend fun upsert(p: ScoringPreset): Long

    @Delete
    suspend fun delete(p: ScoringPreset)
}
