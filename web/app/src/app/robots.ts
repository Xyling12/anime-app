import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        disallow: ["/api/", "/alapi/", "/auth/", "/dm/", "/chats/", "/notifications/"],
      },
      {
        userAgent: "Yandex",
        allow: "/",
        disallow: ["/api/", "/alapi/", "/auth/", "/dm/", "/chats/", "/notifications/"],
      },
      {
        userAgent: "Googlebot",
        allow: "/",
        disallow: ["/api/", "/alapi/", "/auth/", "/dm/", "/chats/", "/notifications/"],
      },
    ],
    sitemap: "https://anipulsetv.ru/sitemap.xml",
    host: "https://anipulsetv.ru",
  };
}
