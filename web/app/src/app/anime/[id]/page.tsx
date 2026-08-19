import { details } from "@/lib/catalog";
import { posterOriginal } from "@/lib/api";
import type { Metadata } from "next";
import { TitleClient } from "./TitleClient";
import { Icon } from "@/components/Icon";
import { PosterImage } from "@/components/PosterImage";

// ISR: страница тайтла — статика с минутным обновлением.
export const revalidate = 60;

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  try {
    const d = await details(Number(id));
    const title = d.russian?.trim() || d.name;
    const year = d.aired_on ? new Date(d.aired_on).getFullYear() : "";
    const epInfo = d.episodes ? ` (${d.episodes} серий)` : "";
    const rawDesc = (d.description || "").replace(/\[[^\]]*\]/g, "").slice(0, 160);
    const desc =
      rawDesc ||
      `Смотреть аниме «${title}»${year ? ` ${year} года` : ""}${epInfo} онлайн бесплатно в высоком качестве HD 1080p/720p с русской озвучкой на AniPulse.`;

    const posterUrl = posterOriginal(d.id, d.image);
    const keywords = [
      title,
      d.name,
      ...(d.english || []),
      "смотреть онлайн",
      "русская озвучка",
      "все серии бесплатно",
      "аниме в хорошем качестве",
      "AniLibria",
      "AniDUB",
      "HD 1080p",
      "AniPulse",
    ].filter(Boolean);

    return {
      title: `${title}${year ? ` (${year})` : ""} смотреть онлайн все серии с русской озвучкой бесплатно — AniPulse`,
      description: desc,
      keywords,
      alternates: {
        canonical: `https://anipulsetv.ru/anime/${d.id}`,
      },
      openGraph: {
        title: `${title}${year ? ` (${year})` : ""} — смотреть онлайн на AniPulse`,
        description: desc,
        url: `https://anipulsetv.ru/anime/${d.id}`,
        siteName: "AniPulse",
        locale: "ru_RU",
        type: d.kind === "movie" ? "video.movie" : "video.tv_show",
        images: [
          {
            url: posterUrl,
            width: 600,
            height: 900,
            alt: title,
          },
        ],
      },
      twitter: {
        card: "summary_large_image",
        title: `${title} — смотреть онлайн`,
        description: desc,
        images: [posterUrl],
      },
    };
  } catch {
    return { title: "Аниме — AniPulse" };
  }
}

export default async function AnimePage({ params }: Props) {
  const { id } = await params;
  const d = await details(Number(id));
  const title = d.russian?.trim() || d.name;
  const year = d.aired_on ? new Date(d.aired_on).getFullYear() : "";
  const cleanDesc = (d.description || "").replace(/\[[^\]]*\]/g, "");
  const posterUrl = posterOriginal(d.id, d.image);

  // Schema.org Structured Data
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": d.kind === "movie" ? "Movie" : "TVSeries",
    name: title,
    alternateName: [d.name, ...(d.english || []), ...(d.synonyms || [])].filter(Boolean),
    description: cleanDesc || `${title} смотреть онлайн на AniPulse`,
    image: posterUrl,
    datePublished: d.aired_on || undefined,
    numberOfEpisodes: d.episodes || undefined,
    genre: d.genres?.map((g) => g.russian || g.name) || [],
    inLanguage: "ru",
    potentialAction: {
      "@type": "WatchAction",
      target: `https://anipulsetv.ru/watch/${d.id}/1`,
    },
    ...(d.score
      ? {
          aggregateRating: {
            "@type": "AggregateRating",
            ratingValue: String(d.score),
            bestRating: "10",
            worstRating: "1",
            ratingCount: "100",
          },
        }
      : {}),
  };

  const breadcrumbJsonLd = {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: [
      {
        "@type": "ListItem",
        position: 1,
        name: "Главная",
        item: "https://anipulsetv.ru",
      },
      {
        "@type": "ListItem",
        position: 2,
        name: "Каталог",
        item: "https://anipulsetv.ru/catalog",
      },
      {
        "@type": "ListItem",
        position: 3,
        name: title,
        item: `https://anipulsetv.ru/anime/${d.id}`,
      },
    ],
  };

  return (
    <div className="relative z-10">
      {/* JSON-LD Rich Snippets for Search Engines */}
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(breadcrumbJsonLd) }}
      />

      <section className="relative min-h-[380px] overflow-hidden sm:min-h-[440px] md:min-h-[520px]">
        <PosterImage
          src={posterUrl}
          alt=""
          fallbackMode="backdrop"
          className="absolute inset-0 h-full w-full scale-105 object-cover opacity-40 blur-sm"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-bg via-bg/75 to-bg/30" />
        <div className="absolute inset-0 bg-gradient-to-r from-bg via-bg/70 to-transparent" />
        <div className="site-container relative flex min-h-[380px] flex-col items-start gap-5 pb-8 pt-16 sm:min-h-[440px] sm:flex-row sm:items-end sm:gap-7 md:min-h-[520px] md:gap-8 md:pb-12 md:pt-20">
          <PosterImage
            src={posterUrl}
            alt={title}
            loading="eager"
            className="aspect-[2/3] w-28 shrink-0 rounded-2xl border border-white/10 object-cover shadow-xl sm:w-44 md:w-56 md:rounded-[24px] md:shadow-2xl lg:w-64"
          />
          <div className="max-w-3xl flex-1 pb-2">
            <div className="eyebrow mb-2 sm:mb-3">Карточка тайтла</div>
            <h1 className="mb-2 text-2xl font-black leading-[1.08] tracking-[-.04em] sm:mb-3 sm:text-4xl md:text-5xl lg:text-6xl">
              {title}
            </h1>
            {d.name && d.name !== title && (
              <div className="mb-2 text-xs font-semibold text-text-dim sm:text-sm">
                {d.name} {d.english && d.english.length > 0 && `· ${d.english.join(", ")}`}
              </div>
            )}
            <div className="mb-3 flex flex-wrap items-center gap-2 text-xs text-text-muted sm:mb-5 sm:text-sm">
              {d.score && (
                <span className="flex items-center gap-1 font-bold text-amber-300">
                  <Icon name="star" className="h-3.5 w-3.5 fill-current sm:h-4 sm:w-4" />
                  {d.score}
                </span>
              )}
              {year && ` · ${year}`}
              {d.episodes ? ` · Эп: ${d.episodes}` : ""}
              {d.kind && ` · ${d.kind.toUpperCase()}`}
            </div>
            {d.genres && d.genres.length > 0 && (
              <div className="mb-3 flex flex-wrap gap-1.5 sm:mb-4 sm:gap-2">
                {d.genres.map((g) => (
                  <span
                    key={g.id}
                    className="rounded-full border border-white/10 bg-black/30 px-2.5 py-1 text-[11px] text-text-muted backdrop-blur sm:px-3 sm:py-1.5 sm:text-xs"
                  >
                    {g.russian || g.name}
                  </span>
                ))}
              </div>
            )}
            {cleanDesc && (
              <p className="line-clamp-3 max-w-3xl text-sm leading-relaxed text-text-muted sm:line-clamp-4 sm:text-base sm:leading-7">
                {cleanDesc}
              </p>
            )}
          </div>
        </div>
      </section>

      {/* Клиентский блок: озвучки, серии, кнопка «Смотреть» */}
      <div className="page-shell !pt-0 w-full min-w-0 overflow-hidden">
        <TitleClient
          id={d.id}
          title={title}
          totalEpisodes={Math.max(d.episodes_aired || 0, d.episodes || 0)}
          image={d.image}
        />
      </div>
    </div>
  );
}
