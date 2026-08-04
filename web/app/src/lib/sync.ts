import { api, auth } from "./api";

export interface ServerProgress {
  animeId: number; episode: number; positionMs: number; durationMs: number; watched: boolean;
  dubId?: string | null; title?: string; posterId?: number; totalEpisodes?: number; updatedAt: number;
}
export interface ServerFavorite {
  animeId: number; title?: string; score?: string | null;
  status: "none" | "watching" | "planned" | "completed"; updatedAt: number; deleted?: boolean;
}
export interface SyncPayload { progress: ServerProgress[]; favorites: ServerFavorite[]; }

const PROGRESS_KEY = "anipulse_progress";
const FAVORITES_KEY = "anipulse_favorites";
function readRecord(key: string): Record<string, Record<string, unknown>> {
  try { return JSON.parse(localStorage.getItem(key) || "{}"); } catch { return {}; }
}

export async function syncAll(): Promise<void> {
  if (!auth.token || typeof window === "undefined") return;
  const progress = Object.entries(readRecord(PROGRESS_KEY)).map(([animeId, raw]) => ({
    animeId: Number(animeId), episode: Number(raw.episode || 1), positionMs: Math.round(Number(raw.positionSec || 0) * 1000),
    durationMs: Math.round(Number(raw.durationSec || 0) * 1000), watched: raw.watched === true,
    dubId: String(raw.dubIdx ?? ""), title: String(raw.title || ""), posterId: Number(animeId),
    totalEpisodes: Number(raw.totalEpisodes || 0), updatedAt: Number(raw.updatedAt || Date.now()),
  }));
  const favorites = Object.values(readRecord(FAVORITES_KEY)).map((raw) => ({
    animeId: Number(raw.animeId), title: String(raw.title || ""), score: raw.score == null ? null : String(raw.score),
    status: String(raw.status || "none") as ServerFavorite["status"], updatedAt: Number(raw.updatedAt || Date.now()),
  }));
  const merged = await api.post<SyncPayload>("sync", { progress, favorites }, true);
  const newest: Record<string, Record<string, unknown>> = {};
  for (const item of merged.progress) {
    const old = newest[item.animeId];
    if (!old || item.updatedAt >= Number(old.updatedAt || 0)) newest[item.animeId] = {
      episode: item.episode, positionSec: item.positionMs / 1000, durationSec: item.durationMs / 1000,
      watched: item.watched, dubIdx: Number(item.dubId || 0), title: item.title || "",
      totalEpisodes: item.totalEpisodes || 0, updatedAt: item.updatedAt,
    };
  }
  const favoriteRecord: Record<string, ServerFavorite> = {};
  for (const item of merged.favorites) if (!item.deleted) favoriteRecord[item.animeId] = item;
  localStorage.setItem(PROGRESS_KEY, JSON.stringify(newest));
  localStorage.setItem(FAVORITES_KEY, JSON.stringify(favoriteRecord));
  window.dispatchEvent(new Event("anipulse-sync"));
}
export function pushProgress(item: ServerProgress) {
  if (auth.token) void api.post<SyncPayload>("sync", { progress: [item], favorites: [] }, true).catch(() => {});
}
export function pushFavorite(item: ServerFavorite) {
  if (auth.token) void api.post<SyncPayload>("sync", { progress: [], favorites: [item] }, true).catch(() => {});
}
