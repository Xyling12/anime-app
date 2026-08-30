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

  return (
    <div className="pb-12">
      {hero ? (
        <section className="cinema-hero">
          <PosterImage
            src={posterOriginal(hero.id, hero.image)}
            alt=""
            fallbackMode="backdrop"
            className="cinema-hero-backdrop"
          />
          <div className="cinema-hero-shade" />
          <div className="cinema-hero-poster">
            <PosterImage
              src={posterOriginal(hero.id, hero.image)}
              alt={hero.russian?.trim() || hero.name}
              loading="eager"
            />
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
              <Link href={`/anime/${hero.id}`} className="cinema-primary">
                <Icon name="play" className="h-5 w-5 fill-current" /> Смотреть
              </Link>
              <Link href="/my" className="cinema-secondary">
                ＋ В моё
              </Link>
            </div>
          </div>
        </section>
      ) : (
        <section className="cinema-hero cinema-hero-empty">
          <div className="cinema-hero-content">
            <p className="eyebrow">AniPulse</p>
            <h1>Истории в вашем ритме</h1>
            <p className="text-text-muted">Каталог временно недоступен — попробуйте обновить страницу позже.</p>
          </div>
        </section>
      )}

      <div className="site-container py-10">
        <ContinueRow />
        <Row title="Сейчас выходит" items={ongoing} href="/catalog?filter=ongoing" />
        <Row title="Популярное" items={popular} href="/catalog" />
        <Row title="Высший рейтинг" items={ranked} href="/catalog?filter=ranked" />

        {/* SEO Семантический информационный блок для поисковых систем */}
        <section className="mt-16 rounded-2xl border border-border bg-surface/40 p-6 md:p-8">
          <h2 className="mb-3 text-lg font-bold text-text sm:text-xl">
            Смотреть аниме онлайн бесплатно в хорошем качестве на AniPulse
          </h2>
          <p className="mb-3 text-xs leading-relaxed text-text-muted sm:text-sm">
            AniPulse — это современная платформа для комфортного просмотра японских аниме и донхуа онлайн. В нашем каталоге собраны тысячи тайтлов: от легендарной классики до горячих новинок текущего сезона. Все серии доступны в высоком качестве Full HD 1080p и 720p с качественной русской озвучкой от популярных студий дубляжа (AniLibria, AniDUB, SHIZA Project, Studio Band, Dream Cast) и оригинальной звуковой дорожкой с русскими субтитрами.
          </p>
          <div className="grid gap-4 pt-2 text-xs text-text-dim sm:grid-cols-3 sm:text-sm">
            <div>
              <h3 className="mb-1 font-semibold text-text">📅 Расписание онгоингов</h3>
              <p>Удобный график выхода новых серий по дням недели по московскому времени.</p>
            </div>
            <div>
              <h3 className="mb-1 font-semibold text-text">🔄 Синхронизация прогресса</h3>
              <p>Продолжайте просмотр с того места, где остановились, на ПК и телефоне.</p>
            </div>
            <div>
              <h3 className="mb-1 font-semibold text-text">🎬 Порядок просмотра</h3>
              <p>Хронологический список всех сезонов, фильмов и спецвыпусков франшизы.</p>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
