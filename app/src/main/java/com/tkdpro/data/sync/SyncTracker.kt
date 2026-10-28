package com.tkdpro.data.sync

import com.tkdpro.core.database.dao.SyncDao
import com.tkdpro.core.database.entity.SyncOp
import com.tkdpro.core.database.entity.SyncQueueItem
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.di.Clock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records local changes in the sync queue. The queue is only filled while sync is enabled, and the
 * local database remains the source of truth: nothing is removed locally when a push fails.
 */
@Singleton
class SyncTracker @Inject constructor(
    private val dao: SyncDao,
    private val settings: SettingsRepository,
    private val json: Json,
    private val clock: Clock,
    private val scheduler: SyncScheduler,
) {
    suspend fun <T> upsert(entity: String, id: Long, value: T, serializer: KSerializer<T>) {
        if (!settings.current().syncEnabled) return
        dao.enqueue(
            SyncQueueItem(
                entity = entity,
                entityId = id,
                op = SyncOp.UPSERT,
                payload = json.encodeToString(serializer, value),
                createdAt = clock.now(),
            ),
        )
        scheduler.schedule()
    }

    suspend fun delete(entity: String, id: Long) {
        if (!settings.current().syncEnabled) return
        dao.enqueue(SyncQueueItem(entity = entity, entityId = id, op = SyncOp.DELETE, payload = "{}", createdAt = clock.now()))
        scheduler.schedule()
    }
}
