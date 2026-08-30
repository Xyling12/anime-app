import Link from "next/link";
import { Icon } from "@/components/Icon";

export default function NotFound() {
  return (
    <div className="page-shell flex min-h-[60vh] flex-col items-center justify-center text-center">
      <div className="pulse-gradient mb-6 grid h-20 w-20 place-items-center rounded-3xl text-3xl font-black text-white shadow-2xl">
        404
      </div>
      <h1 className="mb-2 text-2xl font-black md:text-3xl">Страница не найдена</h1>
      <p className="mb-8 max-w-md text-sm text-text-muted">
        Возможно, тайтл был удален, ссылка устарела или вы перешли по неверному адресу.
      </p>
      <div className="flex flex-wrap items-center justify-center gap-3">
        <Link
          href="/"
          className="pulse-gradient flex items-center gap-2 rounded-xl px-5 py-3 text-sm font-bold text-white shadow-lg"
        >
          <Icon name="home" className="h-4 w-4" /> На главную
        </Link>
        <Link
          href="/catalog"
          className="flex items-center gap-2 rounded-xl border border-border bg-surface px-5 py-3 text-sm font-bold hover:bg-surface-hover"
        >
          <Icon name="compass" className="h-4 w-4" /> В каталог
        </Link>
      </div>
    </div>
  );
}
