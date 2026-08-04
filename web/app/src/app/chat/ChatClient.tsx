"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { chat, sendChat } from "@/lib/social";
import { useMe } from "@/lib/useMe";
import { Avatar } from "@/components/Avatar";
import type { ChatMessage } from "@/lib/types";
import { Icon } from "@/components/Icon";

const MENTION = /@[\wа-яё.\-]{2,24}/gi;

function renderText(text: string, myNick?: string | null) {
  const parts: React.ReactNode[] = [];
  let last = 0;
  for (const m of text.matchAll(MENTION)) {
    const i = m.index!;
    if (i > last) parts.push(text.slice(last, i));
    const isMe = myNick && m[0].toLowerCase() === `@${myNick.toLowerCase()}`;
    parts.push(
      <span key={i} className={`font-bold text-primary ${isMe ? "rounded bg-primary/20 px-0.5" : ""}`}>
        {m[0]}
      </span>,
    );
    last = i + m[0].length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts;
}

export function ChatClient() {
  const { me } = useMe();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [error, setError] = useState<string | null>(null);
  const bottom = useRef<HTMLDivElement>(null);

  // Поллинг чата каждые 4с (как в Android)
  useEffect(() => {
    let alive = true;
    async function poll() {
      const after = messages[messages.length - 1]?.id ?? 0;
      try {
        const fresh = await chat(after);
        if (alive && fresh.length) setMessages((prev) => [...prev, ...fresh].slice(-200));
      } catch {}
    }
    poll();
    const t = setInterval(poll, 4000);
    return () => {
      alive = false;
      clearInterval(t);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [messages.length === 0]);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length]);

  async function send() {
    if (!input.trim()) return;
    setError(null);
    try {
      const msg = await sendChat(input.trim());
      setMessages((prev) => [...prev, msg]);
      setInput("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Не удалось отправить");
    }
  }

  return (
    <div className="page-shell !pb-8"><div className="panel mx-auto flex h-[calc(100vh-170px)] min-h-[620px] max-w-5xl overflow-hidden">
      <aside className="hidden w-64 shrink-0 border-r border-border p-5 md:block"><div className="eyebrow mb-2">Общий канал</div><h1 className="text-xl font-black">Чат AniPulse</h1><p className="mt-2 text-sm leading-6 text-text-muted">Будь вежлив, скрывай спойлеры и находи единомышленников.</p><Link href="/chats" className="mt-6 flex items-center gap-2 text-sm font-bold text-primary"><Icon name="back" className="h-4 w-4"/>Все разделы</Link></aside>
      <div className="flex min-w-0 flex-1 flex-col p-4 md:p-5"><div className="mb-3 flex items-center justify-between border-b border-border pb-4 md:hidden"><h1 className="font-bold">Общий чат</h1><Link href="/chats" className="text-sm text-primary">Назад</Link></div>

      <div className="flex-1 space-y-3 overflow-y-auto px-1 py-2">
        {!messages.length && <p className="py-10 text-center text-text-muted">Пока тихо… Напиши первым!</p>}
        {messages.map((m) => {
          const mine = m.nick === me?.nick;
          return (
            <div key={m.id} className={`flex gap-2 ${mine ? "flex-row-reverse" : ""}`}>
              {!mine && (
                <Link href={`/u/${m.nick}`}>
                  <Avatar id={m.avatar} size={32} nick={m.nick} />
                </Link>
              )}
              <div
                className={`max-w-[75%] rounded-2xl px-3 py-2 ${
                  mine ? "bg-primary/20" : "bg-surface-2"
                }`}
              >
                {!mine && <div className="text-xs font-bold text-primary">{m.nick}</div>}
                <div className="text-sm">{renderText(m.text, me?.nick)}</div>
              </div>
            </div>
          );
        })}
        <div ref={bottom} />
      </div>

      {error && <p className="text-sm text-red-400">{error}</p>}

      {me ? (
        <div className="mt-3 flex gap-2 rounded-2xl border border-border bg-bg/40 p-2 focus-within:border-primary/60">
          <textarea rows={1}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {if(e.key === "Enter" && !e.shiftKey){e.preventDefault();send();}}}
            placeholder="Сообщение…"
            className="min-w-0 flex-1 resize-none bg-transparent px-3 py-2 outline-none"
          />
          <button onClick={send} className="pulse-gradient rounded-xl p-3 text-white"><Icon name="send" className="h-5 w-5"/></button>
        </div>
      ) : (
        <p className="py-3 text-center text-sm text-text-muted">
          <Link href="/profile" className="text-primary">
            Войдите
          </Link>
          , чтобы писать в чат.
        </p>
      )}
    </div></div></div>
  );
}
