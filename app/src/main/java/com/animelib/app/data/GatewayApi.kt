package com.animelib.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

/** Свежая серия AniLibria из ленты «Обновления» (эндпоинт шлюза anilibria-updates). */
@Serializable
data class AnilibriaUpdate(
    val title: String = "",
    @SerialName("titleEn") val titleEn: String? = null,
    val episode: Int? = null,
    @SerialName("episodesTotal") val episodesTotal: Int? = null,
    @SerialName("freshAt") val freshAt: String? = null,
    val poster: String? = null,
)

@Serializable
data class RegisterRequest(val nick: String, val email: String, val password: String)

@Serializable
data class LoginRequest(val login: String, val password: String)

@Serializable
data class AuthResponse(
    val token: String? = null,
    val nick: String? = null,
    val email: String? = null,
    val error: String? = null,
)

@Serializable
data class MeResponse(
    val nick: String? = null,
    val email: String? = null,
    val avatar: Int = 0,
    val linked: List<String> = emptyList(),
    val admin: Boolean = false,
    val emailVerified: Boolean = true,
    val error: String? = null,
)

@Serializable
data class VerifyRequest(val code: String)

@Serializable
data class AdminDeleteChatRequest(val id: Long)

@Serializable
data class AdminBanRequest(val nick: String, val hours: Int)

@Serializable
data class ChatMessage(
    val id: Long = 0,
    val userId: Long = 0,
    val nick: String = "",
    val avatar: Int = 0,
    val text: String = "",
    val at: Long = 0,
    val replyTo: ChatReply? = null,
    /** Комментарий-спойлер: клиент блюрит до тапа. */
    val spoiler: Boolean = false,
)

/** Цитата, на которую отвечает сообщение. */
@Serializable
data class ChatReply(
    val id: Long = 0,
    val nick: String = "",
    val text: String = "",
)

@Serializable
data class RatingResponse(
    val avg: Double? = null,
    val count: Int = 0,
    val my: Int? = null,
    val error: String? = null,
)

/** Краткий рейтинг AniPulse для батч-запроса (бейджи на постерах). */
@Serializable
data class RatingBrief(
    val avg: Double? = null,
    val count: Int = 0,
)

@Serializable
data class TextRequest(val text: String, val replyTo: Long? = null)

/** animeId: "16498" — комментарии тайтла, "16498:ep2" — комментарии серии. */
@Serializable
data class CommentRequest(val animeId: String, val text: String)

@Serializable
data class RatingRequest(val animeId: Long, val score: Int)

@Serializable
data class AvatarRequest(val avatar: Int)

/** Диалог ЛС в списке чатов. */
@Serializable
data class DmThread(
    val withNick: String = "",
    val withAvatar: Int = 0,
    val lastText: String = "",
    val lastAt: Long = 0,
    val unread: Int = 0,
)

/** Сообщение ЛС. */
@Serializable
data class DmMessage(
    val id: Long = 0,
    val from: String = "",
    val fromAvatar: Int = 0,
    val to: String = "",
    val text: String = "",
    val at: Long = 0,
)

@Serializable
data class DmSendRequest(val to: String, val text: String)

/** Уведомление (mention / dm). */
@Serializable
data class Notification(
    val id: Long = 0,
    val type: String = "",
    val from: String = "",
    val text: String = "",
    val source: String? = null,
    val at: Long = 0,
    val read: Boolean = false,
)

/** Публичная карточка пользователя. */
@Serializable
data class UserCard(
    val nick: String = "",
    val avatar: Int = 0,
    val bio: String = "",
    val createdAt: Long? = null,
    val lastSeen: Long? = null,
    val online: Boolean = false,
    val favoriteGenre: String? = null,
    val stats: UserStats? = null,
    val commentsCount: Int = 0,
    val ratingsCount: Int = 0,
    val friendState: String? = null,
)

@Serializable
data class UserStats(
    val watchedEpisodes: Int = 0,
    val watchMinutes: Int = 0,
    val startedTitles: Int = 0,
    val favoritesCount: Int = 0,
)

@Serializable
data class ProfileUpdateRequest(
    val bio: String? = null,
    val favoriteGenre: String? = null,
    val stats: UserStats? = null,
)

@Serializable
data class FriendsResponse(
    val friends: List<UserCard> = emptyList(),
    val incoming: List<UserCard> = emptyList(),
)

@Serializable
data class FriendActionRequest(val nick: String)

@Serializable
data class BugReportRequest(
    val text: String,
    val contact: String? = null,
    val device: String? = null,
    val osVersion: String? = null,
)

@Serializable
data class ForgotRequest(val email: String)

@Serializable
data class ResetRequest(val email: String, val code: String, val password: String)

@Serializable
data class FriendActionResponse(val state: String = "", val error: String? = null)

/** Собственные эндпоинты шлюза (не прокси). */
interface GatewayApi {
    @GET("anilibria-updates")
    suspend fun anilibriaUpdates(): List<AnilibriaUpdate>

    @retrofit2.http.POST("auth/register")
    suspend fun register(@retrofit2.http.Body body: RegisterRequest): AuthResponse

    @retrofit2.http.POST("auth/login")
    suspend fun login(@retrofit2.http.Body body: LoginRequest): AuthResponse

    @GET("auth/me")
    suspend fun me(@retrofit2.http.Header("Authorization") bearer: String): MeResponse

