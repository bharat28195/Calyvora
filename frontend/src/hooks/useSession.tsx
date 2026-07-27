"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { setLocaleConfig } from "@/lib/format";
import type { Me } from "@/lib/types";

type Status = "loading" | "authenticated" | "unauthenticated";

interface SessionValue {
  me: Me | null;
  status: Status;
  setMe: (me: Me) => void;
  logout: () => Promise<void>;
  refetch: () => Promise<void>;
}

const SessionContext = createContext<SessionValue | null>(null);

/**
 * Holds the current user for the authenticated app shell. On mount it attempts a silent
 * refresh (real backend: the httpOnly refresh cookie; mock: the persisted session) so a page
 * reload keeps you logged in even though the access token lives only in memory.
 */
export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [me, setMeState] = useState<Me | null>(null);
  const [status, setStatus] = useState<Status>("loading");

  const load = useCallback(async () => {
    try {
      const result = await api.refresh();
      setMeState(result.me);
      setStatus("authenticated");
    } catch {
      setMeState(null);
      setStatus("unauthenticated");
    }
  }, []);

  // Fire the initial refresh exactly once. Refresh-token rotation treats a duplicate presentation
  // of the same cookie as reuse (theft) and revokes the family — so React StrictMode's double-invoke
  // in dev, or any concurrent refresh, would otherwise nuke a valid session.
  const bootstrapped = useRef(false);
  useEffect(() => {
    if (bootstrapped.current) return;
    bootstrapped.current = true;
    void load();
  }, [load]);

  // Keep the app-wide money/time formatters in sync with the company's chosen currency + timezone.
  useEffect(() => {
    if (me?.company) {
      setLocaleConfig({ currency: me.company.currency, timezone: me.company.timezone });
    }
  }, [me]);

  const value: SessionValue = {
    me,
    status,
    setMe: (m) => {
      setMeState(m);
      setStatus("authenticated");
    },
    logout: async () => {
      await api.logout();
      setMeState(null);
      setStatus("unauthenticated");
    },
    refetch: load,
  };

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error("useSession must be used within a SessionProvider");
  return ctx;
}

/** Redirects to /login once we know the visitor is unauthenticated. */
export function useRequireAuth() {
  const session = useSession();
  const router = useRouter();
  useEffect(() => {
    if (session.status === "unauthenticated") {
      router.replace("/login");
    }
  }, [session.status, router]);
  return session;
}
