"use client";

import { useEffect, useState } from "react";
import { Loader2, CreditCard, Users, CalendarClock, AlertTriangle } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { SubscriptionView } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";
import { money } from "@/lib/format";

/**
 * A company admin's read-only view of its subscription (billing is managed by the platform owner now —
 * PD-10 pt 6). Admins can request more seats, which lands in the owner's console (pt 8).
 */
export default function SubscriptionPage() {
  const [sub, setSub] = useState<SubscriptionView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [asking, setAsking] = useState(false);

  function load() {
    api.mySubscription().then(setSub).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load"));
  }
  useEffect(() => { load(); }, []);

  if (!sub) return <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;

  const expiring = sub.daysLeft != null && sub.daysLeft <= 14 && !sub.locked;

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-semibold tracking-tight">Subscription</h1>
      <p className="mt-1 text-fg/50">Your plan and seats. Billing is handled by your Priority HR account manager.</p>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {sub.locked && <Alert tone="error" className="mt-6">Your subscription has ended — contact your account manager to renew.</Alert>}
      {expiring && <Alert tone="warning" className="mt-6"><AlertTriangle className="h-4 w-4" /> Your subscription ends in {sub.daysLeft} day{sub.daysLeft === 1 ? "" : "s"}. Contact your account manager to renew.</Alert>}

      <div className="mt-6 grid gap-3 sm:grid-cols-3">
        <Stat icon={<CreditCard className="h-4 w-4 text-violet" />} label="Status" value={sub.locked ? "Ended" : sub.status.toLowerCase()} />
        <Stat icon={<Users className="h-4 w-4 text-aqua" />} label="Seats used" value={`${sub.seatsUsed} / ${sub.seats}`} />
        <Stat icon={<CalendarClock className="h-4 w-4 text-emerald-400" />} label="Renews / ends" value={sub.endsAt ?? "—"} />
      </div>

      <Card className="mt-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle>Plan</CardTitle>
            <p className="mt-1 text-sm text-fg/60">
              {sub.pricePerEmployee != null ? <>{money(sub.pricePerEmployee)} per employee / month</> : "Per employee / month"}
              {" · "}{sub.seats} seat{sub.seats === 1 ? "" : "s"}
            </p>
          </div>
          {/* The rate alone doesn't answer "what are we spending on this?" — an admin owns that
              number, so show the bill it produces, billed on people in use rather than seats held. */}
          {sub.monthlyCharge != null && (
            <div className="text-right">
              <p className="text-xs uppercase tracking-wide text-fg/40">This month</p>
              <p className="text-2xl font-semibold tabular-nums tracking-tight">{money(sub.monthlyCharge)}</p>
              <p className="text-xs text-fg/40">
                {sub.locked ? "Subscription ended" : `${sub.seatsUsed} employee${sub.seatsUsed === 1 ? "" : "s"} in use`}
              </p>
            </div>
          )}
        </div>

        {sub.pendingRequestSeats != null ? (
          <p className="mt-4 rounded-lg bg-amber-500/10 px-3 py-2 text-sm text-amber-300">
            Seat request pending: {sub.pendingRequestSeats} seats — awaiting your account manager.
          </p>
        ) : asking ? (
          <RequestSeatsForm current={sub.seats} onDone={() => { setAsking(false); load(); }} onCancel={() => setAsking(false)} />
        ) : (
          <Button className="mt-4" variant="secondary" onClick={() => setAsking(true)}>Request more seats</Button>
        )}
      </Card>
    </div>
  );
}

function RequestSeatsForm({ current, onDone, onCancel }: { current: number; onDone: () => void; onCancel: () => void }) {
  const [seats, setSeats] = useState(String(current + 5));
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try { await api.requestSeats(Number(seats) || current, note || undefined); onDone(); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Couldn't send the request"); setBusy(false); }
  }

  return (
    <form onSubmit={submit} className="mt-4 grid gap-3">
      {error && <Alert tone="error">{error}</Alert>}
      <Field label="New seat count" htmlFor="s-seats"><Input id="s-seats" type="number" min={current} value={seats} onChange={(e) => setSeats(e.target.value)} /></Field>
      <Field label="Note (optional)" htmlFor="s-note"><Input id="s-note" value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. hiring 5 more this quarter" /></Field>
      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Send request</Button>
        <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
      </div>
    </form>
  );
}

function Stat({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <Card className="py-4">
      <div className="flex items-center gap-2 text-xs text-fg/50">{icon} {label}</div>
      <p className="mt-1 text-lg font-semibold capitalize">{value}</p>
    </Card>
  );
}
