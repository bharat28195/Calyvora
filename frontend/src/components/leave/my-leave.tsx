"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, CalendarPlus } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { LeaveBalance, LeaveRequest } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";

const TYPES = ["VACATION", "SICK", "PERSONAL", "UNPAID"] as const;
const selectCls =
  "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

/**
 * Your own time off: balance, the request form, and your history. Shared by People → Time off
 * (which adds the approvals inbox for admins) and the Me hub.
 */
export function MyLeave({ onChanged }: { onChanged?: () => void }) {
  const [balance, setBalance] = useState<LeaveBalance | null>(null);
  const [mine, setMine] = useState<LeaveRequest[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [bal, my] = await Promise.all([api.leaveBalance(), api.myLeave()]);
      setBalance(bal);
      setMine(my);
      onChanged?.();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load time off");
    }
  }, [onChanged]);

  useEffect(() => {
    void load();
  }, [load]);

  async function cancel(id: string) {
    await api.cancelLeave(id);
    void load();
  }

  return (
    <div>
      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      <div className="mt-8 grid gap-5 sm:grid-cols-4">
        <Stat label="Vacation allowance" value={balance ? `${balance.allowanceDays}d` : null} />
        <Stat label="Used" value={balance ? `${balance.usedDays}d` : null} />
        <Stat label="Pending" value={balance ? `${balance.pendingDays}d` : null} />
        <Stat label="Remaining" value={balance ? `${balance.remainingDays}d` : null} highlight />
      </div>

      <div className="mt-8 grid gap-8 lg:grid-cols-2">
        <RequestForm onSubmitted={load} onError={setError} />

        <div>
          <h2 className="text-sm font-medium uppercase tracking-wide text-fg/40">My requests</h2>
          <div className="mt-3 flex flex-col gap-2">
            {mine === null ? (
              <Card><Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" /></Card>
            ) : mine.length === 0 ? (
              <Card className="text-sm text-fg/50">No requests yet.</Card>
            ) : (
              mine.map((r) => (
                <Card key={r.id} className="flex items-center justify-between p-4">
                  <div>
                    <p className="text-sm">
                      <span className="capitalize">{r.type.toLowerCase()}</span> · {r.days}d
                    </p>
                    <p className="text-xs text-fg/50">{r.startDate} → {r.endDate}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <Badge value={r.status} />
                    {r.status === "PENDING" && (
                      <button onClick={() => cancel(r.id)} className="text-xs text-fg/40 hover:text-fg">
                        Cancel
                      </button>
                    )}
                  </div>
                </Card>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function Stat({ label, value, highlight }: { label: string; value: string | null; highlight?: boolean }) {
  return (
    <Card>
      <p className="text-sm text-fg/50">{label}</p>
      {value === null ? (
        <div className="mt-2 h-7 w-16 animate-pulse rounded bg-fg/10" />
      ) : (
        <p className={`mt-1 text-2xl font-semibold ${highlight ? "text-violet" : ""}`}>{value}</p>
      )}
    </Card>
  );
}

function RequestForm({ onSubmitted, onError }: { onSubmitted: () => void; onError: (m: string) => void }) {
  const [type, setType] = useState<(typeof TYPES)[number]>("VACATION");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!startDate || !endDate) return;
    setBusy(true);
    try {
      await api.requestLeave({ type, startDate, endDate, reason });
      setStartDate("");
      setEndDate("");
      setReason("");
      onSubmitted();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Failed to submit request");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardTitle>Request time off</CardTitle>
      <p className="mt-1 text-xs text-fg/40">Your manager gets it in their inbox for approval.</p>
      <form onSubmit={submit} className="mt-4 flex flex-col gap-4">
        <Field label="Type" htmlFor="type">
          <select id="type" className={selectCls} value={type} onChange={(e) => setType(e.target.value as (typeof TYPES)[number])}>
            {TYPES.map((t) => <option key={t} value={t} className="bg-surface">{t.toLowerCase()}</option>)}
          </select>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Start" htmlFor="start">
            <Input id="start" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
          </Field>
          <Field label="End" htmlFor="end">
            <Input id="end" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} required />
          </Field>
        </div>
        <Field label="Reason (optional)" htmlFor="reason">
          <Input id="reason" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. family holiday" />
        </Field>
        <Button type="submit" disabled={busy} className="self-start">
          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <CalendarPlus className="h-4 w-4" />} Submit request
        </Button>
      </form>
    </Card>
  );
}
