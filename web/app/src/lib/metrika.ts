/**
 * Yandex.Metrika Counter & Goal Tracker
 */
export const METRIKA_ID = 111767676;

declare global {
  interface Window {
    ym?: (id: number, action: string, ...args: any[]) => void;
  }
}

/**
 * Отправка цели (события/клика) в Яндекс Метрику.
 */
export function reachGoal(target: string, params?: Record<string, any>) {
  if (typeof window !== "undefined" && typeof window.ym === "function") {
    try {
      window.ym(METRIKA_ID, "reachGoal", target, params);
    } catch (e) {
      console.warn("Metrika reachGoal error:", e);
    }
  }
}
