"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { continueWatching, type Progress } from "@/lib/progress";
import { details } from "@/lib/catalog";
import { posterPreview } from "@/lib/api";
import { PosterImage } from "./PosterImage";

/** Лента «Продолжить просмотр» из localStorage (клиентская — не для SEO). */
export function ContinueRow() {
  const [rows, setRows] = useState<{ id: number; ep: number; title: string; img: string }[]>([]);

  useEffect(() => {
    const load = () => {
      const cw = continueWatching().filter(({ p }) => !p.watched).slice(0, 12);
      if (!cw.length) { setRows([]); return; }
      Promise.all(
      cw.map(async ({ animeId, p }: { animeId: number; p: Progress }) => {
        try {
          const d = await details(animeId);
          return {
            id: animeId,
            ep: p.episode,
            title: d.russian?.trim() || d.name,
            img: posterPreview(animeId, d.image),
          };
        } catch {
          return null;
        }
      }),
      ).then((r) => setRows(r.filter(Boolean) as typeof rows));
    };
    load();
    window.addEventListener("anipulse-sync", load);
    return () => window.removeEventListener("anipulse-sync", load);
  }, []);

  if (!rows.length) return null;

  return (
    <section className="mb-8">
      <h2 className="mb-3 px-4 text-lg font-bold md:px-0">Продолжить просмотр</h2>
      <div className="no-scrollbar flex gap-3 overflow-x-auto px-4 md:px-0">
        {rows.map((r) => (
          <Link key={r.id} href={`/watch/${r.id}/${r.ep}`} className="shrink-0" style={{ width: 130 }}>
            <div className="relative aspect-[2/3] overflow-hidden rounded-xl bg-surface-2">
              <PosterImage src={r.img} alt={r.title} className="h-full w-full object-cover" />
              <span className="absolute bottom-1 left-1 rounded bg-black/70 px-1.5 py-0.5 text-xs text-white">
                Серия {r.ep}
              </span>
            </div>
            <div className="mt-1.5 line-clamp-2 text-sm">{r.title}</div>
          </Link>
        ))}
      </div>
    </section>
  );
}
