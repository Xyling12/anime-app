/**
 * TypeScript-типы, зеркалящие модели бэкенда (Kotlin GatewayApi.kt + Shikimori).
 * Держим синхронно с сервером: если меняется форма ответа — правим здесь.
 */

export interface ShikiImage {
  original?: string;
  preview?: string;
}

/** Тайтл в списках каталога/лент. */
export interface ShikiAnime {
  id: number;
  name: string;
  russian?: string | null;
  image?: ShikiImage | null;
  score?: string | null;
  episodes?: number;
  episodes_aired?: number;
  kind?: string | null;
  status?: string | null;
  aired_on?: string | null;
  released_on?: string | null;
}

/** Детальная карточка тайтла. */
export interface ShikiAnimeDetails extends ShikiAnime {
  description?: string | null;
  english?: string[] | null;
  japanese?: string[] | null;
  synonyms?: string[] | null;
  license_name_ru?: string | null;
  genres?: { id: number; russian?: string; name: string }[];
  aired_on?: string | null;
}

/** Запись расписания (Эфир → Расписание). */
export interface ShikiCalendarEntry {
  next_episode: number;
  next_episode_at?: string | null;
  anime: ShikiAnime;
}

/** Свежая серия AniLibria (Эфир → Обновления). */
export interface AnilibriaUpdate {
  title: string;
  titleEn?: string | null;
  episode?: number | null;
  episodesTotal?: number | null;
  freshAt?: string | null;
  poster?: string | null;
}

/** Связанный тайтл из `/api/animes/{id}/related`. `anime` может быть null (для манги/ранобэ). */
export interface ShikiRelatedNode {
  relation: string;
  relation_russian?: string;
  anime?: ShikiAnime | null;
}

/** Аккаунт (ответ /auth/me). */
export interface Me {
  nick?: string | null;
  email?: string | null;
  avatar?: number;
  created?: number;
}

/** Сообщение чата / комментарий. */
export interface ChatMessage {
  id: number;
  nick: string;
  avatar: number;
  text: string;
  created: number;
  spoiler?: boolean;
}

/** Озвучка Kodik/AniLibria для плеера. */
export interface Dub {
  title: string;
  type: string;
  link: string;
  episodes?: number;
}
