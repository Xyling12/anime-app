"use client";

import { use, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { userCard, friendAction, type UserCard } from "@/lib/social";
import { useMe } from "@/lib/useMe";
import { Avatar } from "@/components/Avatar";

export default function UserPage({ params }: { params: Promise<{ nick: string }> }) {
  const { nick: raw } = use(params);
  const nick = decodeURIComponent(raw);
  const router = useRouter();
  const { me } = useMe();
  const [card, setCard] = useState<UserCard | null>(null);
  const [friendState, setFriendState] = useState<string>("");

  useEffect(() => {
    userCard(nick)
      .then((c) => {
        setCard(c);
        setFriendState(c.friendState || "none");
      })
      .catch(() => setCard(null));
  }, [nick]);

  async function friendBtn() {
    const action = friendState === "friends" ? "remove" : friendState === "incoming" ? "accept" : "add";
    const r = await friendAction(action, nick).catch(() => null);
    if (r?.state) setFriendState(r.state);
  }

  if (!card) return <p className="p-10 text-center text-text-muted">Загрузка…</p>;

  const friendLabel =
    friendState === "friends" ? "В друзьях ✓" : friendState === "incoming" ? "Принять заявку" : friendState === "outgoing" ? "Заявка отправлена" : "В друзья";

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

      {me && me.nick !== card.nick && (
        <div className="mt-6 flex gap-2">
          <button
            onClick={() => router.push(`/dm/${encodeURIComponent(card.nick)}`)}
            className="pulse-gradient flex-1 rounded-full py-2.5 font-bold text-white"
          >
            Написать
          </button>
          <button onClick={friendBtn} className="flex-1 rounded-full border border-border py-2.5 font-bold">
            {friendLabel}
          </button>
        </div>
      )}
    </div></div>
  );
}
