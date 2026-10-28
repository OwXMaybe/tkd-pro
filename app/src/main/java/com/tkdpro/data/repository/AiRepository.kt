package com.tkdpro.data.repository

import com.tkdpro.core.database.dao.AiDao
import com.tkdpro.core.database.entity.AiReport
import com.tkdpro.core.database.entity.AiReportType
import com.tkdpro.core.database.entity.ChatMessage
import com.tkdpro.core.database.entity.ChatRole
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.network.BackendClient
import com.tkdpro.core.network.ChatTurn
import com.tkdpro.core.network.CoachChatRequest
import com.tkdpro.core.network.WeeklyReportRequest
import com.tkdpro.data.ai.CoachContextBuilder
import com.tkdpro.data.ai.DataSource
import com.tkdpro.data.sync.SyncTracker
import com.tkdpro.di.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val dao: AiDao,
    private val contextBuilder: CoachContextBuilder,
    private val client: BackendClient,
    private val settings: SettingsRepository,
    private val json: Json,
    private val clock: Clock,
    private val sync: SyncTracker,
) {
    /** Chat and reports are synced so the conversation continues on the computer. */
    private suspend fun saveMessage(m: ChatMessage): Long {
        val id = dao.insertMessage(m)
        sync.upsert("chat_messages", id, m.copy(id = id), ChatMessage.serializer())
        return id
    }
    val messages: Flow<List<ChatMessage>> = dao.observeMessages()
    val reports: Flow<List<AiReport>> = dao.observeReports()
    val latestWeekly: Flow<AiReport?> = dao.observeLatestWeekly()

    fun decodeSources(raw: String?): List<DataSource> =
        raw?.let { runCatching { json.decodeFromString(ListSerializer(DataSource.serializer()), it) }.getOrNull() } ?: emptyList()

    /**
     * Sends a question to the AI Coach with the permitted data context (last [CONTEXT_DAYS] days).
     * The user's message is stored first so it is never lost; a failed answer is stored as failed.
     */
    suspend fun ask(question: String): Result<ChatMessage> {
        val ai = settings.current().ai
        if (!ai.enabled) return Result.failure(AiNotPermittedException())
        saveMessage(ChatMessage(role = ChatRole.USER, content = question, createdAt = clock.now()))

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.minusDays(CONTEXT_DAYS - 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val context = contextBuilder.build(ai, from, to, zone)

        val history = dao.recentMessages(HISTORY_TURNS).reversed()
            .filter { !it.failed }
            .map { ChatTurn(if (it.role == ChatRole.USER) "user" else "assistant", it.content) }

        val result = client.call {
            it.coachChat(CoachChatRequest(history, context.json, Locale.getDefault().toLanguageTag()))
        }
        val sourcesJson = json.encodeToString(ListSerializer(DataSource.serializer()), context.sources)
        return result.map { resp ->
            val msg = ChatMessage(role = ChatRole.ASSISTANT, content = resp.reply, dataSourcesJson = sourcesJson, createdAt = clock.now())
            msg.copy(id = saveMessage(msg))
        }.onFailure {
            saveMessage(ChatMessage(role = ChatRole.ASSISTANT, content = "", failed = true, createdAt = clock.now()))
        }
    }

    /** Generates and stores a weekly development report for the current ISO week. */
    suspend fun generateWeeklyReport(): Result<AiReport> {
        val ai = settings.current().ai
        if (!ai.enabled) return Result.failure(AiNotPermittedException())
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // Two weeks of context so the report can compare with the previous week.
        val from = today.minusDays(13).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val context = contextBuilder.build(ai, from, to, zone)
        return client.call {
            it.weeklyReport(WeeklyReportRequest(context.json, Locale.getDefault().toLanguageTag()))
        }.map { resp ->
            val report = AiReport(
                type = AiReportType.WEEKLY,
                periodStart = from,
                periodEnd = to,
                content = resp.report,
                dataSourcesJson = json.encodeToString(ListSerializer(DataSource.serializer()), context.sources),
                model = resp.model,
                createdAt = clock.now(),
            )
            val id = dao.insertReport(report)
            sync.upsert("ai_reports", id, report.copy(id = id), AiReport.serializer())
            report.copy(id = id)
        }
    }

    suspend fun clearChat() {
        val ids = dao.messageIds()
        dao.clearChat()
        ids.forEach { sync.delete("chat_messages", it) }
    }

    suspend fun deleteReport(id: Long) {
        dao.deleteReport(id)
        sync.delete("ai_reports", id)
    }

    private companion object {
        const val CONTEXT_DAYS = 90L
        const val HISTORY_TURNS = 12
    }
}
