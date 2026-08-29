"use client";

import { useEffect } from "react";
import { Icon } from "@/components/Icon";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("App error boundary caught:", error);
  }, [error]);

  return (
    <div className="page-shell flex min-h-[60vh] flex-col items-center justify-center text-center">
      <div className="mb-6 grid h-20 w-20 place-items-center rounded-3xl bg-red-500/10 text-3xl font-black text-red-500 shadow-2xl border border-red-500/20">
        !
      </div>
      <h1 className="mb-2 text-2xl font-black md:text-3xl">Что-то пошло не так</h1>
      <p className="mb-8 max-w-md text-sm text-text-muted">
        Произошла непредвиденная ошибка при загрузке страницы. Попробуйте обновить данные.
      </p>
      <button
        onClick={() => reset()}
        className="pulse-gradient flex items-center gap-2 rounded-xl px-6 py-3 text-sm font-bold text-white shadow-lg"
      >
        <Icon name="clock" className="h-4 w-4" /> Попробовать снова
      </button>
    </div>
  );
}
