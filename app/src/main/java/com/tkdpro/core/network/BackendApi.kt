package com.tkdpro.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

@Serializable
data class HealthResponse(
    val status: String,
    @SerialName("ai_configured") val aiConfigured: Boolean,
    @SerialName("motion_available") val motionAvailable: Boolean,
    val version: String = "",
)

@Serializable
data class ChatTurn(val role: String, val content: String)

@Serializable
data class CoachChatRequest(
    val messages: List<ChatTurn>,
    val context: JsonObject,
    val locale: String,
)

@Serializable
data class CoachChatResponse(val reply: String, val model: String)

@Serializable
data class WeeklyReportRequest(val context: JsonObject, val locale: String)

@Serializable
data class WeeklyReportResponse(val report: String, val model: String)

@Serializable
data class MotionInterpretationRequest(
    val technique: String,
    val result: JsonElement,
    val locale: String,
)

@Serializable
data class MotionInterpretationResponse(val interpretation: String, val model: String)

@Serializable
data class SyncItemDto(
    val entity: String,
    @SerialName("entity_id") val entityId: Long,
    val op: String,
    val payload: JsonElement,
    @SerialName("client_updated_at") val clientUpdatedAt: Long,
)

@Serializable
data class SyncPushRequest(val items: List<SyncItemDto>)

@Serializable
data class SyncPushResponse(val accepted: Int)

@Serializable
data class SyncChangeDto(
    val entity: String,
    @SerialName("entity_id") val entityId: Long,
    val op: String,
    val payload: JsonObject,
    @SerialName("client_updated_at") val clientUpdatedAt: Long,
    val revision: Long,
)

@Serializable
data class SyncChangesResponse(
    val items: List<SyncChangeDto>,
    @SerialName("next_after") val nextAfter: Long,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
data class PairRequest(
    val code: String,
    @SerialName("device_name") val deviceName: String,
    val kind: String = "phone",
)

@Serializable
data class PairResponse(val token: String? = null)

@Serializable
data class ServerMotionResponse(val result: JsonElement, val frames: JsonElement)

/** The owner's own FastAPI backend (see /backend). All endpoints require the owner bearer token. */
interface BackendApi {
    @GET("api/v1/health")
    suspend fun health(): HealthResponse

    @POST("api/v1/coach/chat")
    suspend fun coachChat(@Body body: CoachChatRequest): CoachChatResponse

    @POST("api/v1/coach/weekly-report")
    suspend fun weeklyReport(@Body body: WeeklyReportRequest): WeeklyReportResponse

    @POST("api/v1/ai/motion-interpretation")
    suspend fun motionInterpretation(@Body body: MotionInterpretationRequest): MotionInterpretationResponse

    @POST("api/v1/sync/push")
    suspend fun syncPush(@Body body: SyncPushRequest): SyncPushResponse

    @GET("api/v1/sync/changes")
    suspend fun syncChanges(@Query("after") after: Long, @Query("limit") limit: Int): SyncChangesResponse

    /** Unauthenticated: exchanges the 6-digit code shown on the computer for this phone's token. */
    @POST("api/v1/auth/pair")
    suspend fun pair(@Body body: PairRequest): PairResponse

    @Multipart
    @POST("api/v1/motion/analyze")
    suspend fun analyzeMotion(
        @Part video: MultipartBody.Part,
        @Part("technique") technique: RequestBody,
        @Part("side") side: RequestBody,
    ): ServerMotionResponse
}
