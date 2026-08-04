"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { auth } from "@/lib/api";
import { exchangeOAuthCode } from "@/lib/auth";
import { syncAll } from "@/lib/sync";

function CallbackInner() {
  const router = useRouter();
  const params = useSearchParams();
  const [error, setError] = useState("");
  useEffect(() => {
    const code = params.get("code");
    if (!code) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setError("VK не вернул код авторизации");
      return;
    }
    exchangeOAuthCode(code)
      .then((result) => {
        if (!result.token) throw new Error(result.error || "Не удалось завершить вход");
        auth.token = result.token;
        return syncAll().catch(() => {}).then(() => router.replace("/profile"));
      })
      .catch((reason) => setError(reason instanceof Error ? reason.message : "Не удалось завершить вход"));
  }, [params, router]);
  if (error) return <div className="page-shell"><div className="panel mx-auto max-w-md p-8 text-center"><h1 className="text-2xl font-black">Не удалось войти</h1><p className="mt-3 text-sm text-red-300">{error}</p><a href="/profile" className="cinema-primary mt-6">Вернуться в профиль</a></div></div>;
  return <div className="page-shell"><div className="panel mx-auto max-w-md p-8 text-center"><div className="player-loader mx-auto"><i/><span>Завершаем вход</span><small>Получаем профиль AniPulse…</small></div></div></div>;
}

export default function AuthCallback() {
  return (
    <Suspense fallback={<p className="p-10 text-center text-text-muted">Входим…</p>}>
      <CallbackInner />
    </Suspense>
  );
}
