"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { continueWatching, removeProgress, type Progress } from "@/lib/progress";
import { details } from "@/lib/catalog";
import { posterPreview } from "@/lib/api";
import { PosterImage } from "./PosterImage";
import { Icon } from "./Icon";

/** Лента «Продолжить просмотр» из localStorage (клиентская — не для SEO). */
export function ContinueRow() {
  const [rows, setRows] = useState<{ id: number; ep: number; title: string; img: string }[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const isDownRef = useRef(false);
  const startXRef = useRef(0);
  const scrollLeftRef = useRef(0);
  const hasMovedRef = useRef(false);

  const updateScrollButtons = () => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 6);
    setCanScrollRight(el.scrollLeft < el.scrollWidth - el.clientWidth - 6);
  };

  const scrollByAmount = (direction: "left" | "right") => {
    const el = scrollRef.current;
    if (!el) return;
    const delta = direction === "left" ? -360 : 360;
    el.scrollBy({ left: delta, behavior: "smooth" });
    setTimeout(updateScrollButtons, 250);
  };

  const handleMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    const el = scrollRef.current;
    if (!el) return;
    isDownRef.current = true;
    hasMovedRef.current = false;
    startXRef.current = e.clientX;
    scrollLeftRef.current = el.scrollLeft;
  };

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!isDownRef.current) return;
    const el = scrollRef.current;
    if (!el) return;
    const dx = e.clientX - startXRef.current;
    if (Math.abs(dx) > 4) {
      hasMovedRef.current = true;
    }
    el.scrollLeft = scrollLeftRef.current - dx;
    updateScrollButtons();
  };

  const handleMouseUp = () => {
    isDownRef.current = false;
  };

  const handleClickCapture = (e: React.MouseEvent) => {
    if (hasMovedRef.current) {
      e.preventDefault();
      e.stopPropagation();
      hasMovedRef.current = false;
    }
  };

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    updateScrollButtons();
    const handleScroll = () => updateScrollButtons();
    el.addEventListener("scroll", handleScroll, { passive: true });
    window.addEventListener("resize", handleScroll);
    return () => {
      el.removeEventListener("scroll", handleScroll);
      window.removeEventListener("resize", handleScroll);
    };
  }, [rows]);

  useEffect(() => {
    const cw = continueWatching().slice(0, 12);
    if (!cw.length) return;
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
  }, []);

  function onRemove(id: number, e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    removeProgress(id);
    setRows((prev) => prev.filter((r) => r.id !== id));
  }

  if (!rows.length) return null;

  return (
    <section className="mb-8">
      <div className="mb-3 flex items-center justify-between px-4 md:px-0">
        <h2 className="text-lg font-bold">Продолжить просмотр</h2>
        <div className="hidden items-center gap-1.5 md:flex">
          <button
            type="button"
            onClick={() => scrollByAmount("left")}
            disabled={!canScrollLeft}
            aria-label="Назад"
            className="flex h-8 w-8 items-center justify-center rounded-lg border border-border bg-surface text-text transition hover:border-primary/50 hover:bg-surface-2 disabled:pointer-events-none disabled:opacity-25"
          >
            ←
          </button>
          <button
            type="button"
            onClick={() => scrollByAmount("right")}
            disabled={!canScrollRight}
            aria-label="Вперёд"
            className="flex h-8 w-8 items-center justify-center rounded-lg border border-border bg-surface text-text transition hover:border-primary/50 hover:bg-surface-2 disabled:pointer-events-none disabled:opacity-25"
          >
            →
          </button>
        </div>
      </div>
      <div
        ref={scrollRef}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onClickCapture={handleClickCapture}
        className="no-scrollbar flex gap-3 overflow-x-auto px-4 pb-1 select-none cursor-grab active:cursor-grabbing md:px-0"
      >
        {rows.map((r) => (
          <Link
            key={r.id}
            href={`/watch/${r.id}/${r.ep}`}
            draggable={false}
            className="group relative shrink-0"
            style={{ width: 130 }}
          >
            <div className="relative aspect-[2/3] overflow-hidden rounded-xl bg-surface-2">
              <PosterImage
                src={r.img}
                alt={r.title}
                draggable={false}
                className="h-full w-full object-cover"
              />
              <span className="absolute bottom-1 left-1 rounded bg-black/70 px-1.5 py-0.5 text-xs text-white">
                Серия {r.ep}
              </span>
              <button
                type="button"
                onClick={(e) => onRemove(r.id, e)}
                aria-label={`Удалить «${r.title}» из «Продолжить просмотр»`}
                title="Удалить из истории"
                className="absolute right-1 top-1 grid h-7 w-7 place-items-center rounded-full bg-black/65 text-white opacity-0 transition group-hover:opacity-100 hover:bg-black/85"
              >
                <Icon name="x" className="h-4 w-4" />
              </button>
            </div>
            <div className="mt-1.5 line-clamp-2 text-sm">{r.title}</div>
          </Link>
        ))}
      </div>
    </section>
  );
}
