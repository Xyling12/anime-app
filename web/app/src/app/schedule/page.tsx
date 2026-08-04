import { ScheduleClient } from "./ScheduleClient";

export const metadata = {
  title: "Расписание аниме — Эфир — AniPulse",
  description: "Расписание выхода новых серий аниме по дням и свежие обновления озвучек.",
};

export default function SchedulePage() {
  return <ScheduleClient />;
}
