"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { dmList, type DmThread } from "@/lib/social";
import { useMe } from "@/lib/useMe";
import { Avatar } from "@/components/Avatar";

export default function DmListPage() {
  const { me, loading } = useMe();
  const [threads, setThreads] = useState<DmThread[]>([]);

  useEffect(() => {
    if (!me) return;
    dmList().then(setThreads).catch(() => {});
  }, [me]);

  if (loading) return <p className="p-10 text-center text-text-muted">Загрузка…</p>;
  if (!me)
    return (
      <p className="p-10 text-center text-text-muted">
        <Link href="/profile" className="text-primary">Войдите</Link>, чтобы читать ЛС.
      </p>
    );

  return (
    <div className="page-shell"><div className="mb-8"><div className="eyebrow mb-2">Сообщество</div><h1 className="text-4xl font-black tracking-[-.045em]">Личные сообщения</h1><p className="mt-2 text-text-muted">Все личные диалоги в одном месте.</p></div>
      {!threads.length ? (
        <p className="text-text-muted">Пока нет диалогов. Напиши из карточки пользователя.</p>
      ) : (
        <div className="panel divide-y divide-border overflow-hidden">
          {threads.map((t) => (
            <Link
              key={t.withNick}
              href={`/dm/${encodeURIComponent(t.withNick)}`}
              className="group flex items-center gap-4 p-5 transition hover:bg-surface-hover"
            >
              <div className="relative">
                <Avatar id={t.withAvatar} size={42} nick={t.withNick} />
                {t.withOnline && (
                  <span className="absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-surface bg-green-500" />
                )}
              </div>
              <div className="min-w-0 flex-1">
                <div className="font-semibold">{t.withNick}</div>
                <div className="truncate text-sm text-text-muted">{t.lastText}</div>
              </div>
              {t.unread > 0 && (
                <span className="rounded-full bg-primary px-2 py-0.5 text-xs text-white">{t.unread}</span>
              )}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
