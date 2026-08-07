"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon, type IconName } from "@/components/Icon";

const MAIN: {href:string; label:string; icon:IconName}[] = [
  {href:"/",label:"Главная",icon:"home"},
  {href:"/catalog",label:"Поиск",icon:"search"},
  {href:"/schedule",label:"Расписание",icon:"calendar"},
  {href:"/my",label:"Моё",icon:"heart"},
  {href:"/profile",label:"Профиль",icon:"user"},
];
const SOCIAL: {href:string; label:string; icon:IconName}[] = [
  {href:"/notifications",label:"Уведомления",icon:"bell"},
];

export function NavBar() {
  const path = usePathname();
  if (path.startsWith("/watch/")) return null;
  const active = (href:string) => href === "/" ? path === "/" : path.startsWith(href);
  const item = (entry: typeof MAIN[number]) => <Link key={entry.href} href={entry.href} className={`cinema-nav-item ${active(entry.href)?"is-active":""}`}><Icon name={entry.icon} className="h-5 w-5"/><span>{entry.label}</span></Link>;

  return <>
    <aside className="cinema-sidebar">
      <Link href="/" className="cinema-logo"><img src="/brand/anipulse-icon.png" alt=""/><b>AniPulse</b></Link>
      <nav>{MAIN.map(item)}</nav>
      <div className="cinema-nav-divider"><span>События</span></div>
      <nav>{SOCIAL.map(item)}</nav>
      <Link href="/profile" className="cinema-user"><span>AK</span><b>Профиль</b><small>Настройки</small></Link>
    </aside>
    <header className="cinema-topbar">
      <Link href="/" className="cinema-logo"><img src="/brand/anipulse-icon.png" alt=""/><b>AniPulse</b></Link>
      <form action="/catalog"><label><Icon name="search" className="h-4 w-4"/><input name="q" placeholder="Название, жанр или персонаж"/><kbd>Enter</kbd></label></form>
      <div>
        <Link href="/notifications" aria-label="Уведомления"><Icon name="bell" className="h-5 w-5"/></Link>
        <span className="rounded-lg border border-red-400/35 bg-red-400/10 px-2 py-1 text-xs font-black text-red-300" aria-label="Возрастное ограничение 18+">18+</span>
      </div>
    </header>
    <nav className="cinema-mobile-nav">{MAIN.map(item)}</nav>
  </>;
}
