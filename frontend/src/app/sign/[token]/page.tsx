"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { SignerView } from "@/lib/types";
import { Alert } from "@/components/ui/alert";
import { Wordmark } from "@/components/layout/wordmark";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { SignLetter } from "@/components/documents/sign-letter";

/**
 * A candidate signing their offer through the emailed link (PD-72) — no account, no login. The token
 * in the address is the only authority, and it works for this one letter.
 */
export default function PublicSignPage() {
  const { token } = useParams<{ token: string }>();
  const [view, setView] = useState<SignerView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.publicSign<SignerView>(token, "").then(setView)
      .catch((e) => setError(e instanceof ApiError ? e.message : "This link could not be opened."));
  }, [token]);

  return (
    <div className="min-h-screen px-4 py-8 sm:px-6">
      <div className="mx-auto mb-8 flex max-w-3xl items-center justify-between">
        <Wordmark />
        <ThemeToggle />
      </div>
      {error && <div className="mx-auto max-w-3xl"><Alert tone="error">{error}</Alert></div>}
      {!view && !error && <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>}
      {view && (
        <SignLetter
          view={view}
          onAccept={(input) => api.publicSign<SignerView>(token, "accept", input)}
          onDecline={(reason) => api.publicSign<SignerView>(token, "decline", { reason })}
          onDownload={() => { window.location.href = api.publicSignPdfUrl(token); }}
        />
      )}
      <p className="mx-auto mt-10 max-w-3xl text-center text-xs text-fg/35">Sent with Orbit by Calyvora</p>
    </div>
  );
}
