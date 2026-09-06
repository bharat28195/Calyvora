"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { LeavePolicy } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

const LABELS: Record<string, string> = {
  VACATION: "Vacation",
  SICK: "Sick",
  PERSONAL: "Personal",
  UNPAID: "Unpaid",
  COMP_OFF: "Comp-off",
};

const selectCls =
  "h-10 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

/**
 * The company's leave rules.
 *
 * <p>One row per type, each saved on its own. A single "Save all" button would make a typo in one
 * row block a correct change in another, and every row here is an entitlement somebody is counting
 * on — the smallest possible unit of change is the right one.
 */
export default function LeavePolicyPage() {
  const [policies, setPolicies] = useState<LeavePolicy[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setPolicies(await api.leavePolicies());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load leave policies");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Leave policy</h1>
        <p className="mt-1 text-fg/50">
          How much time off each type grants, how it is earned, and how much survives into next year.
        </p>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {saved && <Alert tone="success" className="mt-6">{saved}</Alert>}

      <div className="mt-8 flex flex-col gap-3">
        {policies === null ? (
          <Card><Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" /></Card>
        ) : (
          policies.map((p) => (
            <PolicyRow
              key={p.type}
              policy={p}
              onSaved={(message) => {
                setSaved(message);
                setError(null);
                void load();
              }}
              onError={(m) => {
                setError(m);
                setSaved(null);
              }}
            />
          ))
        )}
      </div>

      <p className="mt-8 max-w-2xl text-xs text-fg/40">
        Changing an entitlement changes what everybody already believes they have, so it applies to
        the current year immediately. Balances are recalculated from the policy on every request —
        nothing is frozen at the point a person joined.
      </p>
    </div>
  );
}

function PolicyRow({
  policy,
  onSaved,
  onError,
}: {
  policy: LeavePolicy;
  onSaved: (message: string) => void;
  onError: (message: string) => void;
}) {
  const [daysPerYear, setDaysPerYear] = useState(String(policy.daysPerYear));
  const [carryForwardCap, setCarryForwardCap] = useState(String(policy.carryForwardCap));
  const [accrual, setAccrual] = useState(policy.accrual);
  const [expiryDays, setExpiryDays] = useState(String(policy.compOffExpiryDays));
  const [busy, setBusy] = useState(false);

  const isCompOff = policy.type === "COMP_OFF";

  async function save() {
    setBusy(true);
    try {
      await api.updateLeavePolicy(
        policy.type,
        isCompOff
          ? { compOffExpiryDays: Number(expiryDays) }
          : {
              daysPerYear: Number(daysPerYear),
              carryForwardCap: Number(carryForwardCap),
              accrual,
            },
      );
      onSaved(`${LABELS[policy.type] ?? policy.type} policy saved.`);
    } catch (e) {
      onError(e instanceof ApiError ? e.message : "Failed to save policy");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="p-4">
      <div className="flex flex-wrap items-end gap-4">
        <div className="min-w-[8rem]">
          <p className="text-sm font-medium">{LABELS[policy.type] ?? policy.type}</p>
          <p className="text-xs text-fg/40">{policy.paid ? "Paid" : "Unpaid"}</p>
        </div>

        {isCompOff ? (
          // Comp-off has no annual entitlement — it is earned a day at a time — so the only thing to
          // configure is how long a credit stays usable.
          <label className="flex flex-col gap-1 text-xs text-fg/50">
            Credit expires after (days)
            <Input
              type="number"
              min={1}
              className="w-32"
              value={expiryDays}
              onChange={(e) => setExpiryDays(e.target.value)}
            />
          </label>
        ) : (
          <>
            <label className="flex flex-col gap-1 text-xs text-fg/50">
              Days per year
              <Input
                type="number"
                min={0}
                step="0.5"
                className="w-28"
                value={daysPerYear}
                onChange={(e) => setDaysPerYear(e.target.value)}
              />
            </label>
            <label className="flex flex-col gap-1 text-xs text-fg/50">
              Earned
              <select
                className={`${selectCls} w-40`}
                value={accrual}
                onChange={(e) => setAccrual(e.target.value as LeavePolicy["accrual"])}
              >
                <option value="ANNUAL" className="bg-surface">All at once</option>
                <option value="MONTHLY" className="bg-surface">Monthly</option>
              </select>
            </label>
            <label className="flex flex-col gap-1 text-xs text-fg/50">
              Carry forward (max)
              <Input
                type="number"
                min={0}
                step="0.5"
                className="w-28"
                value={carryForwardCap}
                onChange={(e) => setCarryForwardCap(e.target.value)}
              />
            </label>
          </>
        )}

        <Button onClick={save} disabled={busy} className="ml-auto">
          {busy && <Loader2 className="h-4 w-4 animate-spin" />}
          Save
        </Button>
      </div>
    </Card>
  );
}
