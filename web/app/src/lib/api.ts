/**
 * Слой доступа к бэкенду AniPulse — тот же шлюз, что и у Android-приложения.
 * Все данные (каталог, видео, аккаунты, соцчасть) уже отдаются сервером;
 * веб-клиент — новый UI поверх тех же эндпоинтов.
 */
export const GATEWAY = "https://anipulsetv.ru/alapi/";
export const SHIKIMORI = GATEWAY + "shikimori/";

/** Токен аккаунта: в браузере храним в localStorage (позже — httpOnly-cookie). */
const TOKEN_KEY = "anipulse_token";
export const auth = {
  get token(): string | null {
    if (typeof window === "undefined") return null;
    return localStorage.getItem(TOKEN_KEY);
  },
  set token(v: string | null) {
    if (typeof window === "undefined") return;
    if (v) localStorage.setItem(TOKEN_KEY, v);
    else localStorage.removeItem(TOKEN_KEY);
  },
  get bearer(): Record<string, string> {
    const t = this.token;
    return t ? { Authorization: `Bearer ${t}` } : {};
  },
};

async function req<T>(path: string, init?: RequestInit & { auth?: boolean }): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init?.headers as Record<string, string>),
    ...(init?.auth ? auth.bearer : {}),
  };
  const res = await fetch(GATEWAY + path, { ...init, headers });
  if (!res.ok) {
    let msg = `Ошибка ${res.status}`;
    try {
      const body = await res.json();
      if (body?.error) msg = body.error;
    } catch {}
    throw new Error(msg);
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : null) as T;
}

export const api = {
  get: <T>(path: string, withAuth = false) => req<T>(path, { auth: withAuth }),
  post: <T>(path: string, body: unknown, withAuth = false) =>
    req<T>(path, { method: "POST", body: JSON.stringify(body), auth: withAuth }),
};

/** Постер тайтла: preview для сеток (лёгкий), original — для крупных экранов. */
export function posterPreview(
  id: number,
  image?: { preview?: string; original?: string; x96?: string; x48?: string } | null,
): string {
  const path = image?.preview || image?.original || image?.x96;
  if (path) {
    const clean = path.startsWith("/") ? path.slice(1) : path;
    return `${GATEWAY}shikimori/${clean}`;
  }
  return `${GATEWAY}poster/${id}`;
}

export function posterOriginal(
  id: number,
  image?: { preview?: string; original?: string } | null,
): string {
  const path = image?.original || image?.preview;
  if (path) {
    const clean = path.startsWith("/") ? path.slice(1) : path;
    return `${GATEWAY}shikimori/${clean}`;
  }
  return `${GATEWAY}poster/${id}`;
}

/** Прямой URL постера с shikimori.io — используем для related, где gateway-кеш ещё холодный. */
export function shikimoriImage(path?: string | null): string | null {
  if (!path) return null;
  if (path.startsWith("http://") || path.startsWith("https://")) return path;
  return `https://shikimori.io${path.startsWith("/") ? "" : "/"}${path}`;
}
