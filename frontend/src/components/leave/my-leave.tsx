"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, CalendarPlus } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { LeaveBalance, LeaveRequest, LeaveTypeBalance } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";

const TYPES = ["VACATION", "SICK", "PERSONAL", "UNPAID", "COMP_OFF"] as const;
const LABELS: Record<string, string> = {
  VACATION: "Vacation",
  SICK: "Sick",
  PERSONAL: "Personal",
  UNPAID: "Unpaid",
  COMP_OFF: "Comp-off",
};
const selectCls =
  "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

/**
 * Your own time off: balance, the request form, and your history. Shared by People → Time off
 * (which adds the approvals inbox for admins) and the Me hub.
 */
export function MyLeave({ onChanged }: { onChanged?: () => void }) {
  const [balance, setBalance] = useState<LeaveBalance | null>(null);
  const [balances, setBalances] = useState<LeaveTypeBalance[]>([]);
  const [mine, setMine] = useState<LeaveRequest[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [bal, all, my] = await Promise.all([
        api.leaveBalance(),
        api.leaveBalances(),
        api.myLeave(),
      ]);
      setBalance(bal);
      setBalances(all);
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

      <TypeBalances balances={balances} />

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

/**
 * Every leave type, with the working shown.
 *
 * <p>The four stats above answer "how much vacation have I got". This answers the question they
 * cannot: out of what, earned when, and how much came from last year. A single number invites an
 * argument with HR; the breakdown ends it.
 *
 * <p>Types the company has not switched on (entitlement zero, nothing taken, nothing carried) are
 * hidden. Listing "Sick: 0 of 0" for every company that never configured sick leave is noise that
 * makes the rows that matter harder to find.
 */
function TypeBalances({ balances }: { balances: LeaveTypeBalance[] }) {
  const shown = balances.filter(
    (b) =>
      b.entitlementPerYear > 0 ||
      b.availableDays > 0 ||
      b.usedDays > 0 ||
      b.pendingDays > 0 ||
      b.carriedForward > 0,
  );
  if (shown.length === 0) return null;

  return (
    <div className="mt-6">
      <h2 className="text-sm font-medium uppercase tracking-wide text-fg/40">Your balances</h2>
      <div className="mt-3 overflow-x-auto">
        <table className="w-full min-w-[34rem] text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wide text-fg/40">
              <th className="pb-2 pr-4 font-medium">Type</th>
              <th className="pb-2 pr-4 font-medium">Earned</th>
              <th className="pb-2 pr-4 font-medium">Carried</th>
              <th className="pb-2 pr-4 font-medium">Used</th>
              <th className="pb-2 pr-4 font-medium">Pending</th>
              <th className="pb-2 font-medium">Available</th>
            </tr>
          </thead>
          <tbody className="tabular-nums">
            {shown.map((b) => (
              <tr key={b.type} className="border-t border-fg/10">
                <td className="py-2 pr-4">
                  <span className="font-medium">{LABELS[b.type] ?? b.type}</span>
                  {/* Why the earned number is what it is — a monthly policy is the usual reason
                      somebody thinks their balance is wrong in March. */}
                  {b.type !== "COMP_OFF" && (
                    <span className="ml-2 text-xs text-fg/40">
                      {b.accrual === "MONTHLY"
                        ? `${b.entitlementPerYear}/yr, monthly`
                        : `${b.entitlementPerYear}/yr`}
                    </span>
                  )}
                  {!b.paid && <span className="ml-2 text-xs text-fg/40">unpaid</span>}
                </td>
                <td className="py-2 pr-4 text-fg/70">
                  {b.type === "COMP_OFF" ? "—" : `${b.earnedThisYear}d`}
                </td>
                <td className="py-2 pr-4 text-fg/70">
                  {b.type === "COMP_OFF" ? `${b.carriedForward}d earned` : `${b.carriedForward}d`}
                </td>
                <td className="py-2 pr-4 text-fg/70">{b.usedDays}d</td>
                <td className="py-2 pr-4 text-fg/70">{b.pendingDays}d</td>
                <td className="py-2 font-medium">{b.availableDays}d</td>
              </tr>
            ))}
          </tbody>
        </table>
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
            {/* Labelled rather than lower-cased: "comp_off" reads as a database column. */}
            {TYPES.map((t) => <option key={t} value={t} className="bg-surface">{LABELS[t] ?? t}</option>)}
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
