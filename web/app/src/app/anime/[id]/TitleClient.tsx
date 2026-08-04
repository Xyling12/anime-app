"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { kodikDubs } from "@/lib/video";
import { getProgress } from "@/lib/progress";
import { getFavorite, setFavorite, type WatchStatus } from "@/lib/favorites";
import { api, auth } from "@/lib/api";
import { Comments } from "@/components/Comments";
import type { ShikiImage } from "@/lib/types";
import { Icon } from "@/components/Icon";

const STATUSES: { label: string; s: WatchStatus }[] = [
  { label: "Смотрю", s: "watching" },
  { label: "В планах", s: "planned" },
  { label: "Просмотрено", s: "completed" },
];

/** Клиентская часть тайтла: озвучки, серии, плеер, статус «Моё», оценка 1-10. */
export function TitleClient({
  id,
  title,
  totalEpisodes,
  image,
}: {
  id: number;
  title: string;
  totalEpisodes: number;
  image?: ShikiImage | null;
}) {
  const router = useRouter();
  const [dubs, setDubs] = useState<{ title: string; link: string; type: string }[]>([]);
  const [dubIdx, setDubIdx] = useState(0);
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState<WatchStatus | "none">("none");
  const [myRating, setMyRating] = useState<number | null>(null);
  const [avgRating, setAvgRating] = useState<{ avg?: number; count: number } | null>(null);

  useEffect(() => {
    kodikDubs(id)
      .then((d) => setDubs(d))
      .catch(() => setDubs([]))
      .finally(() => setLoading(false));
    // Local storage is an external client-side store.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setStatus(getFavorite(id)?.status ?? "none");
    // Рейтинг AniPulse (свой + средний)
    api
      .get<{ avg?: number; count: number; my?: number }>(`rating?animeId=${id}`, !!auth.token)
      .then((r) => {
        setAvgRating({ avg: r.avg, count: r.count });
        setMyRating(r.my ?? null);
      })
      .catch(() => {});
  }, [id]);

  function toggleStatus(s: WatchStatus) {
    const next = status === s ? "none" : s;
    setStatus(next);
    setFavorite({ animeId: id, title, image, status: next });
  }

  function rate(score: number) {
    if (!auth.token) return;
    setMyRating(score);
    api
      .post<{ avg?: number; count: number; my?: number }>("rating", { animeId: id, score }, true)
      .then((r) => setAvgRating({ avg: r.avg, count: r.count }))
      .catch(() => {});
  }

  const epCount = totalEpisodes || 1;
  const resume = getProgress(id);
  const watchHref = (ep: number) => `/watch/${id}/${ep}?dub=${dubIdx}`;

  if (loading) return <p className="mt-8 text-text-muted">Загрузка озвучек…</p>;
  if (!dubs.length)
    return <p className="mt-8 text-text-muted">Видео пока не найдено для этого тайтла.</p>;

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1.35fr)_minmax(320px,.65fr)]">
      <div className="space-y-6">
      <button
        onClick={() => router.push(watchHref(resume?.episode || 1))}
        className="pulse-gradient flex w-full items-center justify-center gap-3 rounded-2xl py-4 text-center text-lg font-bold text-white shadow-[0_12px_40px_rgba(255,77,141,.2)]"
      >
        <Icon name="play" className="h-5 w-5 fill-current"/> {resume ? `Продолжить · Серия ${resume.episode}` : "Смотреть · Серия 1"}
      </button>

      {/* Статусы «Моё» */}
      <div className="grid grid-cols-3 gap-2">
        {STATUSES.map((st) => (
          <button
            key={st.s}
            onClick={() => toggleStatus(st.s)}
            className={`rounded-xl border px-3 py-3 text-sm font-bold ${
              status === st.s ? "border-primary bg-primary/10 text-primary" : "border-border text-text-muted hover:bg-surface"
            }`}
          >
            {st.label}
          </button>
        ))}
      </div>

      {/* Оценка 1-10 (нужен вход) */}
      <div className="panel p-5 md:p-6">
        <div className="mb-2 flex items-center gap-2 font-bold">
          Оценка
          {avgRating?.avg != null && (
            <span className="text-sm font-normal text-primary">
              ♥ {avgRating.avg.toFixed(1)} ({avgRating.count})
            </span>
          )}
        </div>
        <div className="grid grid-cols-5 gap-2 sm:grid-cols-10">
          {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
            <button
              key={n}
              onClick={() => rate(n)}
              disabled={!auth.token}
              className={`aspect-square rounded-xl text-sm font-bold ${
                myRating === n ? "pulse-gradient text-white" : "bg-surface text-text"
              } disabled:opacity-40`}
            >
              {n}
            </button>
          ))}
        </div>
        {!auth.token && <p className="mt-1 text-xs text-text-muted">Войдите, чтобы оценивать.</p>}
      </div>

      <div className="panel p-5 md:p-6"><h3 className="mb-3 text-lg font-bold">Озвучка</h3>
      <div className="no-scrollbar flex flex-wrap gap-2">
        {dubs.map((d, i) => (
          <button
            key={i}
            onClick={() => setDubIdx(i)}
            className={`rounded-xl border px-4 py-2.5 text-sm font-semibold ${
              i === dubIdx ? "border-primary bg-primary/15 text-primary" : "border-border bg-bg/30 text-text-muted"
            }`}
          >
            {d.title}
          </button>
        ))}
      </div></div>

      <div className="panel p-5 md:p-6"><h3 className="mb-3 text-lg font-bold">Серии</h3>
      <div className="grid grid-cols-5 gap-2 sm:grid-cols-8 lg:grid-cols-10">
        {Array.from({ length: epCount }, (_, i) => i + 1).map((ep) => (
          <button
            key={ep}
            onClick={() => router.push(watchHref(ep))}
            className={`aspect-square rounded-xl text-sm font-bold ${
              resume?.episode === ep ? "pulse-gradient text-white" : "bg-surface text-text"
            }`}
          >
            {ep}
          </button>
        ))}
      </div></div></div>

      <aside><Comments animeId={String(id)} /></aside>
    </div>
  );
}
