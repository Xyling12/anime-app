import { details } from "@/lib/catalog";
import { posterOriginal } from "@/lib/api";
import type { Metadata } from "next";
import { TitleClient } from "./TitleClient";
import { Icon } from "@/components/Icon";
import { PosterImage } from "@/components/PosterImage";

// ISR: страница тайтла — статика с часовым обновлением. Именно её индексирует Google.
export const revalidate = 3600;

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  try {
    const d = await details(Number(id));
    const title = d.russian?.trim() || d.name;
    const desc = (d.description || "").replace(/\[[^\]]*\]/g, "").slice(0, 160);
    return {
      title: `${title} — смотреть онлайн — AniPulse`,
      description: desc || `Смотреть аниме «${title}» онлайн бесплатно с русской озвучкой на AniPulse.`,
      openGraph: { title, description: desc, images: [posterOriginal(d.id, d.image)] },
    };
  } catch {
    return { title: "Аниме — AniPulse" };
  }
}

export default async function AnimePage({ params }: Props) {
  const { id } = await params;
  const d = await details(Number(id));
  const title = d.russian?.trim() || d.name;
  const cleanDesc = (d.description || "").replace(/\[[^\]]*\]/g, "");

  return (
    <div className="relative z-10">
      <section className="relative min-h-[520px] overflow-hidden">
        <PosterImage src={posterOriginal(d.id,d.image)} alt="" fallbackMode="backdrop" className="absolute inset-0 h-full w-full scale-105 object-cover opacity-40 blur-sm"/>
        <div className="absolute inset-0 bg-gradient-to-t from-bg via-bg/70 to-bg/30"/><div className="absolute inset-0 bg-gradient-to-r from-bg via-bg/70 to-transparent"/>
        <div className="site-container relative flex min-h-[520px] items-end gap-8 pb-12 pt-20">
        <PosterImage
          src={posterOriginal(d.id, d.image)}
          alt={title}
          className="hidden aspect-[2/3] w-56 shrink-0 rounded-[24px] border border-white/10 object-cover shadow-2xl md:block lg:w-64"
        />
        <div className="max-w-3xl flex-1 pb-2">
          <div className="eyebrow mb-3">Карточка тайтла</div><h1 className="mb-3 text-4xl font-black leading-[1.06] tracking-[-.05em] md:text-6xl">{title}</h1>
          <div className="mb-5 flex flex-wrap items-center gap-2 text-sm text-text-muted">
            {d.score && <span className="flex items-center gap-1 font-bold text-amber-300"><Icon name="star" className="h-4 w-4 fill-current"/>{d.score}</span>}
            {d.aired_on && ` · ${new Date(d.aired_on).getFullYear()}`}
            {d.episodes ? ` · Эп: ${d.episodes}` : ""}
            {d.kind && ` · ${d.kind.toUpperCase()}`}
          </div>
          {d.genres && d.genres.length > 0 && (
            <div className="mb-4 flex flex-wrap gap-2">
              {d.genres.map((g) => (
                <span key={g.id} className="rounded-full border border-white/10 bg-black/20 px-3 py-1.5 text-xs text-text-muted backdrop-blur">
                  {g.russian || g.name}
                </span>
              ))}
            </div>
          )}
          {cleanDesc && <p className="line-clamp-4 max-w-3xl text-base leading-7 text-text-muted">{cleanDesc}</p>}
        </div>
        </div>
      </section>

      {/* Клиентский блок: озвучки, серии, кнопка «Смотреть» */}
      <div className="page-shell !pt-0"><TitleClient id={d.id} title={title} totalEpisodes={Math.max(d.episodes_aired || 0, d.episodes || 0)} image={d.image} /></div>
    </div>
  );
}
