import { GATEWAY } from "@/lib/api";

/** 12 пресетов (градиент + эмодзи) как в Android. id=-1 — кастомная (грузится по нику). */
const PRESETS: [string, string, string][] = [
  ["#7C4DFF", "#FF4D8D", "⚡"],
  ["#FF4D8D", "#FF9D4D", "🔥"],
  ["#4DC3FF", "#7C4DFF", "🌙"],
  ["#66BB6A", "#4DC3FF", "🍃"],
  ["#FF9D4D", "#FFD54F", "⭐"],
  ["#EF5350", "#7C4DFF", "👹"],
  ["#7C4DFF", "#4DFFC3", "🐉"],
  ["#FF4D8D", "#B39DFF", "🌸"],
  ["#29B6F6", "#66BB6A", "🗡️"],
  ["#9B7BFF", "#FF4D8D", "😼"],
  ["#FFD54F", "#FF4D8D", "🍜"],
  ["#4DC3FF", "#FF4D8D", "🎧"],
];

export function Avatar({ id, size = 48, nick }: { id: number; size?: number; nick?: string | null }) {
  if (id === -1 && nick) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img
        src={`${GATEWAY}avatar-img?nick=${encodeURIComponent(nick)}`}
        alt=""
        width={size}
        height={size}
        className="rounded-full object-cover"
        style={{ width: size, height: size }}
      />
    );
  }
  const [c1, c2, emoji] = PRESETS[Math.max(0, Math.min(id, PRESETS.length - 1))];
  return (
    <div
      className="flex items-center justify-center rounded-full"
      style={{ width: size, height: size, background: `linear-gradient(135deg, ${c1}, ${c2})`, fontSize: size * 0.45 }}
    >
      {emoji}
    </div>
  );
}

export const AVATAR_COUNT = PRESETS.length;
