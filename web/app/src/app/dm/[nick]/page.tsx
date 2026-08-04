"use client";

import { use, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { dmThread, dmSend, type DmMessage } from "@/lib/social";
import { useMe } from "@/lib/useMe";

export default function DmChatPage({ params }: { params: Promise<{ nick: string }> }) {
  const { nick: rawNick } = use(params);
  const nick = decodeURIComponent(rawNick);
  const router = useRouter();
  const { me } = useMe();
  const [messages, setMessages] = useState<DmMessage[]>([]);
  const [input, setInput] = useState("");
  const [error, setError] = useState<string | null>(null);
  const bottom = useRef<HTMLDivElement>(null);

  // Поллинг переписки 4с
  useEffect(() => {
    if (!me) return;
    let alive = true;
    async function poll() {
      const after = messages[messages.length - 1]?.id ?? 0;
      try {
        const fresh = await dmThread(nick, after);
        if (alive && fresh.length) setMessages((prev) => [...prev, ...fresh].slice(-200));
      } catch {}
    }
    poll();
    const t = setInterval(poll, 4000);
    return () => { alive = false; clearInterval(t); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [me, nick, messages.length === 0]);

  useEffect(() => { bottom.current?.scrollIntoView({ behavior: "smooth" }); }, [messages.length]);

  async function send() {
    if (!input.trim()) return;
    setError(null);
    try {
      const msg = await dmSend(nick, input.trim());
      setMessages((prev) => [...prev, msg]);
      setInput("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Не удалось отправить");
    }
  }

  return (
    <div className="page-shell !pb-8"><div className="panel mx-auto flex h-[calc(100vh-170px)] min-h-[620px] max-w-4xl flex-col p-4 md:p-6">
      <div className="mb-2 flex items-center gap-3">
        <button onClick={() => router.back()} className="text-xl">←</button>
        <h1 className="text-lg font-bold">{nick}</h1>
      </div>

      <div className="flex-1 space-y-2 overflow-y-auto py-2">
        {messages.map((m) => {
          const mine = m.from === me?.nick;
          return (
            <div key={m.id} className={`flex ${mine ? "justify-end" : "justify-start"}`}>
              <div className={`max-w-[75%] rounded-2xl px-4 py-3 text-sm ${mine ? "bg-primary/20" : "bg-surface-2"}`}>
                {m.text}
              </div>
            </div>
          );
        })}
        <div ref={bottom} />
      </div>

      {error && <p className="text-sm text-red-400">{error}</p>}

      <div className="mt-3 flex gap-2 rounded-2xl border border-border bg-bg/40 p-2 focus-within:border-primary/60">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && send()}
          placeholder="Сообщение…"
          className="min-w-0 flex-1 bg-transparent px-3 py-2.5 outline-none"
        />
        <button onClick={send} className="pulse-gradient rounded-full px-5 font-bold text-white">➤</button>
      </div>
    </div></div>
  );
}
