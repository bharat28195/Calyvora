"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { SignerView } from "@/lib/types";
import { Alert } from "@/components/ui/alert";
import { SignLetter } from "@/components/documents/sign-letter";

/** One letter sent to me: read it as it prints, then sign or decline (PD-72). */
export default function MyLetterPage() {
  const { id } = useParams<{ id: string }>();
  const [view, setView] = useState<SignerView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.myLetter(id).then(setView).catch((e) => setError(e instanceof ApiError ? e.message : "Could not open the letter."));
  }, [id]);

  async function download() {
    try {
      const blob = await api.myLetterPdf(id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${view?.documentTitle ?? "Letter"} (signed).pdf`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not download the copy.");
    }
  }

  return (
    <div>
      <Link href="/me/letters" className="mb-4 inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg">
        <ArrowLeft className="h-4 w-4" /> My letters
      </Link>
      {error && <Alert tone="error">{error}</Alert>}
      {!view && !error && <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>}
      {view && (
        <SignLetter
          view={view}
          onAccept={(input) => api.acceptMyLetter(id, input)}
          onDecline={(reason) => api.declineMyLetter(id, reason)}
          onDownload={() => void download()}
        />
      )}
    </div>
  );
}
