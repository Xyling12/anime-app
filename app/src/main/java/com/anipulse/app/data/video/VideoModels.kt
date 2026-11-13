package com.anipulse.app.data.video

/** Источник видео. */
enum class VideoSourceType { ANILIBRIA, KODIK }

enum class DubType { VOICE, SUB }

/**
 * Один вариант озвучки/субтитров для тайтла.
 * [ref] — непрозрачный для UI идентификатор, по которому источник умеет
 * отдать поток конкретного эпизода (id релиза, ссылка на плеер и т.п.).
 */
data class Dub(
    val id: String,
    val source: VideoSourceType,
    val title: String,        // напр. "AniLibria", "AniDUB", "Crunchyroll"
    val type: DubType,
    val episodesCount: Int,
    val ref: String,
)

/**
 * Готовый к воспроизведению поток эпизода.
 * Если [byQuality] не пуст — играем нативно в ExoPlayer.
 * Иначе, при наличии [embedUrl] — открываем во встроенном плеере (fallback).
 */
data class EpisodeStream(
    val byQuality: Map<Int, String> = emptyMap(),
    val embedUrl: String? = null,
    val opening: Skip? = null,
    val ending: Skip? = null,
) {
    val isNative: Boolean get() = byQuality.isNotEmpty()
    fun bestQuality(): Int? = byQuality.keys.maxOrNull()

    /** Качество по умолчанию: лучшее до 720p включительно (1080p часто лагает на моб.сети). */
    fun defaultQuality(): Int? =
        byQuality.keys.filter { it <= 720 }.maxOrNull() ?: byQuality.keys.minOrNull()
}

/** Таймкоды для пропуска (в секундах). */
data class Skip(val start: Int, val stop: Int) {
    /** Непустой интервал с корректными границами (исключает 0..0 и отрицательные значения). */
    fun isValidInterval(): Boolean = start >= 0 && stop > start

    /**
     * Проверяет, что интервал эндинга действительно относится к концовке серии.
     * На части тайтлов источники или AniSkip отдают 0..0 либо титры в самом начале серии
     * (например, 4..94с при длине 24 мин), из-за чего кнопка «Следующая серия» висит весь эпизод.
     */
    fun isValidEnding(durationMs: Long): Boolean {
        if (durationMs <= 30_000L) return false
        val durationSec = durationMs / 1000L
        if (start <= 0 || stop <= start) return false
        if (start >= durationSec) return false
        return start >= durationSec / 2
    }
}
