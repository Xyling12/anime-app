"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { notifications, markNotificationsRead, type Notification } from "@/lib/social";
import { useMe } from "@/lib/useMe";

const LABEL: Record<string, (from: string) => string> = {
  mention: (f) => `@${f} упомянул(а) вас`,
  dm: (f) => `${f} написал(а) вам`,
  friend_request: (f) => `${f} хочет добавить вас в друзья`,
  friend_accept: (f) => `${f} принял(а) вашу заявку`,
};

export default function NotificationsPage() {
  const { me, loading } = useMe();
  const [items, setItems] = useState<Notification[]>([]);

  useEffect(() => {
    if (!me) return;
    notifications()
      .then((n) => {
        setItems(n.reverse());
        markNotificationsRead().catch(() => {});
      })
      .catch(() => {});
  }, [me]);

  if (loading) return <p className="p-10 text-center text-text-muted">Загрузка…</p>;
  if (!me)
    return (
      <p className="p-10 text-center text-text-muted">
        <Link href="/profile" className="text-primary">Войдите</Link>, чтобы видеть уведомления.
      </p>
    );

  return (
    <div className="page-shell"><div className="mb-8"><div className="eyebrow mb-2">Центр событий</div><h1 className="text-4xl font-black tracking-[-.045em]">Уведомления</h1><p className="mt-2 text-text-muted">Ответы, упоминания, заявки и личные сообщения.</p></div>
      {!items.length ? (
        <p className="text-text-muted">Пока пусто.</p>
      ) : (
        <div className="panel divide-y divide-border overflow-hidden">
          {items.map((n) => {
            const href = n.type === "dm" ? `/dm/${encodeURIComponent(n.from)}` : n.type.startsWith("friend") ? "/friends" : "/chat";
            return (
              <Link key={n.id} href={href} className="block p-5 transition hover:bg-surface-hover">
                <div className="text-sm font-medium">{(LABEL[n.type] || (() => n.type))(n.from)}</div>
                {n.text && <div className="truncate text-sm text-text-muted">{n.text}</div>}
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
