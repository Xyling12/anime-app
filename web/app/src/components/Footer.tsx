import Link from "next/link";
import { Icon } from "./Icon";

export function Footer() {
  return <footer className="relative z-10 border-t border-border bg-bg-elevated/60 px-5 py-12 md:py-14">
    <div className="site-container grid gap-10 md:grid-cols-[1.4fr_.7fr_.7fr]">
      <div><div className="mb-4 flex items-center gap-3"><span className="pulse-gradient grid h-9 w-9 place-items-center rounded-xl font-black text-white">A</span><span className="text-xl font-black">Ani<span className="text-primary">Pulse</span></span></div><p className="max-w-md text-sm leading-6 text-text-muted">Аниме, расписание новых серий и личная коллекция в одном месте. Веб-версия находится в активной разработке.</p><div className="mt-4 flex gap-2"><span className="inline-flex rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-xs font-bold text-primary">Beta-версия</span><span className="inline-flex rounded-full border border-red-400/30 bg-red-400/10 px-3 py-1 text-xs font-black text-red-300">18+</span></div></div>
      <div><div className="mb-4 text-sm font-bold">Навигация</div><div className="grid gap-3 text-sm text-text-muted"><Link href="/catalog">Поиск</Link><Link href="/schedule">Расписание</Link><Link href="/my">Моя коллекция</Link></div></div>
      <div><div className="mb-4 text-sm font-bold">Информация</div><div className="grid gap-3 text-sm text-text-muted"><Link href="/privacy">Конфиденциальность</Link><Link href="/personal-data-consent">Согласие на обработку данных</Link><Link href="/terms">Пользовательское соглашение</Link><Link href="/community-rules">Правила сообщества</Link><Link href="/recommendations-rules">Правила рекомендаций</Link><Link href="/rightholders">Правообладателям</Link><a href="https://anipulsetv.ru/alapi/apk" className="flex items-center gap-2"><Icon name="download" className="h-4 w-4"/>Android-приложение</a></div></div>
    </div><div className="site-container mt-10 border-t border-border pt-6 text-xs leading-5 text-text-dim">© {new Date().getFullYear()} AniPulse · оператор: Ившин Максим Сергеевич · <a href="mailto:marc.1010@yandex.ru">marc.1010@yandex.ru</a><br/>18+. Видео предоставляется сторонними источниками.</div>
  </footer>;
}
