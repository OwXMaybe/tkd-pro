package com.tkdpro.data.repository

import android.content.Context
import android.net.Uri
import com.tkdpro.core.database.dao.CompetitionDao
import com.tkdpro.core.database.dao.GoalDao
import com.tkdpro.core.database.dao.ProfileDao
import com.tkdpro.core.database.entity.AthleteProfile
import com.tkdpro.core.database.entity.BeltRecord
import com.tkdpro.core.database.entity.Competition
import com.tkdpro.core.database.entity.GoalStatus
import com.tkdpro.core.database.entity.PersonalGoal
import com.tkdpro.data.sync.SyncTracker
import com.tkdpro.di.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileDao: ProfileDao,
    private val goalDao: GoalDao,
    private val competitionDao: CompetitionDao,
    private val sync: SyncTracker,
    private val clock: Clock,
) {
    val profile: Flow<AthleteProfile?> = profileDao.observe()
    val belts: Flow<List<BeltRecord>> = profileDao.observeBelts()
    val goals: Flow<List<PersonalGoal>> = goalDao.observeAll()
    val activeGoals: Flow<List<PersonalGoal>> = goalDao.observeActive()
    val competitions: Flow<List<Competition>> = competitionDao.observeAll()

    fun upcomingCompetitions(fromMillis: Long): Flow<List<Competition>> = competitionDao.observeUpcoming(fromMillis)

    suspend fun saveProfile(edit: (AthleteProfile) -> AthleteProfile) {
        val now = clock.now()
        val current = profileDao.get() ?: AthleteProfile(displayName = "", createdAt = now, updatedAt = now)
        val updated = edit(current).copy(id = AthleteProfile.OWNER_ID, updatedAt = now)
        profileDao.upsert(updated)
        sync.upsert("athlete_profile", updated.id, updated, AthleteProfile.serializer())
    }

    /** Copies the chosen photo into private storage so it does not depend on the gallery. */
    suspend fun setPhoto(uri: Uri) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "profile").apply { mkdirs() }
        val target = File(dir, "photo_${clock.now()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: error("Cannot open image")
        val old = profileDao.get()?.photoPath
        saveProfile { it.copy(photoPath = target.absolutePath) }
        old?.let { File(it).delete() }
    }

    /** True when the stored birth year indicates the owner is under 18 (used for age-appropriate privacy defaults). */
    suspend fun isMinor(): Boolean {
        val birthYear = profileDao.get()?.birthYear ?: return false
        return LocalDate.now().year - birthYear < 18
    }

    suspend fun saveBelt(b: BeltRecord) {
        val id = profileDao.upsertBelt(b).let { if (b.id != 0L) b.id else it }
        sync.upsert("belt_records", id, b.copy(id = id), BeltRecord.serializer())
        val latest = profileDao.allBelts().maxByOrNull { it.achievedOn }
        saveProfile { p -> p.copy(currentBelt = latest?.belt ?: p.currentBelt) }
    }

    suspend fun deleteBelt(b: BeltRecord) {
        profileDao.deleteBelt(b)
        sync.delete("belt_records", b.id)
    }

    suspend fun saveGoal(g: PersonalGoal) {
        val now = clock.now()
        val toSave = g.copy(
            updatedAt = now,
            createdAt = if (g.id == 0L) now else g.createdAt,
            achievedAt = if (g.status == GoalStatus.ACHIEVED) (g.achievedAt ?: now) else null,
        )
        val id = goalDao.upsert(toSave).let { if (g.id != 0L) g.id else it }
        sync.upsert("personal_goals", id, toSave.copy(id = id), PersonalGoal.serializer())
    }

    suspend fun deleteGoal(id: Long) {
        goalDao.delete(id)
        sync.delete("personal_goals", id)
    }

    suspend fun saveCompetition(c: Competition) {
        val toSave = if (c.id == 0L) c.copy(createdAt = clock.now()) else c
        val id = competitionDao.upsert(toSave).let { if (c.id != 0L) c.id else it }
        sync.upsert("competitions", id, toSave.copy(id = id), Competition.serializer())
    }

    suspend fun deleteCompetition(id: Long) {
        competitionDao.delete(id)
        sync.delete("competitions", id)
    }
}
