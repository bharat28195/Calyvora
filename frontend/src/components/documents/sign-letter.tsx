"use client";

import { useState } from "react";
import { CheckCircle2, Download, Loader2, PenLine, XCircle } from "lucide-react";
import { ApiError } from "@/lib/api";
import type { SignerView } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";
import { SignaturePad } from "@/components/documents/signature-pad";

/**
 * Reading a letter and accepting it with a signature, or declining it (PD-72). Shared by an
 * employee's My letters and a candidate's link, which differ only in how they reach the server.
 *
 * <p>The letter is shown as its printed pages — images of the very PDF that will be signed — so what
 * someone agrees to is exactly what prints, on any phone, with nothing to install.
 */
export function SignLetter({
  view,
  onAccept,
  onDecline,
  onDownload,
}: {
  view: SignerView;
  onAccept: (input: { typedName: string; signature: string; agree: boolean }) => Promise<SignerView>;
  onDecline: (reason: string) => Promise<SignerView>;
  onDownload: () => void;
}) {
  const [state, setState] = useState(view);
  const [typedName, setTypedName] = useState(view.signerName);
  const [signature, setSignature] = useState<string | null>(null);
  const [agree, setAgree] = useState(false);
  const [declining, setDeclining] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const open = state.status === "SENT" || state.status === "VIEWED";

  async function run(work: () => Promise<SignerView>) {
    setBusy(true);
    setError(null);
    try {
      setState(await work());
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "That did not go through. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-4">
        <p className="text-xs font-medium uppercase tracking-wide text-fg/40">{state.companyName}</p>
        <h1 className="text-2xl font-semibold tracking-tight">{state.documentTitle}</h1>
        {state.message && open && (
          <p className="mt-3 whitespace-pre-line rounded-lg border border-fg/10 bg-fg/[0.03] p-3 text-sm text-fg/80">{state.message}</p>
        )}
      </div>

      {state.status === "SIGNED" && (
        <Alert tone="success" className="mb-4">
          <span className="flex flex-wrap items-center gap-2">
            <CheckCircle2 className="h-4 w-4" /> You signed this letter
            {state.decidedAt && ` on ${new Date(state.decidedAt).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" })}`}.
            <button type="button" onClick={onDownload} className="inline-flex items-center gap-1 font-medium underline">
              <Download className="h-3.5 w-3.5" /> Download your signed copy
            </button>
          </span>
        </Alert>
      )}
      {state.status === "DECLINED" && (
        <Alert tone="error" className="mb-4"><span className="flex items-center gap-2"><XCircle className="h-4 w-4" /> You declined this letter. {state.companyName} has been told.</span></Alert>
      )}
      {state.status === "EXPIRED" && <Alert tone="error" className="mb-4">This request has expired. Ask {state.companyName} to send it again.</Alert>}
      {state.status === "WITHDRAWN" && <Alert tone="error" className="mb-4">This letter was withdrawn. {state.companyName} will send you the current one.</Alert>}

      {/* The letter, as it prints. */}
      <div className="flex flex-col gap-4">
        {state.pages.map((src, i) => (
          // eslint-disable-next-line @next/next/no-img-element
          <img key={i} src={src} alt={`Page ${i + 1} of ${state.pages.length}`}
            className="w-full rounded-md bg-white shadow-md ring-1 ring-black/10" />
        ))}
      </div>

      {open && (
        <div className="mt-6 rounded-xl border border-fg/10 bg-surface p-5">
          {error && <Alert tone="error" className="mb-3">{error}</Alert>}
          {!declining ? (
            <>
              <h2 className="text-lg font-semibold">Accept and sign</h2>
              <label className="mt-3 flex flex-col gap-1">
                <span className="text-xs font-medium text-fg/60">Your full name</span>
                <input value={typedName} onChange={(e) => setTypedName(e.target.value)}
                  className="rounded-md border border-fg/15 bg-fg/[0.03] px-3 py-2 text-sm" />
              </label>
              <div className="mt-3">
                <span className="mb-1 block text-xs font-medium text-fg/60">Your signature</span>
                <SignaturePad onChange={setSignature} defaultName={typedName} />
              </div>
              <label className="mt-3 flex items-start gap-2 text-sm text-fg/80">
                <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} className="mt-0.5 h-4 w-4 accent-violet" />
                I have read this letter and I accept it. I agree that this electronic signature is my signature.
              </label>
              <div className="mt-4 flex flex-wrap items-center gap-2">
                <Button disabled={busy || !agree || !signature || typedName.trim().length < 2}
                  onClick={() => void run(() => onAccept({ typedName: typedName.trim(), signature: signature!, agree }))}>
                  {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <PenLine className="h-4 w-4" />} Accept and sign
                </Button>
                <Button variant="ghost" disabled={busy} onClick={() => setDeclining(true)}>Decline</Button>
              </div>
              <p className="mt-3 text-xs text-fg/45">
                We record your name, the time, your IP address and browser with your signature, and a fingerprint of
                this letter, so there is a clear record of what was signed.
              </p>
            </>
          ) : (
            <>
              <h2 className="text-lg font-semibold">Decline this letter</h2>
              <p className="mt-1 text-sm text-fg/60">Tell {state.companyName} what is not right, so they can follow up.</p>
              <textarea rows={4} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500}
                className="mt-3 w-full rounded-lg border border-fg/15 bg-fg/5 p-3 text-sm" />
              <div className="mt-3 flex gap-2">
                <Button variant="secondary" disabled={busy || !reason.trim()} onClick={() => void run(() => onDecline(reason.trim()))}>
                  {busy && <Loader2 className="h-4 w-4 animate-spin" />} Decline
                </Button>
                <Button variant="ghost" disabled={busy} onClick={() => setDeclining(false)}>Back</Button>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
