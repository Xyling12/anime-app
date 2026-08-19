/** Запросы каталога/тайтлов к Shikimori через шлюз. */
import { api } from "./api";
import type { ShikiAnime, ShikiAnimeDetails, ShikiCalendarEntry, AnilibriaUpdate, ShikiRelatedNode } from "./types";

const LIMIT = 24;

/** Страница каталога. order: popularity|ranked; status: ongoing|released|... */
export function catalog(opts: {
  page?: number;
  order?: string;
  status?: string;
  genre?: string;
  search?: string;
} = {}): Promise<ShikiAnime[]> {
  const p = new URLSearchParams({
    page: String(opts.page ?? 1),
    limit: String(LIMIT),
    censored: "false",
  });
  if (opts.search?.trim()) {
    p.set("search", opts.search.trim());
  } else {
    p.set("order", opts.order ?? "popularity");
    if (opts.status) p.set("status", opts.status);
    if (opts.genre) p.set("genre", opts.genre);
  }
  return api.get<ShikiAnime[]>(`shikimori/api/animes?${p.toString()}`);
}

export function details(id: number): Promise<ShikiAnimeDetails> {
  return api.get<ShikiAnimeDetails>(`shikimori/api/animes/${id}`);
}

export function calendar(): Promise<ShikiCalendarEntry[]> {
  return api.get<ShikiCalendarEntry[]>(`shikimori/api/calendar?censored=false`);
}

export function anilibriaUpdates(): Promise<AnilibriaUpdate[]> {
  return api.get<AnilibriaUpdate[]>(`anilibria-updates`);
}

/** Батч-рейтинги AniPulse для бейджей на постерах. */
export function ratings(ids: number[]): Promise<Record<string, { avg?: number; count: number }>> {
  if (!ids.length) return Promise.resolve({});
  return api.get(`ratings?ids=${ids.join(",")}`);
}

/** Связанные тайтлы из `/api/animes/{id}/related` (для блока «Порядок просмотра»). */
export function related(id: number): Promise<ShikiRelatedNode[]> {
  return api.get<ShikiRelatedNode[]>(`shikimori/api/animes/${id}/related`);
}
