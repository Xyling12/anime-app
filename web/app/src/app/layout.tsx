import type { Metadata, Viewport } from "next";
import "./globals.css";
import "./cinema.css";
import { Providers } from "@/components/Providers";
import { NavBar } from "@/components/NavBar";
import { Footer } from "@/components/Footer";
import { Metrika } from "@/components/Metrika";

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  viewportFit: "cover",
  themeColor: "#08080d",
};

export const metadata: Metadata = {
  metadataBase: new URL("https://anipulsetv.ru"),
  title: {
    default: "AniPulse — Смотреть аниме онлайн бесплатно в хорошем качестве с русской озвучкой",
    template: "%s | AniPulse",
  },
  description:
    "Смотрите аниме онлайн бесплатно в высоком качестве HD 1080p/720p с русской озвучкой (AniLibria, AniDUB, Studio Band). Каталог новинок и классики, актуальное расписание серий, личная коллекция и синхронизация прогресса на сайте и в Android-приложении AniPulse.",
  keywords: [
    "аниме онлайн",
    "смотреть аниме",
    "аниме бесплатно",
    "русская озвучка",
    "аниме в хорошем качестве",
    "AniLibria",
    "AniDUB",
    "расписание аниме",
    "каталог аниме",
    "новинки аниме",
    "AniPulse",
  ],
  alternates: {
    canonical: "https://anipulsetv.ru",
  },
  verification: {
    google: "Px3BESxJup4RWqYxfvYE8rcE51MmUURrrHsVQQ_XNJw",
  },
  openGraph: {
    title: "AniPulse — Смотреть аниме онлайн бесплатно в хорошем качестве",
    description:
      "Огромный каталог аниме, расписание новых серий, русская озвучка AniLibria и личная коллекция.",
    type: "website",
    locale: "ru_RU",
    siteName: "AniPulse",
    images: [
      {
        url: "/brand/anipulse-banner.png",
        width: 1200,
        height: 630,
        alt: "AniPulse — Онлайн-кинотеатр аниме",
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    title: "AniPulse — Смотреть аниме онлайн бесплатно",
    description:
      "Огромный каталог аниме, расписание новых серий, русская озвучка AniLibria и личная коллекция.",
    images: ["/brand/anipulse-banner.png"],
  },
  icons: {
    icon: "/brand/anipulse-icon.png",
    apple: "/brand/anipulse-icon.png",
  },
};

const siteJsonLd = {
  "@context": "https://schema.org",
  "@graph": [
    {
      "@type": "WebSite",
      "@id": "https://anipulsetv.ru/#website",
      url: "https://anipulsetv.ru",
      name: "AniPulse",
      description: "Онлайн-кинотеатр аниме с русской озвучкой и расписанием серий",
      publisher: {
        "@id": "https://anipulsetv.ru/#organization",
      },
      potentialAction: {
        "@type": "SearchAction",
        target: {
          "@type": "EntryPoint",
          urlTemplate: "https://anipulsetv.ru/catalog?q={search_term_string}",
        },
        "query-input": "required name=search_term_string",
      },
      inLanguage: "ru-RU",
    },
    {
      "@type": "Organization",
      "@id": "https://anipulsetv.ru/#organization",
      name: "AniPulse",
      url: "https://anipulsetv.ru",
      logo: {
        "@type": "ImageObject",
        url: "https://anipulsetv.ru/brand/anipulse-icon.png",
      },
    },
  ],
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ru" data-theme="dark" className="h-full antialiased" suppressHydrationWarning>
      <head>
        <meta name="google-site-verification" content="Px3BESxJup4RWqYxfvYE8rcE51MmUURrrHsVQQ_XNJw" />
        <link rel="preconnect" href="https://anipulsetv.ru" />
        <link rel="dns-prefetch" href="https://shikimori.io" />
        <link rel="dns-prefetch" href="https://kodik.info" />
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(siteJsonLd) }}
        />
      </head>
      <body className="flex min-h-full flex-col bg-bg text-text">
        <Providers>
          <Metrika />
          <NavBar />
          {/* pb под нижнюю навигацию на мобилке с учётом safe area */}
          <main className="app-content relative z-10 flex-1">{children}</main>
          <Footer />
        </Providers>
      </body>
    </html>
  );
}
