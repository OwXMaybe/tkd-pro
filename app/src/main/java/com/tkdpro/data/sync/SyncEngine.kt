package com.tkdpro.data.sync

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.room.withTransaction
import com.tkdpro.core.database.TkdDatabase
import com.tkdpro.core.database.dao.SyncApplyDao
import com.tkdpro.core.database.dao.SyncDao
import com.tkdpro.core.database.entity.SyncOp
import com.tkdpro.core.database.entity.SyncQueueItem
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.network.BackendClient
import com.tkdpro.core.network.SyncChangeDto
import com.tkdpro.core.network.SyncItemDto
import com.tkdpro.core.network.SyncPushRequest
import com.tkdpro.di.Clock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two-way sync with the owner's server (normally TKD PRO running on their computer).
 *
 * 1. push: local changes from the queue, in batches (the server keeps the newest copy of each record);
 * 2. pull: every change after the stored cursor, made by any device (web app on the computer, or this
 *    phone's own echoes), applied parents-first. Rows with an unsent local change are skipped, so a
 *    local edit is never overwritten before it reaches the server.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val db: TkdDatabase,
    private val queue: SyncDao,
    private val apply: SyncApplyDao,
    private val client: BackendClient,
    private val settings: SettingsRepository,
    private val json: Json,
    private val clock: Clock,
) {
    /** Queues every syncable record once, e.g. when sync is first turned on or a new computer is paired. */
    suspend fun enqueueEverything(): Int {
        val now = clock.now()
        val items = Sync.ENTITIES.flatMap { entity -> snapshotOf(entity, now) }
        items.chunked(500).forEach { queue.enqueueAll(it) }
        return items.size
    }

    private suspend fun <T> snapshotOf(entity: SyncEntity<T>, now: Long): List<SyncQueueItem> =
        entity.snapshot(apply).map { (id, value) ->
            SyncQueueItem(entity = entity.name, entityId = id, op = SyncOp.UPSERT, payload = json.encodeToString(entity.serializer, value), createdAt = now)
        }

    suspend fun push(): Result<Unit> {
        while (true) {
            val batch = queue.pending(BATCH_SIZE)
            if (batch.isEmpty()) return Result.success(Unit)
            val request = SyncPushRequest(
                batch.map {
                    SyncItemDto(
                        entity = it.entity,
                        entityId = it.entityId,
                        op = it.op.name,
                        payload = json.parseToJsonElement(it.payload) as JsonElement,
                        clientUpdatedAt = it.createdAt,
                    )
                },
            )
            val ids = batch.map { it.id }
            val result = client.call { it.syncPush(request) }
            if (result.isFailure) {
                val e = result.exceptionOrNull()!!
                queue.markFailed(ids, e.message ?: e.javaClass.simpleName)
                return Result.failure(e)
            }
            queue.remove(ids)
        }
    }

    suspend fun pull(): Result<Int> {
        var cursor = settings.current().syncCursor
        val changes = mutableListOf<SyncChangeDto>()
        while (true) {
            val page = client.call { it.syncChanges(cursor, PAGE_SIZE) }.getOrElse { return Result.failure(it) }
            changes += page.items
            cursor = page.nextAfter
            if (!page.hasMore) break
        }
        if (changes.isEmpty()) return Result.success(0)
        val applied = applyChanges(changes)
        settings.setSyncCursor(cursor)
        return Result.success(applied)
    }

    /** Applies the newest change per record; upserts parents-first, then deletions. */
    internal suspend fun applyChanges(changes: List<SyncChangeDto>): Int {
        val pending = queue.pendingKeys().toSet()
        val latest = changes
            .filter { it.entity in Sync.byName && "${it.entity}:${it.entityId}" !in pending }
            .associateBy { it.entity to it.entityId }
            .values
        val (deletes, upserts) = latest.partition { it.op == SyncOp.DELETE.name }
        // Each change runs in its own transaction: in SQLite on Android a failed nested statement marks
        // the enclosing transaction as failed, so one unusable row would otherwise undo the whole batch.
        var applied = 0
        upserts.sortedBy { Sync.order.getValue(it.entity) }.forEach { change ->
            if (applyUpsert(Sync.byName.getValue(change.entity), change)) applied++
        }
        deletes.forEach { change ->
            Sync.byName.getValue(change.entity).delete(apply, change.entityId)
            applied++
        }
        return applied
    }

    private suspend fun <T> applyUpsert(entity: SyncEntity<T>, change: SyncChangeDto): Boolean = try {
        val value = json.decodeFromJsonElement(entity.serializer as KSerializer<T>, change.payload)
        db.withTransaction { entity.upsert(apply, change.entityId, value) }
        true
    } catch (e: SQLiteConstraintException) {
        // e.g. a child whose parent was deleted on this phone, or an analysis whose video exists only elsewhere.
        Log.w(TAG, "Skipped ${change.entity}:${change.entityId}: ${e.message}")
        false
    } catch (e: IllegalArgumentException) {
        // Payload from a newer app version that this one cannot read.
        Log.w(TAG, "Unreadable ${change.entity}:${change.entityId}: ${e.message}")
        false
    }

    private companion object {
        const val TAG = "SyncEngine"
        const val BATCH_SIZE = 200
        const val PAGE_SIZE = 1000
    }
}
