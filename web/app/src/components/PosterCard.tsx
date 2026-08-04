import Link from "next/link";
import type { ShikiAnime } from "@/lib/types";
import { posterOriginal } from "@/lib/api";
import { Icon } from "./Icon";
import { PosterImage } from "./PosterImage";

/** Карточка тайтла: постер с hover (подъём, свечение, play-оверлей, рейтинг). */
export function PosterCard({ anime }: { anime: ShikiAnime }) {
  const title = anime.russian?.trim() || anime.name;
  const kind = anime.kind ? anime.kind.toUpperCase() : null;
  return (
    <Link href={`/anime/${anime.id}`} className="card-hover group block min-w-0">
      <div className="relative aspect-[2/3] overflow-hidden rounded-[18px] border border-white/[.06] bg-surface-2 shadow-lg">
        <PosterImage
          src={posterOriginal(anime.id, anime.image)}
          alt={title}
          loading="lazy"
          className="h-full w-full object-cover transition duration-500 group-hover:scale-[1.04]"
        />
        {/* градиент снизу */}
        <div className="absolute inset-x-0 bottom-0 h-2/5 bg-gradient-to-t from-black/80 to-transparent opacity-0 transition group-hover:opacity-100" />
        {/* play по центру при ховере */}
        <div className="absolute inset-0 flex items-center justify-center opacity-0 transition group-hover:opacity-100">
          <span className="pulse-gradient flex h-12 w-12 items-center justify-center rounded-full text-white shadow-lg"><Icon name="play" className="ml-0.5 h-5 w-5 fill-current"/></span>
        </div>
        {anime.score && Number(anime.score) > 0 && (
          <span className="glass absolute right-2 top-2 flex items-center gap-1 rounded-lg border border-white/10 px-2 py-1 text-xs font-bold text-amber-300">
            <Icon name="star" className="h-3 w-3 fill-current"/> {Number(anime.score).toFixed(1)}
          </span>
        )}
      </div>
      <div className="mt-3 line-clamp-1 text-sm font-bold text-text transition group-hover:text-primary">
        {title}
      </div>
      {kind && <div className="mt-1 text-[11px] font-medium tracking-wide text-text-dim">{kind}</div>}
    </Link>
  );
}
