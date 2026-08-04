/** Запросы видео (Kodik/AniLibria) и таймкодов AniSkip через шлюз. */
import { api } from "./api";

export interface Dub {
  id: string;
  source: string;
  title: string;
  type: string;
  episodesCount: number;
  ref: string;
}

/** Все озвучки Kodik по shikimori_id. */
export function kodikDubs(shikimoriId: number): Promise<
  { title: string; type: string; link: string }[]
> {
  return api.get(`kodik-dubs?shikimoriId=${shikimoriId}`);
}

/** Извлечение m3u8 по качествам для конкретной серии. */
export function kodikStream(ref: string, episode: number): Promise<Record<string, string>> {
  return api.get(`kodik?link=${encodeURIComponent(ref)}&episode=${episode}`);
}

/** Таймкоды опенинга/эндинга (AniSkip) по MAL id + серии. */
export async function skipTimes(malId: number, episode: number): Promise<{
  op?: { start: number; stop: number };
  ed?: { start: number; stop: number };
}> {
  try {
    const r = await api.get<{ results?: { skipType: string; interval: { startTime: number; endTime: number } }[] }>(
      `aniskip/v2/skip-times/${malId}/${episode}?types=op&types=ed&episodeLength=0`,
    );
    const find = (t: string) => {
      const e = r.results?.find((x) => x.skipType === t);
      return e ? { start: Math.floor(e.interval.startTime), stop: Math.floor(e.interval.endTime) } : undefined;
    };
    return { op: find("op"), ed: find("ed") };
  } catch {
    return {};
  }
}
