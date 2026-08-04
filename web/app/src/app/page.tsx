import Link from "next/link";
import { calendar, catalog } from "@/lib/catalog";
import { Row } from "@/components/Row";
import { ContinueRow } from "@/components/ContinueRow";
import { posterOriginal } from "@/lib/api";
import type { ShikiAnime, ShikiCalendarEntry } from "@/lib/types";
import { Icon } from "@/components/Icon";
import { PosterImage } from "@/components/PosterImage";

export const revalidate = 900;

async function safe(promise: Promise<ShikiAnime[]>): Promise<ShikiAnime[]> {
  try {
    return await promise;
  } catch {
    return [];
  }
}

async function safeCalendar(): Promise<ShikiCalendarEntry[]> {
  try {
    return await calendar();
  } catch {
    return [];
  }
}

function scheduledAnime(entries: ShikiCalendarEntry[]): ShikiAnime[] {
  const now = Date.now();
  const windowStart = now - 6 * 60 * 60 * 1000;
  const windowEnd = now + 7 * 24 * 60 * 60 * 1000;
  const seen = new Set<number>();

  return entries
    .filter((entry) => {
      const at = entry.next_episode_at ? Date.parse(entry.next_episode_at) : NaN;
      return Number.isFinite(at) && at >= windowStart && at <= windowEnd;
    })
    .sort((a, b) => Date.parse(a.next_episode_at!) - Date.parse(b.next_episode_at!))
    .map((entry) => entry.anime)
    .filter((anime) => (seen.has(anime.id) ? false : seen.add(anime.id)));
}

export default async function Home() {
  const [calendarEntries, ongoingFallback, popular, ranked] = await Promise.all([
    safeCalendar(),
    safe(catalog({ order: "popularity", status: "ongoing" })),
    safe(catalog({ order: "popularity" })),
    safe(catalog({ order: "ranked" })),
  ]);
  const scheduled = scheduledAnime(calendarEntries);
  const ongoing = scheduled.length ? scheduled : ongoingFallback;
  const hero = ongoing[0];

  return <div className="pb-12">
    {hero ? <section className="cinema-hero">
      <PosterImage src={posterOriginal(hero.id, hero.image)} alt="" fallbackMode="backdrop" className="cinema-hero-backdrop"/>
      <div className="cinema-hero-shade"/>
      <div className="cinema-hero-poster">
        <PosterImage src={posterOriginal(hero.id, hero.image)} alt={hero.russian?.trim() || hero.name}/>
      </div>
      <div className="cinema-hero-content">
        <span className="cinema-live">● Новая серия</span>
        <p className="eyebrow mt-5">Выбор AniPulse · сейчас выходит</p>
        <h1>{hero.russian?.trim() || hero.name}</h1>
        <div className="cinema-meta">
          {hero.score && <span>★ {hero.score} Shikimori</span>}
          {hero.kind && <span>{hero.kind.toUpperCase()}</span>}
          {hero.episodes_aired ? <span>{hero.episodes_aired} серия</span> : null}
        </div>
        <div className="flex flex-wrap gap-3">
          <Link href={`/anime/${hero.id}`} className="cinema-primary"><Icon name="play" className="h-5 w-5 fill-current"/> Смотреть</Link>
          <Link href="/my" className="cinema-secondary">＋ В моё</Link>
        </div>
      </div>
    </section> : <section className="cinema-hero cinema-hero-empty"><div className="cinema-hero-content"><p className="eyebrow">AniPulse</p><h1>Истории в вашем ритме</h1><p className="text-text-muted">Каталог временно недоступен — попробуйте обновить страницу позже.</p></div></section>}
    <div className="site-container py-10">
      <ContinueRow/>
      <Row title="Сейчас выходит" items={ongoing} href="/catalog?filter=ongoing"/>
      <Row title="Популярное" items={popular} href="/catalog"/>
      <Row title="Высший рейтинг" items={ranked} href="/catalog?filter=ranked"/>
    </div>
  </div>;
}
