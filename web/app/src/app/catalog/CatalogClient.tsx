"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { catalog } from "@/lib/catalog";
import type { ShikiAnime } from "@/lib/types";
import { PosterCard } from "@/components/PosterCard";
import { Icon } from "@/components/Icon";

type Filter = { order: string; status?: string };
const FILTERS: { label: string; key: string; f: Filter }[] = [
  { label: "Все", key: "all", f: { order: "popularity" } },
  { label: "Онгоинги", key: "ongoing", f: { order: "popularity", status: "ongoing" } },
  { label: "По рейтингу", key: "ranked", f: { order: "ranked" } },
];

export function CatalogClient() {
  const params = useSearchParams();
  const [items, setItems] = useState<ShikiAnime[]>([]);
  const [page, setPage] = useState(1);
  const [filterIdx, setFilterIdx] = useState(() => {
    const f = params.get("filter");
    const i = FILTERS.findIndex((x) => x.key === f);
    return i >= 0 ? i : 0;
  });
  const [search, setSearch] = useState(params.get("q") || "");
  const [loading, setLoading] = useState(false);
  const [end, setEnd] = useState(false);
  const gen = useRef(0);
  const sentinel = useRef<HTMLDivElement>(null);

  const loadPage = useCallback(
    async (reset: boolean) => {
      if (loading) return;
      const myGen = gen.current;
      setLoading(true);
      const nextPage = reset ? 1 : page;
      try {
        const f = FILTERS[filterIdx].f;
        const list = await catalog({ page: nextPage, ...f, search: search.trim() || undefined });
        if (myGen !== gen.current) return;
        setItems((prev) => {
          const merged = reset ? list : [...prev, ...list];
          const seen = new Set<number>();
          return merged.filter((a) => (seen.has(a.id) ? false : seen.add(a.id)));
        });
        setPage(nextPage + 1);
        setEnd(list.length === 0 || !!search.trim());
      } finally {
        if (myGen === gen.current) setLoading(false);
      }
    },
    [loading, page, filterIdx, search],
  );

  useEffect(() => {
    gen.current++;
    // Reset the paginated view when its query changes.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setItems([]);
    setPage(1);
    setEnd(false);
    const t = setTimeout(() => loadPage(true), search ? 400 : 0);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterIdx, search]);

  useEffect(() => {
    const el = sentinel.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (e) => {
        if (e[0].isIntersecting && !loading && !end) loadPage(false);
      },
      { rootMargin: "800px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [loadPage, loading, end]);

  return (
    <div className="page-shell relative z-10">
      <div className="mb-8 flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
        <div><div className="eyebrow mb-2">Вся коллекция</div><h1 className="text-4xl font-black tracking-[-.045em] md:text-5xl">Каталог аниме</h1><p className="mt-2 text-text-muted">Находи новое по популярности, рейтингу и статусу выхода.</p></div>
        <div className="text-sm text-text-dim">{items.length ? `Загружено: ${items.length}` : "Подбираем тайтлы"}</div>
      </div>

      <div className="panel mb-5 flex items-center gap-3 px-5 py-4 transition focus-within:border-primary/60">
        <Icon name="search" className="h-5 w-5 text-text-dim"/>
        <input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Поиск аниме…"
          className="w-full bg-transparent text-base outline-none placeholder:text-text-dim"
        />
      </div>

      <div className="no-scrollbar mb-9 flex gap-2 overflow-x-auto">
        {FILTERS.map((f, i) => (
          <button
            key={f.key}
            onClick={() => setFilterIdx(i)}
            className={`shrink-0 rounded-xl px-5 py-2.5 text-sm font-semibold transition ${
              i === filterIdx ? "bg-primary text-white shadow-lg" : "border border-border bg-surface text-text-muted hover:bg-surface-hover"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
        {items.map((a) => (
          <PosterCard key={a.id} anime={a} />
        ))}
      </div>

      {loading && (
        <div className="mt-8 grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="skeleton aspect-[2/3] rounded-2xl" />
          ))}
        </div>
      )}
      {!loading && !items.length && <p className="py-16 text-center text-text-muted">Ничего не найдено.</p>}
      <div ref={sentinel} className="h-1" />
    </div>
  );
}
