"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { comments, sendComment } from "@/lib/social";
import { useMe } from "@/lib/useMe";
import { Avatar } from "@/components/Avatar";
import type { ChatMessage } from "@/lib/types";
import { Icon } from "./Icon";

/** Комментарии к тайтлу (animeId — строка, серверный ключ). */
export function Comments({ animeId }: { animeId: string }) {
  const { me } = useMe();
  const [list, setList] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [revealed, setRevealed] = useState<Set<number>>(new Set());

  useEffect(() => {
    comments(animeId).then(setList).catch(() => {});
  }, [animeId]);

  async function post() {
    if (!input.trim()) return;
    try {
      const cm = await sendComment(animeId, input.trim());
      setList((prev) => [...prev, cm]);
      setInput("");
    } catch {}
  }

  return (
    <section className="panel sticky top-24 overflow-hidden p-5 md:p-6">
      <div className="mb-5 flex items-center justify-between"><div><div className="eyebrow mb-1">Обсуждение</div><h3 className="text-lg font-bold">Комментарии</h3></div><span className="rounded-full bg-surface-2 px-3 py-1 text-xs font-bold">{list.length}</span></div>

      {me ? (
        <div className="mb-5 flex gap-2 rounded-2xl border border-border bg-bg/40 p-2 focus-within:border-primary/60">
          <textarea rows={2}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => { if(e.key === "Enter" && !e.shiftKey){ e.preventDefault(); post(); }}}
            placeholder="Написать комментарий…"
            className="min-w-0 flex-1 resize-none bg-transparent px-2 py-1 text-sm outline-none"
          />
          <button onClick={post} className="pulse-gradient self-end rounded-xl p-3 text-white"><Icon name="send" className="h-4 w-4"/></button>
        </div>
      ) : (
        <p className="mb-4 text-sm text-text-muted">
          <Link href="/profile" className="text-primary">Войдите</Link>, чтобы комментировать.
        </p>
      )}

      <div className="max-h-[620px] space-y-2 overflow-y-auto pr-1">
        {!list.length && <p className="text-text-muted">Пока нет комментариев — будь первым!</p>}
        {list.map((c) => (
          <div key={c.id} className="flex gap-3 rounded-2xl bg-bg/35 p-3">
            <Link href={`/u/${encodeURIComponent(c.nick)}`}>
              <Avatar id={c.avatar} size={32} nick={c.nick} />
            </Link>
            <div className="min-w-0 flex-1">
              <div className="text-xs font-bold text-primary">{c.nick}</div>
              {c.spoiler && !revealed.has(c.id) ? (
                <button
                  onClick={() => setRevealed((s) => new Set(s).add(c.id))}
                  className="rounded bg-surface-2 px-2 py-1 text-sm text-text-muted"
                >
                  ⚠ Спойлер — нажми, чтобы открыть
                </button>
              ) : (
                <div className="text-sm break-words">{c.text}</div>
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
