"use client";

import { useEffect, useState } from "react";
import { Loader2, Plus } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Regularization, RegularizationStatus } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";

export const REG_TONE: Record<RegularizationStatus, string> = {
  PENDING: "bg-amber-500/15 text-amber-300",
  APPROVED: "bg-emerald-500/15 text-emerald-400",
  REJECTED: "bg-red-500/15 text-red-400",
};

/** Employee widget: raise a "forgot to punch" regularization and see your requests. */
export function MyRegularizations() {
  const [items, setItems] = useState<Regularization[] | null>(null);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function load() {
    api.myRegularizations().then(setItems).catch((e) => { setItems([]); setError(e instanceof ApiError ? e.message : "Failed to load"); });
  }
  useEffect(() => { load(); }, []);

  return (
    <Card className="mt-4">
      <div className="flex items-center justify-between">
        <CardTitle>Regularizations</CardTitle>
        <Button size="sm" variant="secondary" onClick={() => setAdding((v) => !v)}><Plus className="h-4 w-4" /> Regularize a day</Button>
      </div>
      <p className="mt-1 text-sm text-fg/50">Forgot to clock in? Request a fix — it goes to your manager for approval.</p>

      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      {adding && <RaiseForm onDone={() => { setAdding(false); load(); }} onCancel={() => setAdding(false)} />}

      {items === null ? (
        <div className="mt-4"><Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" /></div>
      ) : items.length === 0 ? (
        <p className="mt-3 text-sm text-fg/40">No regularization requests.</p>
      ) : (
        <div className="mt-3 flex flex-col divide-y divide-fg/5">
          {items.map((r) => (
            <div key={r.id} className="flex items-center justify-between gap-3 py-2.5">
              <div className="min-w-0">
                <p className="text-sm font-medium">{r.date}{r.checkIn ? ` · ${r.checkIn.slice(0, 5)}` : ""}{r.checkOut ? `–${r.checkOut.slice(0, 5)}` : ""}</p>
                {(r.reason || r.decisionNote) && <p className="truncate text-xs text-fg/40">{r.decisionNote || r.reason}</p>}
              </div>
              <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${REG_TONE[r.status]}`}>{r.status.toLowerCase()}</span>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}

function RaiseForm({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const [date, setDate] = useState(() => new Date(Date.now() - 86400000).toISOString().slice(0, 10));
  const [checkIn, setCheckIn] = useState("09:30");
  const [checkOut, setCheckOut] = useState("18:00");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try { await api.raiseRegularization({ date, checkIn, checkOut, reason: reason || undefined }); onDone(); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Couldn't submit"); setBusy(false); }
  }

  return (
    <form onSubmit={submit} className="mt-3 grid gap-3 rounded-lg border border-fg/10 bg-fg/[0.02] p-3 sm:grid-cols-3">
      {error && <Alert tone="error" className="sm:col-span-3">{error}</Alert>}
      <Field label="Date" htmlFor="r-date"><Input id="r-date" type="date" value={date} max={new Date().toISOString().slice(0, 10)} onChange={(e) => setDate(e.target.value)} /></Field>
      <Field label="Check in" htmlFor="r-in"><Input id="r-in" type="time" value={checkIn} onChange={(e) => setCheckIn(e.target.value)} /></Field>
      <Field label="Check out" htmlFor="r-out"><Input id="r-out" type="time" value={checkOut} onChange={(e) => setCheckOut(e.target.value)} /></Field>
      <div className="sm:col-span-3"><Field label="Reason" htmlFor="r-reason"><Input id="r-reason" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. forgot to check in — was in office" /></Field></div>
      <div className="flex gap-2 sm:col-span-3">
        <Button type="submit" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Submit request</Button>
        <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
      </div>
    </form>
  );
}
