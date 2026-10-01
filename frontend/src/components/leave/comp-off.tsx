"use client";

import { useCallback, useEffect, useState } from "react";
import { Check, Loader2, X } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { CompOffCredit } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";

/**
 * Comp-off: claim a day you worked that you were not owed, and spend it later as leave.
 *
 * <p>Kept as its own component rather than folded into the leave form because it is a different
 * transaction: leave spends an entitlement, comp-off *creates* one. Putting a "day worked" field on
 * a "days off" form is how people end up booking a holiday when they meant to bank one.
 */
export function MyCompOff({ onChanged }: { onChanged?: () => void }) {
  const [mine, setMine] = useState<CompOffCredit[] | null>(null);
  const [workedOn, setWorkedOn] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setMine(await api.myCompOff());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load comp-off");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!workedOn) return;
    setBusy(true);
    setError(null);
    try {
      await api.claimCompOff({ workedOn, reason });
      setWorkedOn("");
      setReason("");
      await load();
      onChanged?.();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to claim comp-off");
    } finally {
      setBusy(false);
    }
  }

  const spendable = (mine ?? []).filter((c) => c.spendable).length;

  return (
    <div className="mt-8 grid gap-8 lg:grid-cols-2">
      <Card>
        <CardTitle>Claim a comp-off</CardTitle>
        <p className="mt-1 text-xs text-fg/40">
          Worked a holiday or a weekend? Claim the day back. Your approver decides, and the credit
          expires if it is not used.
        </p>
        {error && <Alert tone="error" className="mt-3">{error}</Alert>}
        <form onSubmit={submit} className="mt-4 flex flex-col gap-4">
          <Field label="Day worked" htmlFor="workedOn">
            {/* max=today: the server refuses a future date, and a date picker that cannot offer one
                is kinder than an error after submitting. */}
            <Input
              id="workedOn"
              type="date"
              max={new Date().toISOString().slice(0, 10)}
              value={workedOn}
              onChange={(e) => setWorkedOn(e.target.value)}
              required
            />
          </Field>
          <Field label="Reason (optional)" htmlFor="compReason">
            <Input
              id="compReason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. release weekend"
            />
          </Field>
          <Button type="submit" disabled={busy} className="self-start">
            {busy && <Loader2 className="h-4 w-4 animate-spin" />}
            Claim
          </Button>
        </form>
      </Card>

      <div>
        <h2 className="text-sm font-medium uppercase tracking-wide text-fg/40">
          My comp-off {spendable > 0 && <span className="text-fg/60">· {spendable} available</span>}
        </h2>
        <div className="mt-3 flex flex-col gap-2">
          {mine === null ? (
            <Card><Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" /></Card>
          ) : mine.length === 0 ? (
            <Card className="text-sm text-fg/50">Nothing claimed yet.</Card>
          ) : (
            mine.map((c) => (
              <Card key={c.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
                <div>
                  <p className="text-sm font-medium">Worked {c.workedOn}</p>
                  <p className="text-xs text-fg/50">
                    {c.reason ?? "No reason given"}
                    {c.expiresOn && (
                      <>
                        {" · "}
                        {c.spendable ? `use by ${c.expiresOn}` : `expired ${c.expiresOn}`}
                      </>
                    )}
                  </p>
                </div>
                <Badge value={statusLabel(c)} />
              </Card>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * Approvals queue for comp-off claims.
 *
 * <p>Scoped by the API, not here: HR and admins see the company, a manager sees their own reports.
 */
export function CompOffApprovals() {
  const [pending, setPending] = useState<CompOffCredit[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setPending(await api.pendingCompOff());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load comp-off requests");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function decide(id: string, action: "approve" | "reject") {
    try {
      await api.decideCompOff(id, action);
      await load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to update request");
    }
  }

  // Nothing waiting is not worth a heading — the screen already has two other sections.
  if (pending.length === 0 && !error) return null;

  return (
    <div className="mt-10">
      <h2 className="text-sm font-medium uppercase tracking-wide text-fg/40">Comp-off requests</h2>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      <div className="mt-3 flex flex-col gap-2">
        {pending.map((c) => (
          <Card key={c.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
            <div>
              <p className="text-sm font-medium">{c.employeeName}</p>
              <p className="text-xs text-fg/50">
                worked {c.workedOn}
                {c.reason && <> · {c.reason}</>}
              </p>
            </div>
            <div className="flex gap-2">
              <Button variant="ghost" onClick={() => decide(c.id, "reject")}>
                <X className="h-4 w-4" /> Reject
              </Button>
              <Button onClick={() => decide(c.id, "approve")}>
                <Check className="h-4 w-4" /> Approve
              </Button>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

/**
 * An APPROVED credit past its date is not spendable, and labelling it "approved" is a lie the person
 * would only discover when their leave request was refused.
 */
function statusLabel(c: CompOffCredit): string {
  if (c.status === "APPROVED" && !c.spendable) return "EXPIRED";
  return c.status;
}
