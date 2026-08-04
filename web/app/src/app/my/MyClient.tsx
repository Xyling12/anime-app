"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { listFavorites, type Favorite, type WatchStatus } from "@/lib/favorites";
import { posterPreview } from "@/lib/api";
import { PosterImage } from "@/components/PosterImage";

const TABS: { label: string; s?: WatchStatus }[] = [
  { label: "Все" },
  { label: "Смотрю", s: "watching" },
  { label: "В планах", s: "planned" },
  { label: "Просмотрено", s: "completed" },
];

export function MyClient() {
  const [tab, setTab] = useState(0);
  const [items, setItems] = useState<Favorite[]>([]);

  useEffect(() => {
    // Favorites are stored outside React in localStorage.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setItems(listFavorites(TABS[tab].s));
    const refresh = () => setItems(listFavorites(TABS[tab].s));
    window.addEventListener("anipulse-sync", refresh);
    return () => window.removeEventListener("anipulse-sync", refresh);
  }, [tab]);

  return (
    <div className="page-shell">
      <div className="mb-8"><div className="eyebrow mb-2">Твоя коллекция</div><h1 className="text-4xl font-black tracking-[-.045em] md:text-5xl">Моё</h1><p className="mt-2 text-text-muted">Списки, прогресс и всё, что хочется посмотреть.</p></div>
      <div className="no-scrollbar mb-8 flex gap-2 overflow-x-auto rounded-2xl border border-border bg-surface p-1.5 md:w-fit">
        {TABS.map((t, i) => (
          <button
            key={t.label}
            onClick={() => setTab(i)}
            className={`shrink-0 rounded-xl px-5 py-2.5 text-sm font-bold ${
              i === tab ? "bg-primary text-white" : "text-text-muted hover:text-white"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {!items.length ? (
        <div className="panel grid min-h-72 place-items-center p-8 text-center"><div><div className="mx-auto mb-4 grid h-16 w-16 place-items-center rounded-2xl bg-primary/10 text-3xl">♡</div><h2 className="text-xl font-bold">Здесь пока пусто</h2><p className="mt-2 text-text-muted">Добавляй тайтлы в коллекцию на странице аниме.</p><Link href="/catalog" className="mt-5 inline-flex rounded-xl bg-primary px-5 py-3 text-sm font-bold text-white">Перейти в каталог</Link></div></div>
      ) : (
        <div className="grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
          {items.map((f) => (
            <Link key={f.animeId} href={`/anime/${f.animeId}`} className="block">
              <div className="aspect-[2/3] overflow-hidden rounded-[18px] border border-border bg-surface-2">
                <PosterImage src={posterPreview(f.animeId, f.image)} alt={f.title} className="h-full w-full object-cover" />
              </div>
              <div className="mt-1.5 line-clamp-2 text-sm">{f.title}</div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
