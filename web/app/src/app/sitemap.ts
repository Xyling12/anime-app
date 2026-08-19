import type { MetadataRoute } from "next";
import { catalog } from "@/lib/catalog";

export const revalidate = 86400; // 24 hours

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const baseUrl = "https://anipulsetv.ru";

  const staticPages: MetadataRoute.Sitemap = [
    {
      url: `${baseUrl}`,
      lastModified: new Date(),
      changeFrequency: "daily",
      priority: 1.0,
    },
    {
      url: `${baseUrl}/catalog`,
      lastModified: new Date(),
      changeFrequency: "daily",
      priority: 0.9,
    },
    {
      url: `${baseUrl}/schedule`,
      lastModified: new Date(),
      changeFrequency: "daily",
      priority: 0.9,
    },
    {
      url: `${baseUrl}/rightholders`,
      lastModified: new Date(),
      changeFrequency: "monthly",
      priority: 0.3,
    },
    {
      url: `${baseUrl}/privacy`,
      lastModified: new Date(),
      changeFrequency: "monthly",
      priority: 0.3,
    },
  ];

  try {
    // Fetch popular & ongoing anime to include in sitemap
    const [popular, ongoing, ranked] = await Promise.all([
      catalog({ page: 1, order: "popularity" }).catch(() => []),
      catalog({ page: 1, status: "ongoing" }).catch(() => []),
      catalog({ page: 1, order: "ranked" }).catch(() => []),
    ]);

    const seen = new Set<number>();
    const allAnime = [...popular, ...ongoing, ...ranked].filter((a) => {
      if (seen.has(a.id)) return false;
      seen.add(a.id);
      return true;
    });

    const animePages: MetadataRoute.Sitemap = allAnime.map((anime) => ({
      url: `${baseUrl}/anime/${anime.id}`,
      lastModified: new Date(),
      changeFrequency: anime.status === "ongoing" ? "daily" : "weekly",
      priority: 0.8,
    }));

    return [...staticPages, ...animePages];
  } catch {
    return staticPages;
  }
}
