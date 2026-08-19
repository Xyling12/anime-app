import { ScheduleClient } from "./ScheduleClient";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Расписание выхода серий аниме — График онгоингов по дням недели",
  description:
    "Актуальное расписание выхода новых серий аниме по дням недели по московскому времени (МСК). Узнайте, когда выйдет следующая серия вашего любимого онгоинга в русской озвучке.",
  keywords: [
    "расписание аниме",
    "график выхода серий",
    "онгоинги расписание",
    "когда выйдет серия",
    "новые серии аниме",
    "аниме по дням недели",
  ],
  alternates: {
    canonical: "https://anipulsetv.ru/schedule",
  },
  openGraph: {
    title: "Расписание выхода серий аниме — AniPulse",
    description: "График выхода новых эпизодов аниме по дням недели (МСК).",
    url: "https://anipulsetv.ru/schedule",
  },
};

export default function SchedulePage() {
  return <ScheduleClient />;
}
