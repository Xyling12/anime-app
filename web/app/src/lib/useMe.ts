"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { me } from "./auth";
import { auth } from "./api";
import type { Me } from "./types";

/** Текущий аккаунт: /auth/me если есть токен, иначе null. Кэш через React Query. */
export function useMe() {
  const qc = useQueryClient();
  const q = useQuery<Me | null>({
    queryKey: ["me"],
    queryFn: async () => {
      if (!auth.token) return null;
      try {
        const m = await me();
        return m.nick ? m : null;
      } catch {
        return null;
      }
    },
    staleTime: 60_000,
  });
  return {
    me: q.data ?? null,
    loading: q.isLoading,
    refresh: () => qc.invalidateQueries({ queryKey: ["me"] }),
  };
}
