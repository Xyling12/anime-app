package com.animelib.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animelib.app.data.AnimeRepository
import com.animelib.app.data.AnilibriaUpdate
import com.animelib.app.data.shikimori.ShikiCalendarEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

/** Секция расписания: один день с релизами. */
data class DaySection(
    val date: LocalDate,
    val label: String, // «Сегодня, 8 июля» / «Завтра, …» / «Четверг, …»
    val entries: List<ShikiCalendarEntry>,
)

data class ScheduleState(
    val tab: Int = 0, // 0 = расписание, 1 = обновления
    val days: List<DaySection> = emptyList(),
    val updates: List<AnilibriaUpdate> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

private val MSK: ZoneId = ZoneId.of("Europe/Moscow")
private val MONTHS = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)
private val WEEKDAYS = listOf(
    "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье",
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repo: AnimeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleState())
    val state: StateFlow<ScheduleState> = _state.asStateFlow()

    init { load() }

    fun setTab(tab: Int) = _state.update { it.copy(tab = tab) }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val calendar = async { runCatching { repo.calendar() }.getOrDefault(emptyList()) }
            val updates = async { runCatching { repo.anilibriaUpdates() }.getOrDefault(emptyList()) }
            val cal = calendar.await()
            val upd = updates.await()
            if (cal.isEmpty() && upd.isEmpty()) {
                _state.update { it.copy(isLoading = false, error = "Не удалось загрузить расписание") }
                return@launch
            }
            _state.update { it.copy(days = groupByDay(cal), updates = upd, isLoading = false) }
        }
    }

    private fun groupByDay(entries: List<ShikiCalendarEntry>): List<DaySection> {
        val today = LocalDate.now(MSK)
        return entries
            .mapNotNull { e ->
                val at = e.nextEpisodeAt ?: return@mapNotNull null
                val dt = runCatching { OffsetDateTime.parse(at).atZoneSameInstant(MSK) }.getOrNull()
                    ?: return@mapNotNull null
                Triple(dt.toLocalDate(), dt, e)
            }
            .filter { (date, _, _) -> !date.isBefore(today) }
            .sortedBy { it.second }
            .groupBy({ it.first }, { it.third })
            .map { (date, list) ->
                val label = when (date) {
                    today -> "Сегодня"
                    today.plusDays(1) -> "Завтра"
                    else -> WEEKDAYS[date.dayOfWeek.value - 1]
                }
                DaySection(date, "$label, ${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}", list)
            }
    }

    /** «HH:mm (москва)» для карточки расписания. */
    fun timeOf(entry: ShikiCalendarEntry): String? =
        entry.nextEpisodeAt
            ?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(MSK) }.getOrNull() }
            ?.let { "%d:%02d (москва)".format(it.hour, it.minute) }

    /** «сегодня/вчера, HH:mm» для ленты обновлений. */
    fun freshLabel(update: AnilibriaUpdate): String? {
        val dt = update.freshAt
            ?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(MSK) }.getOrNull() }
            ?: return null
        val today = LocalDate.now(MSK)
        val day = when (dt.toLocalDate()) {
            today -> "сегодня"
            today.minusDays(1) -> "вчера"
            else -> "${dt.dayOfMonth} ${MONTHS[dt.monthValue - 1]}"
        }
        return "$day, %d:%02d".format(dt.hour, dt.minute)
    }

    /** Тайтл AniLibria → id Shikimori (поиском по имени), затем открыть страницу. */
    fun openUpdate(update: AnilibriaUpdate, onFound: (Long) -> Unit) {
        viewModelScope.launch {
            val query = update.titleEn ?: update.title
            val id = runCatching { repo.search(query).firstOrNull()?.id }.getOrNull()
                ?: runCatching { repo.search(update.title).firstOrNull()?.id }.getOrNull()
            if (id != null) onFound(id)
        }
    }
}
