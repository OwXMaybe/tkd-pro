package com.tkdpro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tkdpro.core.database.TkdDatabase
import com.tkdpro.core.database.entity.GpsPoint
import com.tkdpro.core.database.entity.RecoveryEntry
import com.tkdpro.core.database.entity.RunStatus
import com.tkdpro.core.database.entity.RunType
import com.tkdpro.core.database.entity.RunningSession
import com.tkdpro.core.database.entity.SessionStatus
import com.tkdpro.core.database.entity.TechniqueCategory
import com.tkdpro.core.database.entity.TechniqueRecord
import com.tkdpro.core.database.entity.TrainingCategory
import com.tkdpro.core.database.entity.TrainingSession
import com.tkdpro.core.database.entity.TrainingTechnique
import com.tkdpro.data.repository.toRoutePoint
import com.tkdpro.core.domain.run.RouteMetrics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DatabaseTest {
    private lateinit var db: TkdDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TkdDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun session(name: String, start: Long, status: SessionStatus = SessionStatus.COMPLETED) =
        TrainingSession(name = name, category = TrainingCategory.TECHNICAL, status = status, startTime = start, durationMin = 60, rpe = 6, createdAt = 0, updatedAt = 0)

    @Test
    fun techniqueUsageAggregatesCompletedSessionsOnly() = runTest {
        val tech = db.techniqueDao().upsert(TrainingTechnique(name = "Dollyo Chagi", category = TechniqueCategory.KICK, createdAt = 0, updatedAt = 0))
        val done = db.trainingDao().insertSession(session("A", 1_000))
        val planned = db.trainingDao().insertSession(session("B", 2_000, SessionStatus.PLANNED))
        db.trainingDao().insertRecord(TechniqueRecord(sessionId = done, techniqueId = tech, rightReps = 30, leftReps = 20, reps = 50, createdAt = 0))
        db.trainingDao().insertRecord(TechniqueRecord(sessionId = planned, techniqueId = tech, reps = 99, createdAt = 0))

        val usage = db.trainingDao().observeTechniqueUsage(0, 10_000).first().single()
        assertEquals(50, usage.totalReps)
        assertEquals(30, usage.rightReps)
        assertEquals(20, usage.leftReps)
        assertEquals(1, usage.sessions)
    }

    @Test
    fun duplicateSessionDetectionAndCascadeDelete() = runTest {
        val tech = db.techniqueDao().upsert(TrainingTechnique(name = "Ap Chagi", category = TechniqueCategory.KICK, createdAt = 0, updatedAt = 0))
        val id = db.trainingDao().insertSession(session("Morning", 5_000))
        db.trainingDao().insertRecord(TechniqueRecord(sessionId = id, techniqueId = tech, reps = 10, createdAt = 0))
        assertEquals(1, db.trainingDao().countDuplicates("Morning", 5_000, TrainingCategory.TECHNICAL.name))
        db.trainingDao().deleteSession(id)
        assertEquals(0, db.trainingDao().allRecords().size)
    }

    @Test
    fun gpsPointsIgnoreDuplicatesAndKeepOrder() = runTest {
        val run = db.runningDao().insert(RunningSession(type = RunType.EASY, status = RunStatus.ACTIVE, startTime = 0, createdAt = 0, updatedAt = 0))
        fun p(t: Long, metersNorth: Double) = GpsPoint(runId = run, timeMs = t, latitude = 41.0 + metersNorth / 111_195.0, longitude = 29.0, altitudeM = null, accuracyM = 5f, speedMps = null, segment = 0)
        db.runningDao().insertPoint(p(0, 0.0))
        db.runningDao().insertPoint(p(3000, 10.0))
        db.runningDao().insertPoint(p(3000, 999.0)) // duplicate timestamp ignored
        db.runningDao().insertPoint(p(6000, 20.0))
        val points = db.runningDao().points(run)
        assertEquals(3, points.size)
        assertEquals(20.0, RouteMetrics.totalDistanceM(points.map { it.toRoutePoint() }), 0.5)
        assertEquals(run, db.runningDao().unfinished()?.id)
    }

    @Test
    fun recoveryIsUniquePerDay() = runTest {
        val dao = db.recoveryDao()
        val id = dao.upsert(RecoveryEntry(epochDay = 20_000, sleepHours = 7.5, createdAt = 0, updatedAt = 0))
        dao.upsert(RecoveryEntry(id = id, epochDay = 20_000, sleepHours = 8.0, createdAt = 0, updatedAt = 1))
        val all = dao.all()
        assertEquals(1, all.size)
        assertEquals(8.0, all.single().sleepHours!!, 0.0)
        assertNull(dao.forDay(20_001))
    }
}
