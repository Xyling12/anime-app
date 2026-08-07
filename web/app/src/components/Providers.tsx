"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { ThemeProvider } from "./ThemeProvider";
import { syncAll } from "@/lib/sync";
import { startAnalytics } from "@/lib/analytics";

/** Клиентские провайдеры: React Query (кэш/поллинг) + тема. */
export function Providers({ children }: { children: React.ReactNode }) {
  const [qc] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 60_000, retry: 2, refetchOnWindowFocus: false },
        },
      }),
  );
  useEffect(() => { void syncAll().catch(() => {}); }, []);
  useEffect(() => startAnalytics(), []);
  return (
    <QueryClientProvider client={qc}>
      <ThemeProvider>{children}</ThemeProvider>
    </QueryClientProvider>
  );
}
