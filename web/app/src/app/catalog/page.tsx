import { Suspense } from "react";
import { CatalogClient } from "./CatalogClient";

export const metadata = {
  title: "Каталог аниме — AniPulse",
  description: "Полный каталог аниме с русской озвучкой: поиск, фильтры по популярности и рейтингу, онгоинги.",
};

export default function CatalogPage() {
  return (
    <Suspense fallback={<div className="mx-auto max-w-[1500px] px-8 py-8" />}>
      <CatalogClient />
    </Suspense>
  );
}
