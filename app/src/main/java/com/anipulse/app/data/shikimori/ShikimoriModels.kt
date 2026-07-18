package com.anipulse.app.data.shikimori

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShikiAnime(
    val id: Long,
    val name: String,
    val russian: String? = null,
    val image: ShikiImage? = null,
    val kind: String? = null,
    val score: String? = null,
    val status: String? = null,
    val episodes: Int = 0,
    @SerialName("episodes_aired") val episodesAired: Int = 0,
    @SerialName("aired_on") val airedOn: String? = null,
)

@Serializable
data class ShikiImage(
    val original: String? = null,
    val preview: String? = null,
)

@Serializable
data class ShikiGenre(
    val id: Long,
    val name: String,
    val russian: String? = null,
    @SerialName("entry_type") val entryType: String? = null,
)

/** Запись календаря Shikimori: когда выйдет следующая серия онгоинга. */
@Serializable
data class ShikiCalendarEntry(
    @SerialName("next_episode") val nextEpisode: Int = 0,
    @SerialName("next_episode_at") val nextEpisodeAt: String? = null,
    val anime: ShikiAnime,
)

@Serializable
data class ShikiAnimeDetails(
    val id: Long,
    val name: String,
    val russian: String? = null,
    val image: ShikiImage? = null,
    val kind: String? = null,
    val score: String? = null,
    val status: String? = null,
    val episodes: Int = 0,
    @SerialName("episodes_aired") val episodesAired: Int = 0,
    @SerialName("aired_on") val airedOn: String? = null,
    val description: String? = null,
    val genres: List<ShikiGenre> = emptyList(),
    val duration: Int = 0,
    val rating: String? = null,
)

// Картинки Shikimori тоже блокируются в РФ → грузим через шлюз (см. Api.SHIKIMORI_IMAGES).
// Если у тайтла нет постера, Shikimori отдаёт заглушку missing_*/globals → возвращаем null,
// чтобы UI показал свой аккуратный плейсхолдер вместо чужого «404 not found».
private fun String.isMissingImage() = contains("missing") || contains("/globals/")

fun ShikiImage?.posterUrl(): String? =
    this?.original?.takeUnless { it.isMissingImage() }?.let { com.anipulse.app.data.Api.SHIKIMORI_IMAGES + it }

fun ShikiImage?.previewUrl(): String? =
    this?.preview?.takeUnless { it.isMissingImage() }?.let { com.anipulse.app.data.Api.SHIKIMORI_IMAGES + it }

/**
 * Постер тайтла с автоподбором: если у Shikimori нет картинки — шлюз найдёт её
 * в открытом источнике (Jikan/MAL) по тому же id. Возвращает всегда рабочий URL.
 */
fun posterOf(id: Long, image: ShikiImage?): String =
    image.posterUrl() ?: (com.anipulse.app.data.Api.GATEWAY + "poster/$id")

/**
 * Лёгкий постер для сеток/лент: preview Shikimori в разы меньше оригинала,
 * на медленной сети грузится заметно быстрее. Для крупных экранов — posterOf.
 */
fun posterPreviewOf(id: Long, image: ShikiImage?): String =
    image.previewUrl() ?: (com.anipulse.app.data.Api.GATEWAY + "poster/$id")
