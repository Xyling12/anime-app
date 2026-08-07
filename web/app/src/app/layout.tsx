import type { Metadata } from "next";
import "./globals.css";
import "./cinema.css";
import { Providers } from "@/components/Providers";
import { NavBar } from "@/components/NavBar";
import { Footer } from "@/components/Footer";

export const metadata: Metadata = {
  metadataBase: new URL("https://anipulsetv.ru"),
  title: {
    default: "AniPulse — аниме, расписание серий и личная коллекция",
    template: "%s | AniPulse",
  },
  description: "Находите новые аниме, следите за выходом серий, сохраняйте тайтлы в коллекцию и продолжайте просмотр на сайте и в Android-приложении AniPulse.",
  openGraph: {
    title: "AniPulse — истории в вашем ритме",
    description: "Каталог аниме, расписание новых серий и личная коллекция.",
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
          {/* pb под нижнюю навигацию на мобилке */}
          <main className="app-content relative z-10 flex-1 pb-20 md:pb-0">{children}</main>
          <Footer />
        </Providers>
      </body>
    </html>
  );
}
