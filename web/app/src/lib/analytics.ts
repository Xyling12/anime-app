import { GATEWAY, auth } from "./api";

const INSTALL_KEY = "anipulse_analytics_install_id";

function installId(): string {
  let value = localStorage.getItem(INSTALL_KEY);
  if (!value) {
    value = crypto.randomUUID();
    localStorage.setItem(INSTALL_KEY, value);
  }
  return value;
}

function send(event: "start" | "heartbeat" | "stop") {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`;
  return fetch(GATEWAY + "analytics/heartbeat", {
    method: "POST",
    headers,
    body: JSON.stringify({ installId: installId(), platform: "web", version: "web", event }),
    keepalive: event === "stop",
  }).catch(() => undefined);
}

export function startAnalytics(): () => void {
  let timer: ReturnType<typeof setInterval> | undefined;
  const start = () => {
    if (document.visibilityState !== "visible") return;
    void send("start");
    if (!timer) timer = setInterval(() => void send("heartbeat"), 60_000);
  };
  const stop = () => {
    if (timer) clearInterval(timer);
    timer = undefined;
    void send("stop");
  };
  const visibility = () => document.visibilityState === "visible" ? start() : stop();
  document.addEventListener("visibilitychange", visibility);
  window.addEventListener("pagehide", stop);
  start();
  return () => {
    document.removeEventListener("visibilitychange", visibility);
    window.removeEventListener("pagehide", stop);
    stop();
  };
}
