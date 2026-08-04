"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useMe } from "@/lib/useMe";
import { logout } from "@/lib/auth";
import { listFavorites } from "@/lib/favorites";
import { continueWatching } from "@/lib/progress";
import { useTheme } from "@/components/ThemeProvider";
import { Avatar } from "@/components/Avatar";
import { AuthDialog } from "@/components/AuthDialog";
import { Icon, type IconName } from "@/components/Icon";

const LINKS: { href: string; icon: IconName; title: string; text: string }[] = [
  { href: "/chats", icon: "message", title: "Сообщество", text: "Чаты, друзья и личные сообщения" },
  { href: "/notifications", icon: "bell", title: "Уведомления", text: "Упоминания и новые сообщения" },
  { href: "/privacy", icon: "shield", title: "Конфиденциальность", text: "Как мы работаем с данными" },
  { href: "/rightholders", icon: "info", title: "Правообладателям", text: "Связь и порядок обращений" },
];

export function ProfileClient() {
  const { me, loading, refresh } = useMe();
  const { theme, toggle } = useTheme();
  const [dialog, setDialog] = useState(false);
  const [stats, setStats] = useState({ all: 0, watching: 0, done: 0, progress: 0 });
  useEffect(() => {
    // Collection statistics come from the browser's localStorage.
    const load = () => setStats({ all: listFavorites().length, watching: listFavorites("watching").length, done: listFavorites("completed").length, progress: continueWatching().filter(({p}) => !p.watched).length });
    load();
    window.addEventListener("anipulse-sync", load);
    return () => window.removeEventListener("anipulse-sync", load);
  }, []);
  if (loading) return <div className="page-shell"><div className="skeleton h-80 rounded-3xl"/></div>;

  return <div className="page-shell">
    {dialog && <AuthDialog onClose={()=>setDialog(false)} onDone={()=>{setDialog(false); refresh();}}/>}
    <div className="mb-8"><div className="eyebrow mb-2">Личное пространство</div><h1 className="text-4xl font-black tracking-[-.045em] md:text-5xl">Профиль</h1></div>
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1.35fr)_minmax(320px,.65fr)]">
      <div className="space-y-6">
        <section className="panel relative overflow-hidden p-6 md:p-8">
          <div className="pointer-events-none absolute -right-24 -top-24 h-64 w-64 rounded-full bg-primary/15 blur-3xl"/>
          <div className="relative flex flex-col gap-5 sm:flex-row sm:items-center">
            <Avatar id={me?.avatar ?? 0} size={88} nick={me?.nick}/><div className="flex-1"><div className="flex flex-wrap items-center gap-3"><h2 className="text-2xl font-black">{me?.nick ?? "Гость"}</h2><span className="rounded-full border border-primary/20 bg-primary/10 px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider text-primary">Beta</span></div><p className="mt-1 text-sm text-text-muted">{me?.email ?? "Войдите, чтобы открыть общение и синхронизацию"}</p><p className="mt-3 max-w-xl text-sm leading-6 text-text-dim">Веб-версия AniPulse находится в разработке. Коллекция и прогресс уже доступны в этом браузере.</p></div>
            {!me && <button onClick={()=>setDialog(true)} className="pulse-gradient rounded-xl px-6 py-3 font-bold text-white">Войти</button>}
          </div>
          <div className="relative mt-7 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {[[stats.watching,"Смотрю"],[stats.all,"В коллекции"],[stats.done,"Просмотрено"],[stats.progress,"С прогрессом"]].map(([v,l])=><div key={String(l)} className="rounded-2xl border border-border bg-bg/45 p-4"><div className="text-2xl font-black">{v}</div><div className="mt-1 text-xs text-text-muted">{l}</div></div>)}
          </div>
        </section>
        <section className="panel overflow-hidden">
          {LINKS.map((l,i)=><Link key={l.href} href={l.href} className={`group flex items-center gap-4 p-5 transition hover:bg-surface-hover ${i ? "border-t border-border" : ""}`}><span className="grid h-11 w-11 place-items-center rounded-xl bg-primary/10 text-primary"><Icon name={l.icon} className="h-5 w-5"/></span><span className="flex-1"><b className="block">{l.title}</b><span className="text-sm text-text-muted">{l.text}</span></span><Icon name="chevron" className="h-5 w-5 text-text-dim transition group-hover:translate-x-1"/></Link>)}
        </section>
      </div>
      <aside className="space-y-6">
        <section className="panel p-6"><h3 className="mb-5 text-lg font-bold">Настройки сайта</h3><button onClick={toggle} className="flex w-full items-center gap-4 rounded-2xl border border-border bg-bg/40 p-4 text-left"><span className="grid h-10 w-10 place-items-center rounded-xl bg-accent/10 text-accent"><Icon name={theme === "dark" ? "moon" : "sun"} className="h-5 w-5"/></span><span className="flex-1"><b className="block">Тема оформления</b><span className="text-xs text-text-muted">{theme === "dark" ? "Тёмная" : "Светлая"}</span></span><span className={`h-7 w-12 rounded-full p-1 ${theme === "dark" ? "bg-primary" : "bg-surface-2"}`}><span className={`block h-5 w-5 rounded-full bg-white transition ${theme === "dark" ? "translate-x-5" : ""}`}/></span></button></section>
        <section className="panel p-6"><div className="mb-2 flex items-center gap-2"><Icon name="download" className="h-5 w-5 text-primary"/><h3 className="font-bold">AniPulse для Android</h3></div><p className="mb-4 text-sm leading-6 text-text-muted">Уведомления о сериях, полноэкранный плеер и обновления по воздуху.</p><a href="https://anipulsetv.ru/alapi/apk" className="inline-flex rounded-xl border border-border px-4 py-2.5 text-sm font-bold hover:bg-surface-hover">Скачать приложение</a></section>
        {me && <button onClick={()=>{logout();refresh();}} className="w-full rounded-xl border border-red-400/25 py-3 text-sm font-bold text-red-300 hover:bg-red-400/10">Выйти из аккаунта</button>}
      </aside>
    </div>
  </div>;
}
