"use client";

import { useCallback, useEffect, useState } from "react";
import { BellRing, CheckCircle2, Clock, Eye, Loader2, PenLine, Undo2, XCircle } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { GeneratedDoc, SignatureRequestView, SignatureStatus } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Modal } from "@/components/ui/modal";
import { Input } from "@/components/ui/input";

/**
 * Sending an issued letter to be accepted with a signature, and following it up (PD-72).
 *
 * <p>An employee signs inside Orbit; anyone else (a candidate) gets a link by email that works for
 * this one letter. HR sees where it stands — sent, opened, signed or declined — and can remind or
 * withdraw while it is open.
 */
const LABEL: Record<SignatureStatus, string> = {
  SENT: "Waiting — not opened yet",
  VIEWED: "Opened, not signed yet",
  SIGNED: "Signed",
  DECLINED: "Declined",
  WITHDRAWN: "Withdrawn",
  EXPIRED: "Expired",
};

function when(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : "—";
}

export function SignaturePanel({ doc, onSigned }: { doc: GeneratedDoc; onSigned?: () => void }) {
  const [requests, setRequests] = useState<SignatureRequestView[] | null>(null);
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [days, setDays] = useState(14);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const r = await api.signatureRequests(doc.id);
      setRequests(r);
      if (r[0]?.status === "SIGNED") onSigned?.();
    } catch {
      setRequests([]);
    }
  }, [doc.id, onSigned]);
  useEffect(() => { void load(); }, [load]);

  async function act(work: () => Promise<unknown>, done: string) {
    setBusy(true);
    setError(null);
    setNote(null);
    try {
      await work();
      setNote(done);
      await load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "That did not work.");
    } finally {
      setBusy(false);
    }
  }

  const latest = requests?.[0];
  const signed = latest?.status === "SIGNED";
  const forEmployee = !!doc.employeeId;

  return (
    <Card className="mt-6 print:hidden">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <CardTitle>Signature</CardTitle>
          <p className="mt-0.5 text-xs text-fg/50">
            {forEmployee
              ? "They read it in Orbit and accept with their signature."
              : "They get a link by email — no account needed — to read and sign this letter."}
          </p>
        </div>
        {!signed && (
          <Button size="sm" disabled={busy} onClick={() => { setError(null); setOpen(true); }}>
            <PenLine className="h-4 w-4" /> {latest?.open ? "Send again" : "Send for signature"}
          </Button>
        )}
      </div>

      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      {note && <Alert tone="success" className="mt-3">{note}</Alert>}

      {requests === null ? (
        <Loader2 className="mt-3 h-4 w-4 animate-spin text-violet" />
      ) : requests.length === 0 ? (
        <p className="mt-3 text-sm text-fg/50">Not sent for signature yet.</p>
      ) : (
        <ul className="mt-3 divide-y divide-fg/5 text-sm">
          {requests.map((r, i) => (
            <li key={r.id} className={`py-2.5 ${i > 0 ? "opacity-60" : ""}`}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="flex items-center gap-2">
                  {r.status === "SIGNED" ? <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                    : r.status === "DECLINED" ? <XCircle className="h-4 w-4 text-red-500" />
                    : r.status === "VIEWED" ? <Eye className="h-4 w-4 text-sky-500" />
                    : <Clock className="h-4 w-4 text-amber-500" />}
                  <span className="font-medium">{LABEL[r.status]}</span>
                  <span className="text-fg/50">· {r.signerName}{r.signerEmail ? ` <${r.signerEmail}>` : ""}</span>
                </span>
                {i === 0 && r.open && (
                  <span className="flex gap-1">
                    <Button size="sm" variant="ghost" disabled={busy}
                      onClick={() => void act(() => api.remindSignature(r.id), "Reminder sent.")}>
                      <BellRing className="h-3.5 w-3.5" /> Remind
                    </Button>
                    <Button size="sm" variant="ghost" disabled={busy}
                      onClick={() => void act(() => api.withdrawSignature(r.id), "Withdrawn — the link no longer works.")}>
                      <Undo2 className="h-3.5 w-3.5" /> Withdraw
                    </Button>
                  </span>
                )}
              </div>
              <p className="mt-1 text-xs text-fg/45">
                Sent {when(r.sentAt)}
                {r.viewedAt && ` · opened ${when(r.viewedAt)}`}
                {r.decidedAt && r.status === "SIGNED" && ` · signed ${when(r.decidedAt)} as “${r.typedName}”`}
                {r.decidedAt && r.status === "DECLINED" && ` · declined ${when(r.decidedAt)}`}
                {r.open && ` · open until ${new Date(r.expiresAt).toLocaleDateString("en-IN", { dateStyle: "medium" })}`}
                {r.reminders > 0 && ` · reminded ${r.reminders}×`}
              </p>
              {r.declineReason && <p className="mt-1 rounded bg-red-500/5 px-2 py-1 text-xs text-red-400">“{r.declineReason}”</p>}
            </li>
          ))}
        </ul>
      )}
      {signed && (
        <p className="mt-2 text-xs text-fg/50">
          Download PDF now gives the signed copy, with their signature and a page recording how it was signed.
        </p>
      )}

      <Modal open={open} onClose={() => setOpen(false)} title="Send for signature">
        <div className="flex flex-col gap-3">
          {!forEmployee && (
            <label className="flex flex-col gap-1">
              <span className="text-xs font-medium text-fg/60">Their email</span>
              <Input type="email" value={email} placeholder="name@example.com" onChange={(e) => setEmail(e.target.value)} />
            </label>
          )}
          {forEmployee && (
            <p className="text-sm text-fg/60">
              {doc.employeeName ?? "They"} will get an email and a notification, and can sign under <b>Me → My letters</b>.
            </p>
          )}
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-fg/60">Message (optional)</span>
            <textarea rows={3} value={message} onChange={(e) => setMessage(e.target.value)}
              placeholder="e.g. Welcome aboard! Please sign by Friday."
              className="w-full rounded-lg border border-fg/15 bg-fg/5 p-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet" />
          </label>
          <label className="flex items-center gap-2 text-sm text-fg/70">
            Open for
            <select value={days} onChange={(e) => setDays(Number(e.target.value))}
              className="rounded-md border border-fg/15 bg-surface px-2 py-1 text-sm">
              {[3, 7, 14, 30].map((d) => <option key={d} value={d}>{d} days</option>)}
            </select>
          </label>
          {latest?.open && <p className="text-xs text-amber-500">Sending again replaces the open request; its link stops working.</p>}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            <Button disabled={busy || (!forEmployee && !email.trim())}
              onClick={() => void act(async () => {
                await api.sendForSignature(doc.id, { email: email.trim() || undefined, message: message.trim() || undefined, validDays: days });
                setOpen(false);
              }, forEmployee ? "Sent. They have been emailed and notified." : "Sent. They have been emailed a link to sign.")}>
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <PenLine className="h-4 w-4" />} Send
            </Button>
          </div>
        </div>
      </Modal>
    </Card>
  );
}
