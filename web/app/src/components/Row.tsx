"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import type { ShikiAnime } from "@/lib/types";
import { PosterCard } from "./PosterCard";

/** Горизонтальная лента тайтлов с заголовком, стрелками навигации и поддержкой колесика мыши. */
export function Row({ title, items, href }: { title: string; items: ShikiAnime[]; href?: string }) {
  const seen = new Set<number>();
  const filteredItems = items.filter((a) => (seen.has(a.id) ? false : seen.add(a.id)));

  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const updateScrollButtons = () => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 6);
    setCanScrollRight(el.scrollLeft < el.scrollWidth - el.clientWidth - 6);
  };

  const scrollByAmount = (direction: "left" | "right") => {
    const el = scrollRef.current;
    if (!el) return;
    const delta = direction === "left" ? -480 : 480;
    el.scrollBy({ left: delta, behavior: "smooth" });
    setTimeout(updateScrollButtons, 250);
  };

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const handleWheel = (e: WheelEvent) => {
      if (e.deltaY !== 0) {
        const maxScroll = el.scrollWidth - el.clientWidth;
        if (maxScroll > 0) {
          if ((e.deltaY > 0 && el.scrollLeft < maxScroll - 1) || (e.deltaY < 0 && el.scrollLeft > 1)) {
            e.preventDefault();
            el.scrollLeft += e.deltaY;
            updateScrollButtons();
          }
        }
      }
    };
    el.addEventListener("wheel", handleWheel, { passive: false });
    return () => el.removeEventListener("wheel", handleWheel);
  }, [filteredItems]);

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
  }, [filteredItems]);

  if (!filteredItems.length) return null;

  return (
    <section className="mb-14">
      <div className="mb-5 flex items-center justify-between">
        <h2 className="section-title">{title}</h2>
        <div className="flex items-center gap-3">
          {href && (
            <Link href={href} className="text-sm font-semibold text-text-muted transition hover:text-primary">
              Смотреть все →
            </Link>
          )}
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
      </div>
      <div
        ref={scrollRef}
        className="no-scrollbar grid auto-cols-[minmax(150px,1fr)] grid-flow-col gap-4 overflow-x-auto pb-4 scroll-smooth md:auto-cols-[minmax(170px,1fr)] lg:auto-cols-[minmax(185px,1fr)]"
      >
        {filteredItems.map((a) => (
          <div key={a.id}>
            <PosterCard anime={a} />
          </div>
        ))}
      </div>
    </section>
  );
}