    @GET("chat")
    suspend fun chat(@retrofit2.http.Query("after") after: Long = 0): List<ChatMessage>

    @retrofit2.http.POST("chat")
    suspend fun sendChat(
        @retrofit2.http.Header("Authorization") bearer: String,
        @retrofit2.http.Body body: TextRequest,
    ): ChatMessage

    @GET("comments")
    suspend fun comments(@retrofit2.http.Query("animeId") animeId: String): List<ChatMessage>

    @retrofit2.http.POST("comments")
    suspend fun sendComment(
        @retrofit2.http.Header("Authorization") bearer: String,
        @retrofit2.http.Body body: CommentRequest,
    ): ChatMessage

    /** Батч: ids="16498,1735" → { "16498": {avg,count}, ... } — только тайтлы с оценками. */
    @GET("ratings")
    suspend fun ratings(@retrofit2.http.Query("ids") ids: String): Map<String, RatingBrief>

    @GET("rating")
    suspend fun rating(
        @retrofit2.http.Query("animeId") animeId: Long,
        @retrofit2.http.Header("Authorization") bearer: String? = null,
    ): RatingResponse

    @retrofit2.http.POST("rating")
    suspend fun sendRating(
        @retrofit2.http.Header("Authorization") bearer: String,
        @retrofit2.http.Body body: RatingRequest,
    ): RatingResponse

    @GET("dm/list")
    suspend fun dmList(@retrofit2.http.Header("Authorization") bearer: String): List<DmThread>

    @GET("dm")
    suspend fun dmThread(
        @retrofit2.http.Header("Authorization") bearer: String,
        @retrofit2.http.Query("with") withNick: String,
        @retrofit2.http.Query("after") after: Long = 0,
    ): List<DmMessage>

    @retrofit2.http.POST("dm")
    suspend fun dmSend(
        @retrofit2.http.Header("Authorization") bearer: String,
        @retrofit2.http.Body body: DmSendRequest,
    ): DmMessage

    @GET("notifications")
    suspend fun notifications(
        @retrofit2.http.Header("Authorization") bearer: String,
        @retrofit2.http.Query("after") after: Long = 0,
    ): List<Notification>

    @GET("user")
    suspend fun userCard(
        @retrofit2.http.Header("Authorization") bearer: String?,
        @retrofit2.http.Query("nick") nick: String,
    ): UserCard

    @retrofit2.http.POST("profile")
    suspend fun updateProfile(
        @retrofit2.http.Header("Authorization") bearer: String,
        @retrofit2.http.Body body: ProfileUpdateRequest,
    ): kotlinx.serialization.json.JsonObject

    @GET("friends")
    suspend fun friends(@retrofit2.http.Header("Authorization") bearer: String): FriendsResponse

    @retrofit2.http.POST("friends/{action}")
    suspend fun friendAction(
        @retrofit2.http.Header("Authorization") bearer: String,
        @retrofit2.http.Path("action") action: String,
        @retrofit2.http.Body body: FriendActionRequest,
    ): FriendActionResponse

    @retrofit2.http.POST("admin/delete-chat")
    suspend fun adminDeleteChat(
        @retrofit2.http.Header("Authorization") bearer: String,
        @retrofit2.http.Body body: AdminDeleteChatRequest,
    ): kotlinx.serialization.json.JsonObject

    @retrofit2.http.POST("admin/ban")
    suspend fun adminBan(
        @retrofit2.http.Header("Authorization") bearer: String,
        @retrofit2.http.Body body: AdminBanRequest,
    ): kotlinx.serialization.json.JsonObject

    @retrofit2.http.POST("notifications/read")
    suspend fun markNotificationsRead(@retrofit2.http.Header("Authorization") bearer: String): kotlinx.serialization.json.JsonObject

    @retrofit2.http.POST("auth/verify")
    suspend fun verifyEmail(
        @retrofit2.http.Header("Authorization") bearer: String,
        @retrofit2.http.Body body: VerifyRequest,
    ): kotlinx.serialization.json.JsonObject

    @retrofit2.http.POST("auth/resend")
    suspend fun resendCode(@retrofit2.http.Header("Authorization") bearer: String): kotlinx.serialization.json.JsonObject

    @retrofit2.http.POST("auth/forgot")
    suspend fun forgotPassword(@retrofit2.http.Body body: ForgotRequest): kotlinx.serialization.json.JsonObject

    /** bearer опционален — баг можно отправить и гостю. */
    @retrofit2.http.POST("bugreport")
    suspend fun sendBugReport(
        @retrofit2.http.Header("Authorization") bearer: String?,
        @retrofit2.http.Body body: BugReportRequest,
    ): kotlinx.serialization.json.JsonObject

    /** Успешный reset сразу логинит: сервер возвращает токен. */
    @retrofit2.http.POST("auth/reset")
    suspend fun resetPassword(@retrofit2.http.Body body: ResetRequest): AuthResponse

    @retrofit2.http.POST("auth/logoutall")
    suspend fun logoutAll(@retrofit2.http.Header("Authorization") bearer: String): AuthResponse

    @retrofit2.http.POST("avatar")
    suspend fun setAvatar(
        @retrofit2.http.Header("Authorization") bearer: String,
        @retrofit2.http.Body body: AvatarRequest,
    ): AuthResponse
}
