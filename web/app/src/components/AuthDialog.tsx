"use client";

import { useState } from "react";
import { register, login, forgot, reset } from "@/lib/auth";
import { auth } from "@/lib/api";
import { oauthUrl } from "@/lib/auth";
import { reachGoal } from "@/lib/metrika";

type Mode = "login" | "register" | "forgot";

export function AuthDialog({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [mode, setMode] = useState<Mode>("login");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resetSent, setResetSent] = useState(false);

  const [nick, setNick] = useState("");
  const [email, setEmail] = useState("");
  const [pass, setPass] = useState("");
  const [pass2, setPass2] = useState("");
  const [code, setCode] = useState("");

  async function submit() {
    setError(null);
    setBusy(true);
    try {
      if (mode === "login") {
        const r = await login(nick.trim(), pass);
        if (r.error) throw new Error(r.error);
        auth.token = r.token!;
        reachGoal("auth_login");
        onDone();
      } else if (mode === "register") {
        if (pass !== pass2) throw new Error("Пароли не совпадают");
        const r = await register(nick.trim(), email.trim(), pass, true, true);
        if (r.error) throw new Error(r.error);
        auth.token = r.token!;
        reachGoal("auth_register");
        onDone();
      } else {
        // forgot
        if (!resetSent) {
          await forgot(email.trim());
          setResetSent(true);
        } else {
          const r = await reset(email.trim(), code.trim(), pass);
          if (r.error) throw new Error(r.error);
          auth.token = r.token!;
          onDone();
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Ошибка");
    } finally {
      setBusy(false);
    }
  }

  const field = "w-full rounded-xl border border-border bg-bg/45 px-4 py-3.5 outline-none transition focus:border-primary/70";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-4 backdrop-blur-md" onClick={onClose}>
      <div
        className="relative w-full max-w-md overflow-hidden rounded-[28px] border border-white/10 bg-surface p-7 shadow-2xl md:p-9"
        onClick={(e) => e.stopPropagation()}
      >
        <button onClick={onClose} className="absolute right-5 top-5 grid h-9 w-9 place-items-center rounded-full bg-surface-2 text-text-muted">×</button>
        <div className="mb-5 flex items-center gap-3"><span className="pulse-gradient grid h-12 w-12 place-items-center rounded-2xl text-xl font-black text-white">A</span><div><div className="text-sm font-bold">AniPulse</div><div className="text-xs text-text-muted">единый аккаунт</div></div></div>
        <h2 className="mb-2 text-3xl font-black tracking-[-.04em]">
          {mode === "login" ? "Вход" : mode === "register" ? "Регистрация" : "Восстановление пароля"}
        </h2>
        <p className="mb-6 text-sm text-text-muted">{mode === "login" ? "Продолжи просмотр и общение с того места, где остановился." : mode === "register" ? "Сохраняй коллекцию, оценки и общайся с друзьями." : "Вернём доступ к аккаунту через почту."}</p>

        <div className="space-y-3">
          {mode === "register" && (
            <input className={field} placeholder="Ник" value={nick} onChange={(e) => setNick(e.target.value)} />
          )}
          {mode === "login" && (
            <input className={field} placeholder="Ник или почта" value={nick} onChange={(e) => setNick(e.target.value)} />
          )}
          {(mode === "register" || mode === "forgot") && (
            <input
              className={field}
              placeholder="Почта"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          )}
          {mode === "forgot" && resetSent && (
            <input className={field} placeholder="Код из письма" value={code} onChange={(e) => setCode(e.target.value)} />
          )}
          {(mode !== "forgot" || resetSent) && (
            <input
              className={field}
              placeholder={mode === "forgot" ? "Новый пароль" : "Пароль"}
              type="password"
              value={pass}
              onChange={(e) => setPass(e.target.value)}
            />
          )}
          {mode === "register" && (
            <input
              className={field}
              placeholder="Повтори пароль"
              type="password"
              value={pass2}
              onChange={(e) => setPass2(e.target.value)}
            />
          )}
        </div>

        {error && <p className="mt-3 text-sm text-red-400">{error}</p>}

        <button
          onClick={submit}
          disabled={busy}
          className="pulse-gradient mt-5 w-full rounded-xl py-3.5 font-bold text-white shadow-[0_10px_30px_rgba(255,77,141,.2)] disabled:opacity-50"
        >
          {busy
            ? "…"
            : mode === "login"
              ? "Войти"
              : mode === "register"
                ? "Создать аккаунт"
                : resetSent
                  ? "Сменить пароль"
                  : "Отправить код"}
        </button>

        {/* OAuth */}
        {mode !== "forgot" && (
          <>
            <div className="my-3 text-center text-xs text-text-muted">или через сервис</div>
            <div className="flex gap-2">
              <a href={oauthUrl("yandex")} className="flex-1 rounded-xl border border-border bg-bg/30 py-3 text-center text-sm font-bold hover:bg-surface-hover">
                <span className="mr-2 inline-grid h-6 w-6 place-items-center rounded-full bg-red-500 text-xs text-white">Я</span>Яндекс
              </a>
              <a href={oauthUrl("vk")} className="flex-1 rounded-xl border border-border bg-bg/30 py-3 text-center text-sm font-bold hover:bg-surface-hover">
                <span className="mr-2 inline-grid h-6 w-6 place-items-center rounded-full bg-blue-500 text-[10px] text-white">VK</span>VK
              </a>
            </div>
          </>
        )}

        {/* Переключатели режима */}
        <div className="mt-4 space-y-1 text-center text-sm text-text-muted">
          {mode === "login" && (
            <>
              <button onClick={() => setMode("register")} className="block w-full text-primary">
                Создать аккаунт
              </button>
              <button onClick={() => setMode("forgot")} className="block w-full">
                Забыли пароль?
              </button>
            </>
          )}
          {mode !== "login" && (
            <button onClick={() => { setMode("login"); setResetSent(false); }} className="block w-full text-primary">
              Уже есть аккаунт — войти
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
