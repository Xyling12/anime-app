"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon, type IconName } from "@/components/Icon";

const MAIN: { href: string; label: string; icon: IconName }[] = [
  { href: "/", label: "Главная", icon: "home" },
  { href: "/catalog", label: "Каталог", icon: "compass" },
  { href: "/schedule", label: "Эфир", icon: "calendar" },
  { href: "/my", label: "Моё", icon: "heart" },
  { href: "/profile", label: "Профиль", icon: "user" },
];
const SOCIAL: { href: string; label: string; icon: IconName }[] = [
  { href: "/chats", label: "Сообщество", icon: "message" },
  { href: "/dm", label: "Сообщения", icon: "mail" },
  { href: "/friends", label: "Друзья", icon: "users" },
  { href: "/notifications", label: "Уведомления", icon: "bell" },
];

export function NavBar() {
  const path = usePathname();
  if (path.startsWith("/watch/")) return null;
  const active = (href: string) => (href === "/" ? path === "/" : path.startsWith(href));
  const item = (entry: (typeof MAIN)[number]) => (
    <Link
      key={entry.href}
      href={entry.href}
      className={`cinema-nav-item ${active(entry.href) ? "is-active" : ""}`}
    >
      <Icon name={entry.icon} className="h-5 w-5" />
      <span>{entry.label}</span>
    </Link>
  );

  return (
    <>
      <aside className="cinema-sidebar">
        <Link href="/" className="cinema-logo">
          <img src="/brand/anipulse-icon.png" alt="" />
          <b>AniPulse</b>
        </Link>
        <nav>{MAIN.map(item)}</nav>
        <div className="cinema-nav-divider">
          <span>Сообщество</span>
        </div>
        <nav>{SOCIAL.map(item)}</nav>
        <Link href="/profile" className="cinema-user">
          <span>AK</span>
          <b>Профиль</b>
          <small>Настройки</small>
        </Link>
      </aside>
      <header className="cinema-topbar">
        <Link href="/" className="cinema-logo">
          <img src="/brand/anipulse-icon.png" alt="" />
          <b>AniPulse</b>
        </Link>
        <form action="/catalog">
          <label>
            <Icon name="search" className="h-4 w-4" />
            <input name="q" placeholder="Название, жанр или персонаж" />
            <kbd>Enter</kbd>
          </label>
        </form>
        <div className="flex items-center gap-1">
          <Link
            href="/catalog"
            aria-label="Поиск"
            className="grid h-9 w-9 place-items-center rounded-lg text-text-muted transition hover:bg-surface hover:text-primary md:hidden"
          >
            <Icon name="search" className="h-5 w-5" />
          </Link>
          <Link href="/friends" aria-label="Друзья">
            <Icon name="users" className="h-5 w-5" />
          </Link>
          <Link href="/dm" aria-label="Сообщения">
            <Icon name="mail" className="h-5 w-5" />
          </Link>
          <Link href="/notifications" aria-label="Уведомления">
            <Icon name="bell" className="h-5 w-5" />
          </Link>
        </div>
      </header>
      <nav className="cinema-mobile-nav">{MAIN.map(item)}</nav>
    </>
  );
}
