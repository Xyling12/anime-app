/**
 * «Моё» — избранное со статусами. localStorage (MVP), как прогресс.
 * Статусы: watching | planned | completed. На Android это Room-local, серверного
 * эндпоинта нет — при желании синка позже добавим эндпоинт (как для прогресса).
 */
export type WatchStatus = "watching" | "planned" | "completed";

export interface Favorite {
  animeId: number;
  title: string;
  image?: { preview?: string; original?: string } | null;
  status: WatchStatus | "none";
  updatedAt: number;
}

const KEY = "anipulse_favorites";

function all(): Record<string, Favorite> {
  if (typeof window === "undefined") return {};
  try {
    return JSON.parse(localStorage.getItem(KEY) || "{}");
  } catch {
    return {};
  }
}

export function getFavorite(animeId: number): Favorite | null {
  return all()[animeId] || null;
}

export function setFavorite(fav: Omit<Favorite, "updatedAt">) {
  if (typeof window === "undefined") return;
  const data = all();
  const updatedAt = Date.now();
  if (fav.status === "none") delete data[fav.animeId];
  else data[fav.animeId] = { ...fav, updatedAt };
  localStorage.setItem(KEY, JSON.stringify(data));
  import("./sync").then(({ pushFavorite }) => pushFavorite({
    animeId: fav.animeId, title: fav.title, status: fav.status,
    updatedAt, deleted: fav.status === "none",
  }));
}

export function listFavorites(status?: WatchStatus): Favorite[] {
  const arr = Object.values(all()).sort((a, b) => b.updatedAt - a.updatedAt);
  return status ? arr.filter((f) => f.status === status) : arr;
}
