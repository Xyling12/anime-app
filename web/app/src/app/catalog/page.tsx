import { Suspense } from "react";
import { CatalogClient } from "./CatalogClient";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Каталог аниме — Смотреть аниме онлайн с русской озвучкой бесплатно",
  description:
    "Полный каталог японского аниме и донхуа. Удобный поиск по названию, жанрам, годам и статусу выхода. Смотрите онгоинги и завершённые аниме в русской озвучке AniLibria, AniDUB, Studio Band.",
  keywords: [
    "каталог аниме",
    "аниме онлайн",
    "онгоинги аниме",
    "популярное аниме",
    "топ аниме",
    "аниме 2024",
    "аниме 2025",
    "аниме 2026",
    "русская озвучка",
  ],
  alternates: {
    canonical: "https://anipulsetv.ru/catalog",
  },
  openGraph: {
    title: "Каталог аниме — Смотреть онлайн на AniPulse",
    description: "Поиск и фильтры по популярности, рейтингу, онгоингам и жанрам.",
    url: "https://anipulsetv.ru/catalog",
  },
};

export default function CatalogPage() {
  return (
    <Suspense fallback={<div className="mx-auto max-w-[1500px] px-8 py-8" />}>
      <CatalogClient />
    </Suspense>
  );
}
