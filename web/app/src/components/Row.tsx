import Link from "next/link";
import type { ShikiAnime } from "@/lib/types";
import { PosterCard } from "./PosterCard";

/** Горизонтальная лента тайтлов с заголовком и ссылкой «Всё». */
export function Row({ title, items, href }: { title: string; items: ShikiAnime[]; href?: string }) {
  // Дедуп по id: Shikimori иногда отдаёт дубли внутри ленты → одинаковые React key.
  const seen = new Set<number>();
  items = items.filter((a) => (seen.has(a.id) ? false : seen.add(a.id)));
  if (!items.length) return null;
  return (
    <section className="mb-14">
      <div className="mb-5 flex items-baseline justify-between">
        <h2 className="section-title">{title}</h2>
        {href && (
          <Link href={href} className="text-sm font-semibold text-text-muted transition hover:text-primary">
            Смотреть все →
          </Link>
        )}
      </div>
      <div className="no-scrollbar grid auto-cols-[minmax(150px,1fr)] grid-flow-col gap-4 overflow-x-auto pb-4 md:auto-cols-[minmax(170px,1fr)] lg:auto-cols-[minmax(185px,1fr)]">
        {items.map((a) => (
          <div key={a.id}>
            <PosterCard anime={a} />
          </div>
        ))}
      </div>
    </section>
  );
}
