"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { calendar, anilibriaUpdates } from "@/lib/catalog";
import type { ShikiCalendarEntry, AnilibriaUpdate } from "@/lib/types";
import { posterPreview } from "@/lib/api";
import { Icon } from "@/components/Icon";
import { PosterImage } from "@/components/PosterImage";

const MSK_OFFSET = 3 * 60; // МСК = UTC+3
function mskTime(iso?: string | null): string {
  if (!iso) return "--:--";
  const d = new Date(iso);
  const m = new Date(d.getTime() + (d.getTimezoneOffset() + MSK_OFFSET) * 60000);
  return `${m.getHours()}:${String(m.getMinutes()).padStart(2, "0")}`;
}

export function ScheduleClient() {
  const router = useRouter();
  const [tab, setTab] = useState(0);
  const [days, setDays] = useState<Record<string, ShikiCalendarEntry[]>>({});
  const [updates, setUpdates] = useState<AnilibriaUpdate[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      calendar().catch(() => []),
      anilibriaUpdates().catch(() => []),
    ]).then(([cal, upd]) => {
      const grouped: Record<string, ShikiCalendarEntry[]> = {};
      for (const e of cal) {
        if (!e.next_episode_at) continue;
        const key = new Date(e.next_episode_at).toISOString().slice(0, 10);
        (grouped[key] ||= []).push(e);
      }
      setDays(grouped);
      setUpdates(upd);
      setLoading(false);
    });
  }, []);

  const dayKeys = Object.keys(days).sort();

  return (
    <div className="page-shell">
      <div className="mb-9 flex flex-col justify-between gap-5 md:flex-row md:items-end"><div><div className="eyebrow mb-2">Новые эпизоды</div><h1 className="text-4xl font-black tracking-[-.045em] md:text-5xl">Расписание</h1><p className="mt-2 text-text-muted">Вся неделя перед глазами. Время выхода указано по МСК.</p></div>
      <div className="flex rounded-2xl border border-border bg-surface p-1.5">
        {["Расписание", "Обновления"].map((t, i) => (
          <button
            key={t}
            onClick={() => setTab(i)}
            className={`rounded-xl px-6 py-2.5 text-sm font-bold transition ${
              i === tab ? "bg-primary text-white" : "text-text-muted hover:text-white"
            }`}
          >
            {t}
          </button>
        ))}
      </div></div>

      {loading && <p className="py-8 text-center text-text-muted">Загрузка…</p>}

      {!loading && tab === 0 && (
        <div className="grid gap-6 lg:grid-cols-2">
          {dayKeys.map((k) => (
            <section key={k} className="panel overflow-hidden p-3 md:p-4">
              <h3 className="mb-3 px-2 pt-1 text-lg font-bold capitalize">
                {new Date(k).toLocaleDateString("ru", { weekday: "long", day: "numeric", month: "long" })}
              </h3>
              <div className="space-y-1">
                {days[k].map((e) => (
                  <button
                    key={e.anime.id}
                    onClick={() => router.push(`/anime/${e.anime.id}`)}
                    className="group flex w-full items-center gap-4 rounded-2xl p-2 text-left transition hover:bg-surface-hover"
                  >
                    <span className="w-14 shrink-0 rounded-xl bg-primary/10 py-2 text-center font-black text-primary">
                      {mskTime(e.next_episode_at)}
                    </span>
                    <PosterImage
                      src={posterPreview(e.anime.id, e.anime.image)}
                      alt=""
                      className="h-20 w-14 shrink-0 rounded-xl object-cover"
                    />
                    <span className="flex-1">
                      <span className="line-clamp-2 font-bold">
                        {e.anime.russian?.trim() || e.anime.name}
                      </span>
                      <span className="text-xs text-primary">Серия {e.next_episode}</span>
                    </span><Icon name="chevron" className="h-5 w-5 text-text-dim transition group-hover:translate-x-1 group-hover:text-primary"/>
                  </button>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}

      {!loading && tab === 1 && (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {updates.map((u, i) => (
            <div key={i} className="panel flex items-center gap-4 p-3">
              {u.poster && (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={`https://anipulsetv.ru${u.poster}`}
                  alt=""
                  className="h-24 w-16 shrink-0 rounded-xl object-cover"
                />
              )}
              <div className="flex-1">
                <div className="line-clamp-2 text-sm font-medium">{u.title}</div>
                {u.episode && <div className="text-xs text-primary">Серия {u.episode}</div>}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
