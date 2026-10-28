package com.tkdpro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tkdpro.core.database.TkdDatabase
import com.tkdpro.core.database.entity.RecoveryEntry
import com.tkdpro.core.database.entity.RunStatus
import com.tkdpro.core.database.entity.RunType
import com.tkdpro.core.database.entity.RunningSession
import com.tkdpro.core.database.entity.SessionStatus
import com.tkdpro.core.database.entity.SyncOp
import com.tkdpro.core.database.entity.SyncQueueItem
import com.tkdpro.core.database.entity.TechniqueCategory
import com.tkdpro.core.database.entity.TechniqueRecord
import com.tkdpro.core.database.entity.TrainingCategory
import com.tkdpro.core.database.entity.TrainingSession
import com.tkdpro.core.database.entity.TrainingTechnique
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.network.BackendClient
import com.tkdpro.core.network.SyncChangeDto
import com.tkdpro.core.security.SecretStore
import com.tkdpro.data.sync.SyncEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SyncEngineTest {
    private lateinit var db: TkdDatabase
    private lateinit var engine: SyncEngine
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TkdDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsRepository(context)
        engine = SyncEngine(db, db.syncDao(), db.syncApplyDao(), BackendClient(settings, SecretStore(context), json), settings, json) { 1_000L }
    }

    @After
    fun tearDown() = db.close()

    private fun <T> payload(serializer: kotlinx.serialization.KSerializer<T>, value: T): JsonObject =
        json.encodeToJsonElement(serializer, value).jsonObject

    private fun change(entity: String, id: Long, body: JsonObject, op: String = "UPSERT", rev: Long = 1) =
        SyncChangeDto(entity, id, op, body, clientUpdatedAt = rev, revision = rev)

    private val webSession = TrainingSession(
        id = -1_700_000_000_000_123, name = "From computer", category = TrainingCategory.SPARRING, status = SessionStatus.COMPLETED,
        startTime = 5_000, durationMin = 75, rpe = 7, createdAt = 1, updatedAt = 1,
    )

    @Test
    fun appliesChildBeforeParentWhenFeedOrderIsReversed() = runTest {
        val tech = TrainingTechnique(id = 3, name = "Dollyo Chagi", category = TechniqueCategory.KICK, createdAt = 0, updatedAt = 0)
        val record = TechniqueRecord(id = -5, sessionId = webSession.id, techniqueId = 3, rightReps = 20, leftReps = 15, createdAt = 2)
        // The session was edited after the record was created, so it comes later in the feed.
        val applied = engine.applyChanges(
            listOf(
                change("technique_records", record.id, payload(TechniqueRecord.serializer(), record), rev = 1),
                change("techniques", 3, payload(TrainingTechnique.serializer(), tech), rev = 2),
                change("training_sessions", webSession.id, payload(TrainingSession.serializer(), webSession), rev = 3),
            ),
        )
        assertEquals(3, applied)
        val stored = db.trainingDao().sessions(0, 10_000).single()
        assertEquals("From computer", stored.name)
        assertEquals(1, db.trainingDao().allRecords().size)
    }

    @Test
    fun pulledChangesDoNotEnterTheSyncQueueAndPendingLocalEditsWin() = runTest {
        db.syncApplyDao().upsertSession(webSession.copy(name = "Edited on phone"))
        db.syncDao().enqueue(SyncQueueItem(entity = "training_sessions", entityId = webSession.id, op = SyncOp.UPSERT, payload = "{}", createdAt = 1))

        engine.applyChanges(listOf(change("training_sessions", webSession.id, payload(TrainingSession.serializer(), webSession))))

        assertEquals("Edited on phone", db.trainingDao().sessions(0, 10_000).single().name)
        assertEquals(1, db.syncDao().pending(10).size)
    }

    @Test
    fun deleteFromComputerCascadesLocally() = runTest {
        db.syncApplyDao().upsertTechnique(TrainingTechnique(id = 3, name = "Ap Chagi", category = TechniqueCategory.KICK, createdAt = 0, updatedAt = 0))
        db.syncApplyDao().upsertSession(webSession)
        db.syncApplyDao().upsertTechniqueRecord(TechniqueRecord(id = 9, sessionId = webSession.id, techniqueId = 3, reps = 10, createdAt = 0))

        engine.applyChanges(listOf(change("training_sessions", webSession.id, JsonObject(emptyMap()), op = "DELETE")))

        assertTrue(db.trainingDao().sessions(0, 10_000).isEmpty())
        assertTrue(db.trainingDao().allRecords().isEmpty())
    }

    @Test
    fun orphanChildIsSkippedWithoutFailingTheBatch() = runTest {
        val orphan = TechniqueRecord(id = -8, sessionId = 404, techniqueId = 404, reps = 5, createdAt = 0)
        val applied = engine.applyChanges(
            listOf(
                change("technique_records", orphan.id, payload(TechniqueRecord.serializer(), orphan)),
                change("training_sessions", webSession.id, payload(TrainingSession.serializer(), webSession), rev = 2),
            ),
        )
        assertEquals(1, applied)
        assertEquals(1, db.trainingDao().sessions(0, 10_000).size)
    }

    @Test
    fun newerCheckInForSameDayReplacesOlderOne() = runTest {
        db.syncApplyDao().upsertRecovery(RecoveryEntry(id = 4, epochDay = 20_000, energy = 2, createdAt = 0, updatedAt = 10))
        val fromComputer = RecoveryEntry(id = -77, epochDay = 20_000, energy = 5, createdAt = 0, updatedAt = 20)

        engine.applyChanges(listOf(change("recovery_entries", fromComputer.id, payload(RecoveryEntry.serializer(), fromComputer))))

        val entry = db.syncApplyDao().recoveryForDay(20_000)
        assertNotNull(entry)
        assertEquals(-77L, entry!!.id)
        assertEquals(5, entry.energy)
    }

    @Test
    fun routeIsRestoredOnlyWhenThePhoneHasNone() = runTest {
        val run = RunningSession(id = 2, type = RunType.EASY, status = RunStatus.COMPLETED, startTime = 0, distanceM = 1000.0, createdAt = 0, updatedAt = 0)
        db.syncApplyDao().upsertRun(run)
        val route = json.parseToJsonElement(
            """{"points":[{"t":0,"lat":41.0,"lon":29.0,"acc":5.0,"seg":0},{"t":1000,"lat":41.001,"lon":29.0,"acc":5.0,"seg":0}],"splits":[]}""",
        ).jsonObject

        engine.applyChanges(listOf(change("run_routes", 2, route)))
        assertEquals(2, db.syncApplyDao().pointCount(2))

        engine.applyChanges(listOf(change("run_routes", 2, route, rev = 2)))
        assertEquals(2, db.syncApplyDao().pointCount(2))
    }

    @Test
    fun enqueueEverythingQueuesExistingRecordsOnce() = runTest {
        db.syncApplyDao().upsertSession(webSession)
        db.syncApplyDao().upsertRun(RunningSession(id = 1, type = RunType.EASY, status = RunStatus.ACTIVE, startTime = 0, createdAt = 0, updatedAt = 0))
        val queued = engine.enqueueEverything()
        // The unfinished run stays on the phone.
        assertEquals(1, queued)
        engine.enqueueEverything()
        assertEquals(1, db.syncDao().pending(10).size)
    }

    @Test
    fun plainHttpOnlyInsideTheHomeNetwork() {
        assertTrue(BackendClient.isAllowedUrl("https://tkd.example.com"))
        assertTrue(BackendClient.isAllowedUrl("http://192.168.1.20:8000"))
        assertTrue(BackendClient.isAllowedUrl("http://10.0.2.2:8000"))
        assertTrue(BackendClient.isAllowedUrl("http://172.20.0.5:8000"))
        assertTrue(BackendClient.isAllowedUrl("http://100.101.102.103:8000"))
        assertTrue(BackendClient.isAllowedUrl("http://my-pc.local:8000"))
        assertFalse(BackendClient.isAllowedUrl("http://tkd.example.com"))
        assertFalse(BackendClient.isAllowedUrl("http://8.8.8.8:8000"))
        assertFalse(BackendClient.isAllowedUrl("http://172.32.0.1"))
        assertFalse(BackendClient.isAllowedUrl("not a url"))
    }
}
