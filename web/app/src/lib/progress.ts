/**
 * Прогресс просмотра — localStorage (MVP). Позже — серверный синк (см. WEB_PROJECT §5.2).
 * Ключ: anipulse_progress → { [animeId]: { episode, positionSec, dubIdx, updatedAt } }
 */
export interface Progress {
  episode: number;
  positionSec: number;
  dubIdx: number;
  updatedAt: number;
  durationSec?: number;
  watched?: boolean;
  title?: string;
  totalEpisodes?: number;
}

const KEY = "anipulse_progress";

function all(): Record<string, Progress> {
  if (typeof window === "undefined") return {};
  try {
    return JSON.parse(localStorage.getItem(KEY) || "{}");
  } catch {
    return {};
  }
}

export function getProgress(animeId: number): Progress | null {
  return all()[animeId] || null;
}

export function saveProgress(animeId: number, p: Progress) {
  if (typeof window === "undefined") return;
  const data = all();
  const saved = { ...p, updatedAt: Date.now() };
  data[animeId] = saved;
  localStorage.setItem(KEY, JSON.stringify(data));
  import("./sync").then(({ pushProgress }) => pushProgress({
    animeId, episode: saved.episode, positionMs: Math.round(saved.positionSec * 1000),
    durationMs: Math.round((saved.durationSec || 0) * 1000), watched: saved.watched === true,
    dubId: String(saved.dubIdx), title: saved.title || "", posterId: animeId,
    totalEpisodes: saved.totalEpisodes || 0, updatedAt: saved.updatedAt,
  }));
}

export function removeProgress(animeId: number) {
  if (typeof window === "undefined") return;
  const data = all();
  delete data[animeId];
  localStorage.setItem(KEY, JSON.stringify(data));
}

/** Лента «Продолжить просмотр» — свежие сверху. */
export function continueWatching(): { animeId: number; p: Progress }[] {
  return Object.entries(all())
    .map(([id, p]) => ({
      animeId: Number(id),
      p: p.watched && (!p.totalEpisodes || p.episode < p.totalEpisodes)
        ? { ...p, episode: p.episode + 1, positionSec: 0, watched: false }
        : p,
    }))
    .sort((a, b) => b.p.updatedAt - a.p.updatedAt);
}
