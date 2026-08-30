/** Аккаунты: регистрация/вход/сброс/верификация + профиль. Те же эндпоинты, что у Android. */
import { api, auth } from "./api";
import type { Me } from "./types";

export interface AuthResponse {
  token?: string;
  nick?: string;
  email?: string;
  error?: string;
}

export function register(nick: string, email: string, password: string, acceptTerms: boolean, privacyConsent: boolean) {
  return api.post<AuthResponse>("auth/register", { nick, email, password, acceptTerms, privacyConsent });
}
export function login(loginId: string, password: string) {
  return api.post<AuthResponse>("auth/login", { login: loginId, password });
}
export function me() {
  return api.get<Me>("auth/me", true);
}
export function forgot(email: string) {
  return api.post<{ ok?: boolean; error?: string }>("auth/forgot", { email });
}
export function reset(email: string, code: string, password: string) {
  return api.post<AuthResponse>("auth/reset", { email, code, password });
}
export function verify(code: string) {
  return api.post<{ ok?: boolean; error?: string }>("auth/verify", { code }, true);
}
export function resendCode() {
  return api.post<{ ok?: boolean; error?: string }>("auth/resend", {}, true);
}
export function logout() {
  auth.token = null;
}
export function exchangeOAuthCode(code: string) {
  return api.post<AuthResponse>("auth/exchange", { code });
}

/** OAuth: страница входа VK/Яндекс. state=web → сервер редиректит на сайт, а не в приложение. */
export function oauthUrl(provider: "vk" | "yandex", linkToken?: string): string {
  const state = linkToken ? `web.link.${linkToken}` : "web";
  return `https://anipulsetv.ru/alapi/auth/${provider}?state=${encodeURIComponent(state)}`;
}
