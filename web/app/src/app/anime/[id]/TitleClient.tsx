"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { kodikDubs } from "@/lib/video";
import { getProgress } from "@/lib/progress";
import { getFavorite, setFavorite, type WatchStatus } from "@/lib/favorites";
import { api, auth, posterOriginal, shikimoriImage } from "@/lib/api";
import { related } from "@/lib/catalog";
import { Comments } from "@/components/Comments";
import type { ShikiImage, ShikiRelatedNode } from "@/lib/types";
import { Icon } from "@/components/Icon";
import { PosterImage } from "@/components/PosterImage";
import { reachGoal } from "@/lib/metrika";

const STATUSES: { label: string; s: WatchStatus }[] = [
  { label: "Смотрю", s: "watching" },
  { label: "В планах", s: "planned" },
  { label: "Просмотрено", s: "completed" },
];

/** Клиентская часть тайтла: озвучки, серии, плеер, статус «Моё», оценка 1-10, порядок просмотра. */
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
  const [ratingError, setRatingError] = useState<string | null>(null);
  const [relatedList, setRelatedList] = useState<ShikiRelatedNode[]>([]);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const relatedScrollRef = useRef<HTMLDivElement>(null);
  const currentRelatedRef = useRef<HTMLAnchorElement>(null);
  const isDraggingRef = useRef(false);
  const dragStartXRef = useRef(0);
  const scrollStartLeftRef = useRef(0);
  const hasMovedRef = useRef(false);

  const updateScrollButtons = () => {
    const el = relatedScrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 6);
    setCanScrollRight(el.scrollLeft < el.scrollWidth - el.clientWidth - 6);
  };

  const scrollRelated = (direction: "left" | "right") => {
    const el = relatedScrollRef.current;
    if (!el) return;
    const delta = direction === "left" ? -340 : 340;
    el.scrollBy({ left: delta, behavior: "smooth" });
    setTimeout(updateScrollButtons, 250);
  };

  useEffect(() => {
    kodikDubs(id)
      .then((d) => setDubs(d))
      .catch(() => setDubs([]))
      .finally(() => setLoading(false));
    setStatus(getFavorite(id)?.status ?? "none");
    // Рейтинг AniPulse (свой + средний)
    const myEpoch = ++ratingEpochRef.current;
    api
      .get<{ avg?: number; count: number; my?: number }>(`rating?animeId=${id}`, !!auth.token)
      .then((r) => {
        if (myEpoch !== ratingEpochRef.current) return;
        setAvgRating({ avg: r.avg, count: r.count });
        setMyRating(r.my ?? null);
      })
      .catch(() => {});
    // Порядок просмотра (Shikimori /related)
    related(id)
      .then((nodes) => {
        const items = nodes
          .filter((n) => n.anime)
          .map((n) => ({
            node: n,
            year: n.anime?.aired_on ? new Date(n.anime.aired_on).getFullYear() : 0,
          }))
          .sort((a, b) => a.year - b.year);
        setRelatedList(items.map((i) => i.node));
      })
      .catch(() => setRelatedList([]));
  }, [id]);

  // Horizontal mouse wheel scrolling for Порядок просмотра
  useEffect(() => {
    const el = relatedScrollRef.current;
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
  }, [relatedList]);

  // Scroll and resize listener for buttons
  useEffect(() => {
    const el = relatedScrollRef.current;
    if (!el) return;
    updateScrollButtons();
    const handleScroll = () => updateScrollButtons();
    el.addEventListener("scroll", handleScroll, { passive: true });
    window.addEventListener("resize", handleScroll);
    return () => {
      el.removeEventListener("scroll", handleScroll);
      window.removeEventListener("resize", handleScroll);
    };
  }, [relatedList]);

  // Auto-scroll to current anime in franchise order
  useEffect(() => {
    if (relatedList.length > 0 && currentRelatedRef.current && relatedScrollRef.current) {
      const timer = setTimeout(() => {
        const container = relatedScrollRef.current;
        const target = currentRelatedRef.current;
        if (container && target) {
          const targetLeft = target.offsetLeft;
          const targetWidth = target.offsetWidth;
          const containerWidth = container.clientWidth;
          const scrollTo = targetLeft - containerWidth / 2 + targetWidth / 2;
          container.scrollTo({ left: Math.max(0, scrollTo), behavior: "smooth" });
          updateScrollButtons();
        }
      }, 150);
      return () => clearTimeout(timer);
    }
  }, [relatedList, id]);

  const handleMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    const el = relatedScrollRef.current;
    if (!el) return;
    isDraggingRef.current = true;
    hasMovedRef.current = false;
    dragStartXRef.current = e.pageX - el.offsetLeft;
    scrollStartLeftRef.current = el.scrollLeft;
  };

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!isDraggingRef.current) return;
    const el = relatedScrollRef.current;
    if (!el) return;
    e.preventDefault();
    const x = e.pageX - el.offsetLeft;
    const walk = (x - dragStartXRef.current) * 1.3;
    if (Math.abs(x - dragStartXRef.current) > 5) {
      hasMovedRef.current = true;
    }
    el.scrollLeft = scrollStartLeftRef.current - walk;
    updateScrollButtons();
  };

  const handleMouseUp = () => {
    isDraggingRef.current = false;
  };

  const handleCardClick = (e: React.MouseEvent) => {
    if (hasMovedRef.current) {
      e.preventDefault();
      hasMovedRef.current = false;
    }
  };

  function toggleStatus(s: WatchStatus) {
    const next = status === s ? "none" : s;
    setStatus(next);
    setFavorite({ animeId: id, title, image, status: next });
    reachGoal("favorite_status", { animeId: id, title, status: next });
  }

  function rate(score: number) {
    if (!auth.token) return;
    const prev = myRating;
    const isToggle = prev === score;
    setMyRating(isToggle ? null : score);
    setRatingError(null);
    ratingEpochRef.current++;
    reachGoal("rate_anime", { animeId: id, title, score });
    api
      .post<{ avg?: number; count: number; my?: number }>("rating", { animeId: id, score }, true)
      .then((r) => {
        setAvgRating({ avg: r.avg, count: r.count });
        setMyRating(isToggle ? null : score);
      })
      .catch((e) => {
        setMyRating(prev);
        setRatingError(e?.message || "Не удалось поставить оценку");
      });
  }

  const epCount = totalEpisodes || 1;
  const ratingEpochRef = useRef(0);
  const resume = getProgress(id);
  const watchHref = (ep: number) => `/watch/${id}/${ep}?dub=${dubIdx}`;

  if (loading) return <p className="mt-8 text-text-muted">Загрузка озвучек…</p>;
  if (!dubs.length)
    return <p className="mt-8 text-text-muted">Видео пока не найдено для этого тайтла.</p>;

  return (
    <div className="grid w-full min-w-0 max-w-full gap-6 lg:grid-cols-[minmax(0,1.35fr)_minmax(300px,.65fr)]">
      <div className="w-full min-w-0 space-y-5 sm:space-y-6">
        <button
          onClick={() => {
            const ep = resume?.episode || 1;
            reachGoal("start_watch", { animeId: id, title, episode: ep });
            router.push(watchHref(ep));
          }}
          className="pulse-gradient flex w-full items-center justify-center gap-3 rounded-2xl py-3.5 sm:py-4 text-center text-base sm:text-lg font-bold text-white shadow-[0_12px_40px_rgba(255,77,141,.2)]"
        >
          <Icon name="play" className="h-5 w-5 fill-current" />{" "}
          {resume ? `Продолжить · Серия ${resume.episode}` : "Смотреть · Серия 1"}
        </button>

        {/* Кнопки статуса */}
        <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
          {STATUSES.map((st) => (
            <button
              key={st.s}
              onClick={() => toggleStatus(st.s)}
              className={`rounded-xl border px-3 py-2 text-xs font-semibold transition sm:px-4 sm:py-2.5 sm:text-sm ${
                status === st.s
                  ? "border-primary bg-primary/15 text-primary"
                  : "border-border bg-bg/30 text-text-muted hover:bg-surface"
              }`}
            >
              {st.label}
            </button>
          ))}
        </div>

        {/* Оценка 1-10 */}
        <div className="panel w-full min-w-0 overflow-hidden p-4 sm:p-5 md:p-6">
          <div className="mb-2 flex items-center gap-2 font-bold text-sm sm:text-base">
            Оценка
            {avgRating?.avg != null && (
              <span className="text-xs sm:text-sm font-normal text-primary">
                ♥ {avgRating.avg.toFixed(1)} ({avgRating.count})
              </span>
            )}
          </div>
          <div className="grid grid-cols-5 gap-1.5 sm:grid-cols-10 sm:gap-2">
            {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => {
              const filled = myRating != null && n <= myRating;
              const isPicked = myRating === n;
              return (
                <button
                  key={n}
                  onClick={() => rate(n)}
                  disabled={!auth.token}
                  title={auth.token ? `Поставить ${n}` : "Войдите, чтобы оценивать"}
                  className={`aspect-square rounded-xl text-sm font-bold transition disabled:opacity-40 ${
                    isPicked
                      ? "pulse-gradient text-white ring-2 ring-primary ring-offset-2 ring-offset-bg scale-105"
                      : filled
                      ? "pulse-gradient text-white/95"
                      : "bg-surface text-text"
                  }`}
                >
                  {n}
                </button>
              );
            })}
          </div>
          {!auth.token && <p className="mt-1 text-xs text-text-muted">Войдите, чтобы оценивать.</p>}
          {ratingError && <p className="mt-1 text-xs text-red-400">{ratingError}</p>}
        </div>

        {/* Порядок просмотра (Shikimori /related) */}
        {relatedList.length > 0 && (
          <div className="panel w-full min-w-0 overflow-hidden p-4 sm:p-5 md:p-6">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <h3 className="text-base sm:text-lg font-bold">Порядок просмотра</h3>
                <span className="rounded-full bg-surface px-2 py-0.5 text-xs font-semibold text-text-muted">
                  {relatedList.length}
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={() => scrollRelated("left")}
                  disabled={!canScrollLeft}
                  aria-label="Назад по порядку"
                  title="Прокрутить назад"
                  className="flex h-7 w-7 sm:h-8 sm:w-8 items-center justify-center rounded-lg border border-border bg-surface text-text transition hover:border-primary/50 hover:bg-surface disabled:pointer-events-none disabled:opacity-30 text-xs sm:text-sm"
                >
                  ←
                </button>
                <button
                  type="button"
                  onClick={() => scrollRelated("right")}
                  disabled={!canScrollRight}
                  aria-label="Вперёд по порядку"
                  title="Прокрутить вперёд"
                  className="flex h-7 w-7 sm:h-8 sm:w-8 items-center justify-center rounded-lg border border-border bg-surface text-text transition hover:border-primary/50 hover:bg-surface disabled:pointer-events-none disabled:opacity-30 text-xs sm:text-sm"
                >
                  →
                </button>
              </div>
            </div>
            <div
              ref={relatedScrollRef}
              onMouseDown={handleMouseDown}
              onMouseMove={handleMouseMove}
              onMouseUp={handleMouseUp}
              onMouseLeave={handleMouseUp}
              className="no-scrollbar -mx-1 flex w-full max-w-full gap-2.5 sm:gap-3 overflow-x-auto px-1 pb-1 scroll-smooth select-none cursor-grab active:cursor-grabbing"
            >
              {relatedList.map((node, i) => {
                const a = node.anime!;
                const year = a.aired_on ? new Date(a.aired_on).getFullYear() : "";
                const rel = node.relation_russian || node.relation;
                const current = a.id === id;
                return (
                  <a
                    key={`${a.id}-${i}`}
                    ref={current ? currentRelatedRef : null}
                    href={`/anime/${a.id}`}
                    onClick={handleCardClick}
                    className={`group flex w-[115px] sm:w-[125px] shrink-0 flex-col rounded-xl border transition ${
                      current
                        ? "border-primary bg-primary/15 ring-2 ring-primary/40 ring-offset-2 ring-offset-bg"
                        : "border-border bg-bg/30 hover:border-primary/50 hover:bg-surface"
                    }`}
                    title={a.russian || a.name}
                  >
                    <div className="relative aspect-[2/3] w-full overflow-hidden rounded-t-xl">
                      <span className="absolute left-1.5 top-1.5 z-10 rounded-full bg-black/70 px-1.5 py-0.5 text-[10px] font-bold text-white backdrop-blur">
                        #{i + 1}
                      </span>
                      {current && (
                        <span className="absolute right-1.5 top-1.5 z-10 rounded-full bg-primary px-1.5 py-0.5 text-[9px] font-extrabold text-white shadow">
                          Текущий
                        </span>
                      )}
                      <PosterImage
                        src={
                          shikimoriImage(a.image?.preview) ||
                          shikimoriImage(a.image?.original) ||
                          posterOriginal(a.id, a.image)
                        }
                        alt={a.russian || a.name}
                        className="h-full w-full object-cover transition group-hover:scale-105"
                      />
                    </div>
                    <div className="p-1.5 text-[11px] leading-tight">
                      <div className={`line-clamp-2 font-semibold ${current ? "text-primary font-bold" : "text-text"}`}>
                        {a.russian || a.name}
                      </div>
                      <div className="mt-0.5 text-text-muted">
                        {year ? `${year}` : ""}
                        {rel ? ` · ${rel}` : ""}
                      </div>
                    </div>
                  </a>
                );
              })}
            </div>
          </div>
        )}

        <div className="panel w-full min-w-0 overflow-hidden p-4 sm:p-5 md:p-6">
          <h3 className="mb-3 text-base sm:text-lg font-bold">Озвучка</h3>
          <div className="flex w-full flex-wrap gap-2">
            {dubs.map((d, i) => (
              <button
                key={i}
                onClick={() => {
                  reachGoal("change_dub", { animeId: id, dub: d.title });
                  setDubIdx(i);
                }}
                className={`rounded-xl border px-3 py-2 text-xs sm:px-4 sm:py-2.5 sm:text-sm font-semibold transition ${
                  i === dubIdx
                    ? "border-primary bg-primary/15 text-primary"
                    : "border-border bg-bg/30 text-text-muted hover:bg-surface"
                }`}
              >
                {d.title}
              </button>
            ))}
          </div>
        </div>

        <div className="panel w-full min-w-0 overflow-hidden p-4 sm:p-5 md:p-6">
          <h3 className="mb-3 text-base sm:text-lg font-bold">Серии</h3>
          <div className="grid grid-cols-5 gap-1.5 xs:grid-cols-6 sm:grid-cols-8 lg:grid-cols-10 sm:gap-2">
            {Array.from({ length: epCount }, (_, i) => i + 1).map((ep) => (
              <button
                key={ep}
                onClick={() => {
                  reachGoal("select_episode", { animeId: id, title, episode: ep });
                  router.push(watchHref(ep));
                }}
                className={`aspect-square rounded-xl text-xs sm:text-sm font-bold transition ${
                  resume?.episode === ep ? "pulse-gradient text-white shadow-md" : "bg-surface text-text hover:bg-surface-2"
                }`}
              >
                {ep}
              </button>
            ))}
          </div>
        </div>
      </div>

      <aside className="w-full min-w-0">
        <Comments animeId={String(id)} />
      </aside>
    </div>
  );
}
