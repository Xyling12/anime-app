"use client";

import { use, useEffect, useState } from "react";
import { userCard, type UserCard } from "@/lib/social";
import { Avatar } from "@/components/Avatar";

export default function UserPage({ params }: { params: Promise<{ nick: string }> }) {
  const { nick: raw } = use(params);
  const nick = decodeURIComponent(raw);
  const [card, setCard] = useState<UserCard | null>(null);

  useEffect(() => {
    userCard(nick)
      .then((c) => {
        setCard(c);
      })
      .catch(() => setCard(null));
  }, [nick]);

  if (!card) return <p className="p-10 text-center text-text-muted">Загрузка…</p>;

  return (
    <div className="page-shell"><div className="panel mx-auto max-w-3xl p-7 text-center md:p-10">
      <div className="relative mx-auto w-fit">
        <Avatar id={card.avatar} size={96} nick={card.nick} />
        {card.online && (
          <span className="absolute bottom-1 right-1 h-4 w-4 rounded-full border-2 border-bg bg-green-500" />
        )}
      </div>
      <h1 className="mt-3 text-2xl font-bold">{card.nick}</h1>
      <div className="text-sm text-text-muted">{card.online ? "онлайн" : "не в сети"}</div>
      {card.bio && <p className="mt-2 text-sm">{card.bio}</p>}

      {card.stats && (
        <div className="mt-8 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
          {[
            [card.stats.watchedEpisodes, "серий"],
            [Math.round(card.stats.watchMinutes / 60), "часов"],
            [card.stats.startedTitles, "тайтлов"],
            [card.commentsCount, "комментов"],
          ].map(([v, l], i) => (
            <div key={i} className="rounded-2xl border border-border bg-bg/35 p-4">
              <div className="font-bold">{v}</div>
              <div className="text-xs text-text-muted">{l}</div>
            </div>
          ))}
        </div>
      )}

      {card.favoriteGenre && (
        <div className="mt-3 inline-block rounded-full bg-surface px-3 py-1 text-xs text-text-muted">
          Любимый жанр: {card.favoriteGenre}
        </div>
      )}

    </div></div>
  );
}
