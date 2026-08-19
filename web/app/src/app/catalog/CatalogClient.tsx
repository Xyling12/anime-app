"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { catalog } from "@/lib/catalog";
import type { ShikiAnime } from "@/lib/types";
import { PosterCard } from "@/components/PosterCard";
import { Icon } from "@/components/Icon";
import { reachGoal } from "@/lib/metrika";

type Filter = { label: string; key: string; f: { order: string; status?: string } };
const FILTERS: Filter[] = [
  { label: "Все", key: "all", f: { order: "popularity" } },
  { label: "Онгоинги", key: "ongoing", f: { order: "popularity", status: "ongoing" } },
  { label: "По рейтингу", key: "ranked", f: { order: "ranked" } },
];

export function CatalogClient() {
  const router = useRouter();
  const params = useSearchParams();
  const [items, setItems] = useState<ShikiAnime[]>([]);
  const [page, setPage] = useState(1);
  const [filterIdx, setFilterIdx] = useState(() => {
    const f = params.get("filter");
    const i = FILTERS.findIndex((x) => x.key === f);
    return i >= 0 ? i : 0;
  });
  const [search, setSearch] = useState(params.get("q") || "");
  const [loading, setLoading] = useState(true);
  const [end, setEnd] = useState(false);
  const genRef = useRef(0);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const fetchItems = useCallback(
    async (pageNum: number, isReset: boolean, query: string, currentFilterIdx: number) => {
      const currentGen = ++genRef.current;
      setLoading(true);
      try {
        const filter = FILTERS[currentFilterIdx].f;
        const list = await catalog({
          page: pageNum,
          ...filter,
          search: query.trim() || undefined,
        });

        if (currentGen !== genRef.current) return;

        setItems((prev) => {
          if (isReset) return list;
          const merged = [...prev, ...list];
          const seen = new Set<number>();
          return merged.filter((a) => (seen.has(a.id) ? false : seen.add(a.id)));
        });

        setPage(pageNum + 1);
        setEnd(list.length === 0 || (!!query.trim() && list.length < 24));
      } catch (err) {
        console.error("Catalog fetch error:", err);
      } finally {
        if (currentGen === genRef.current) {
          setLoading(false);
        }
      }
    },
    [],
  );

  // Debounced search / filter change
  useEffect(() => {
    const timer = setTimeout(() => {
      setEnd(false);
      setPage(1);
      if (search.trim()) {
        reachGoal("catalog_search", { query: search.trim() });
      }
      fetchItems(1, true, search, filterIdx);
    }, search ? 350 : 0);

    return () => clearTimeout(timer);
  }, [search, filterIdx, fetchItems]);

  // Infinite scroll
  useEffect(() => {
    const el = sentinelRef.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && !loading && !end) {
          fetchItems(page, false, search, filterIdx);
        }
      },
      { rootMargin: "600px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [loading, end, page, search, filterIdx, fetchItems]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setEnd(false);
    setPage(1);
    if (search.trim()) {
      reachGoal("catalog_search", { query: search.trim() });
    }
    fetchItems(1, true, search, filterIdx);
  };

  const handleClearSearch = () => {
    setSearch("");
    inputRef.current?.focus();
  };

  return (
    <div className="page-shell relative z-10 w-full min-w-0 max-w-full">
      <div className="mb-6 flex flex-col justify-between gap-4 lg:mb-8 lg:flex-row lg:items-end">
        <div>
          <div className="eyebrow mb-1.5 sm:mb-2">Вся коллекция</div>
          <h1 className="text-3xl font-black tracking-[-.045em] sm:text-4xl md:text-5xl">Каталог аниме</h1>
          <p className="mt-1.5 text-xs text-text-muted sm:text-sm md:text-base">
            Находи новое по популярности, рейтингу и статусу выхода.
          </p>
        </div>
        <div className="text-xs text-text-dim sm:text-sm">
          {items.length ? `Найдено: ${items.length}` : loading ? "Ищем тайтлы…" : "По вашему запросу"}
        </div>
      </div>

      {/* Поисковая форма */}
      <form
        onSubmit={handleSearchSubmit}
        className="panel mb-5 flex items-center gap-3 px-4 py-3 sm:px-5 sm:py-3.5 transition focus-within:border-primary/60"
      >
        <Icon name="search" className="h-5 w-5 shrink-0 text-text-dim" />
        <input
          ref={inputRef}
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Поиск по названию (например, Наруто, Блич, Фрирен)…"
          className="w-full bg-transparent text-sm sm:text-base outline-none placeholder:text-text-dim"
        />
        {search && (
          <button
            type="button"
            onClick={handleClearSearch}
            aria-label="Очистить поиск"
            className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-surface-2 text-text-muted hover:bg-surface-hover hover:text-text"
          >
            ✕
          </button>
        )}
      </form>

      {/* Переключатели фильтров */}
      <div className="no-scrollbar mb-6 sm:mb-8 flex gap-2 overflow-x-auto pb-1">
        {FILTERS.map((f, i) => (
          <button
            key={f.key}
            type="button"
            onClick={() => setFilterIdx(i)}
            className={`shrink-0 rounded-xl px-4 py-2 sm:px-5 sm:py-2.5 text-xs sm:text-sm font-semibold transition ${
              i === filterIdx
                ? "bg-primary text-white shadow-lg"
                : "border border-border bg-surface text-text-muted hover:bg-surface-hover"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* Сетка тайтлов */}
      <div className="grid grid-cols-2 gap-x-3 gap-y-5 sm:grid-cols-3 md:grid-cols-4 md:gap-x-4 md:gap-y-7 lg:grid-cols-5 xl:grid-cols-6">
        {items.map((a) => (
          <PosterCard key={a.id} anime={a} />
        ))}
      </div>

      {/* Индикатор загрузки */}
      {loading && (
        <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 md:gap-4 lg:grid-cols-5 xl:grid-cols-6">
          {Array.from({ length: items.length ? 6 : 12 }).map((_, i) => (
            <div key={i} className="skeleton aspect-[2/3] rounded-2xl" />
          ))}
        </div>
      )}

      {/* Пустое состояние */}
      {!loading && !items.length && (
        <div className="py-16 text-center">
          <p className="text-base sm:text-lg font-bold text-text">Ничего не найдено</p>
          <p className="mt-1 text-xs sm:text-sm text-text-muted">
            Попробуйте изменить поисковый запрос или проверить правильность написания.
          </p>
          {search && (
            <button
              type="button"
              onClick={handleClearSearch}
              className="mt-4 rounded-xl border border-border bg-surface px-4 py-2 text-xs sm:text-sm font-semibold text-primary hover:bg-surface-hover"
            >
              Сбросить поиск
            </button>
          )}
        </div>
      )}

      <div ref={sentinelRef} className="h-4" />
    </div>
  );
}
