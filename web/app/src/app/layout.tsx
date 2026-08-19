import type { Metadata, Viewport } from "next";
import "./globals.css";
import "./cinema.css";
import { Providers } from "@/components/Providers";
import { NavBar } from "@/components/NavBar";
import { Footer } from "@/components/Footer";

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
    default: "AniPulse — аниме, расписание серий и личная коллекция",
    template: "%s | AniPulse",
  },
  description: "Находите новые аниме, следите за выходом серий, сохраняйте тайтлы в коллекцию и продолжайте просмотр на сайте и в Android-приложении AniPulse.",
  openGraph: {
    title: "AniPulse — истории в вашем ритме",
    description: "Каталог аниме, расписание новых серий, личная коллекция и сообщество.",
    type: "website",
    locale: "ru_RU",
    siteName: "AniPulse",
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ru" data-theme="dark" className="h-full antialiased" suppressHydrationWarning>
      <body className="flex min-h-full flex-col bg-bg text-text">
        <Providers>
          <NavBar />
          {/* pb под нижнюю навигацию на мобилке с учётом safe area */}
          <main className="app-content relative z-10 flex-1">{children}</main>
          <Footer />
        </Providers>
      </body>
    </html>
  );
}
