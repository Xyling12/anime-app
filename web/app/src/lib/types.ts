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
}

/** Детальная карточка тайтла. */
export interface ShikiAnimeDetails extends ShikiAnime {
  description?: string | null;
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

/** Аккаунт (ответ /auth/me). */
export interface Me {
  nick?: string | null;
  email?: string | null;
  avatar: number; // -1 = кастомная аватарка
  linked: string[];
  admin: boolean;
  emailVerified: boolean;
}

/** Сообщение общего чата / комментарий. */
export interface ChatMessage {
  id: number;
  nick: string;
  avatar: number;
  text: string;
  at: number;
  replyTo?: { id: number; nick: string; text: string } | null;
  spoiler?: boolean;
}

/** Краткий рейтинг AniPulse (бейджи ♥ на постерах). */
export interface RatingBrief {
  avg?: number | null;
  count: number;
}

/** Манифест обновления приложения (для баннера «доступна новая версия Android-приложения»). */
export interface AppVersion {
  versionCode: number;
  versionName: string;
  url: string;
}
