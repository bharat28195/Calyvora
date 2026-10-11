"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ChevronRight, Loader2, PenLine } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { SignatureRequestView, SignatureStatus } from "@/lib/types";
import { Alert } from "@/components/ui/alert";
import { Card } from "@/components/ui/card";

const STATUS: Record<SignatureStatus, { label: string; tone: string }> = {
  SENT: { label: "To sign", tone: "bg-violet/15 text-violet" },
  VIEWED: { label: "To sign", tone: "bg-violet/15 text-violet" },
  SIGNED: { label: "Signed", tone: "bg-emerald-500/15 text-emerald-500" },
  DECLINED: { label: "Declined", tone: "bg-red-500/15 text-red-400" },
  WITHDRAWN: { label: "Withdrawn", tone: "bg-fg/10 text-fg/50" },
  EXPIRED: { label: "Expired", tone: "bg-fg/10 text-fg/50" },
};

/** Letters sent to me to accept with my signature, and the ones I have signed (PD-72). */
export default function MyLettersPage() {
  const [letters, setLetters] = useState<SignatureRequestView[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.myLetters().then(setLetters).catch((e) => setError(e instanceof ApiError ? e.message : "Could not load your letters."));
  }, []);

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">My letters</h1>
      <p className="mt-1 text-fg/50">Letters from HR for you to read and sign, and the ones you have signed.</p>
      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {letters === null && !error && <Loader2 className="mt-8 h-5 w-5 animate-spin text-violet" />}
      {letters && letters.length === 0 && (
        <Card className="mt-6"><p className="text-sm text-fg/50">Nothing here yet. When HR sends you a letter to sign, it appears here.</p></Card>
      )}
      {letters && letters.length > 0 && (
        <Card className="mt-6 p-0">
          <ul className="divide-y divide-fg/5">
            {letters.map((l) => (
              <li key={l.id}>
                <Link href={`/me/letters/${l.id}`} className="flex items-center justify-between gap-3 px-5 py-4 hover:bg-fg/[0.03]">
                  <span className="flex items-center gap-3">
                    <PenLine className="h-4 w-4 text-fg/40" />
                    <span>
                      <span className="block font-medium">{l.documentTitle}</span>
                      <span className="text-xs text-fg/45">
                        Sent {new Date(l.sentAt).toLocaleDateString("en-IN", { dateStyle: "medium" })}
                        {l.open && ` · sign by ${new Date(l.expiresAt).toLocaleDateString("en-IN", { dateStyle: "medium" })}`}
                      </span>
                    </span>
                  </span>
                  <span className="flex items-center gap-2">
                    <span className={`rounded px-2 py-0.5 text-xs font-medium ${STATUS[l.status].tone}`}>{STATUS[l.status].label}</span>
                    <ChevronRight className="h-4 w-4 text-fg/30" />
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
