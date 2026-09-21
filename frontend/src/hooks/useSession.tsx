"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { api, setSessionLostHandler } from "@/lib/api";
import { setLocaleConfig } from "@/lib/format";
import type { Me } from "@/lib/types";

type Status = "loading" | "authenticated" | "unauthenticated";

interface SessionValue {
  me: Me | null;
  status: Status;
  /** Why the session ended, when it ended on its own. Carried to the login screen. */
  endedReason: "expired" | null;
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
  const [endedReason, setEndedReason] = useState<"expired" | null>(null);

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

  /**
   * The transport gives up on a session when a refresh fails, and until this was wired to it
   * nobody was listening: the call threw a 401, the screen showed an error, and the app went on
   * looking signed in around it. A session can end without anyone being idle — the window lapsed
   * in another tab, an admin disabled the account, the password was changed somewhere else — and
   * every one of those should land on the login screen rather than on a broken page.
   */
  useEffect(() => {
    setSessionLostHandler(() => {
      setMeState(null);
      setEndedReason("expired");
      setStatus("unauthenticated");
    });
    return () => setSessionLostHandler(null);
  }, []);

  // Keep the app-wide money/time formatters in sync with the company's currency and, for time, with
  // whichever zone this person is actually in — their own if set, else the company's. This is what
  // the attendance clock reads, and it has to match what the server stamps on a punch.
  useEffect(() => {
    if (me?.company) {
      setLocaleConfig({ currency: me.company.currency, timezone: me.timezone ?? me.company.timezone });
    }
  }, [me]);

  const value: SessionValue = {
    me,
    status,
    endedReason,
    setMe: (m) => {
      setMeState(m);
      setEndedReason(null);
      setStatus("authenticated");
    },
    logout: async () => {
      await api.logout();
      setMeState(null);
      // Signing out on purpose is not a session that ended on you; the login screen should say
      // nothing about it.
      setEndedReason(null);
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
      // Say why, when there is a why. Arriving at a login screen with no explanation reads as a
      // bug, or worse, as somebody else having taken the account.
      router.replace(session.endedReason ? `/login?reason=${session.endedReason}` : "/login");
    }
  }, [session.status, session.endedReason, router]);
  return session;
}
