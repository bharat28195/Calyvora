"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ArrowRight, Check, Loader2, Lock } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { TaxComputation, TaxDeclaration, TaxRegime } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { money } from "@/lib/format";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/**
 * Declare what you are claiming, and choose which set of rules you are taxed under.
 *
 * <p>The regime choice is the whole reason this screen exists. India runs two in parallel: the new
 * one has wider slabs and a much larger rebate but disallows nearly every deduction, the old one
 * taxes more steeply and lets you subtract what you have invested. Neither wins in general — it
 * depends entirely on how much a particular person has to deduct — so the only honest way to present
 * it is to price both on the employee's own numbers and let them see the gap. That comparison is the
 * single most valuable thing this page does, and it is a question people otherwise answer with a
 * spreadsheet, or not at all.
 */
export default function TaxDeclarationPage() {
  const [data, setData] = useState<TaxDeclaration | null>(null);
  const [computation, setComputation] = useState<TaxComputation | null>(null);
  const [amounts, setAmounts] = useState<Record<string, string>>({});
  const [regime, setRegime] = useState<TaxRegime>("NEW");
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const d = await api.taxDeclaration();
      setData(d);
      setRegime(d.regime);
      setAmounts(Object.fromEntries(
        Object.entries(d.declared).map(([k, v]) => [k, String(v)])));
      setComputation(await api.taxComputation());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load your tax declaration");
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function save(nextRegime: TaxRegime = regime) {
    setBusy(true);
    setError(null);
    try {
      const declared: Record<string, number> = {};
      for (const [key, raw] of Object.entries(amounts)) {
        const n = Number(raw);
        if (raw.trim() !== "" && Number.isFinite(n) && n > 0) declared[key] = n;
      }
      const d = await api.saveTaxDeclaration({ regime: nextRegime, declared });
      setData(d);
      setRegime(d.regime);
      // Reprice immediately: changing the regime without seeing what it costs is the one thing this
      // page must not let somebody do.
      setComputation(await api.taxComputation());
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not save your declaration");
    } finally {
      setBusy(false);
    }
  }

  async function submit() {
    setBusy(true);
    try {
      await save(regime);
      setData(await api.submitTaxDeclaration());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not submit your declaration");
    } finally {
      setBusy(false);
    }
  }

  const locked = data !== null && !data.windowOpen;
  // The old regime is what the deduction fields are for; under the new one all but 80CCD(2) are
  // ignored, so they are shown greyed rather than hidden — an employee switching regimes needs to
  // see what they are giving up.
  const fieldsMatter = regime === "OLD";

  // An error before anything loaded has to be shown here. The spinner used to win this race
  // unconditionally, so a failed first load spun forever with the message set and never rendered —
  // found by the e2e sweep, which opens this page against a backend that refuses it.
  //
  // The heading stays even then: a screen that cannot load should still say which screen it is,
  // rather than leaving the reader to work it out from the address bar.
  if (data === null) {
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Tax declaration</h1>
        {error !== null
          ? <Alert tone="error" className="mt-6">{error}</Alert>
          : <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>}
      </div>
    );
  }

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Tax declaration</h1>
          <p className="mt-1 text-fg/50">
            Financial year {data.financialYear} · {statusLabel(data.status)}
          </p>
        </div>
        <Link href="/finance/tax/computation">
          <Button variant="secondary">How my tax is calculated <ArrowRight className="h-4 w-4" /></Button>
        </Link>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {locked && (
        <Alert tone="warning" className="mt-6">
          <Lock className="mr-2 inline h-4 w-4" />
          Declarations are closed for {data.financialYear}. Ask HR to reopen them if something has changed.
        </Alert>
      )}

      <RegimePicker
        regime={regime}
        computation={computation}
        currency={computation?.currency ?? "INR"}
        disabled={busy || locked}
        onChange={(r) => { setRegime(r); void save(r); }}
      />

      <Card className="mt-6">
        <CardTitle>What you are claiming</CardTitle>
        <p className="mt-1 text-xs text-fg/50">
          {fieldsMatter
            ? "Enter the annual amount for each. Anything above the statutory limit is trimmed when your tax is worked out — you will see both figures."
            : "The new regime allows only your employer's NPS contribution. These are kept in case you switch back."}
        </p>

        <div className="mt-5 flex flex-col gap-4">
          {data.options.map((o) => {
            const ignored = regime === "NEW" && !o.allowedInNewRegime;
            return (
              <div key={o.key} className={`grid gap-2 sm:grid-cols-[1fr_180px] sm:items-center ${ignored ? "opacity-40" : ""}`}>
                <div className="min-w-0">
                  <p className="text-sm font-medium">
                    {o.section}
                    {ignored && <span className="ml-2 text-xs font-normal text-fg/40">not allowed in the new regime</span>}
                  </p>
                  <p className="text-xs text-fg/50">
                    {o.label}
                    {o.cap !== null && <> · limit {money(o.cap)}</>}
                  </p>
                </div>
                <Input
                  type="number"
                  min="0"
                  step="1"
                  inputMode="numeric"
                  disabled={busy || locked || ignored}
                  value={amounts[o.key] ?? ""}
                  placeholder="0"
                  onChange={(e) => setAmounts((a) => ({ ...a, [o.key]: e.target.value }))}
                />
              </div>
            );
          })}
        </div>

        <div className="mt-6 flex flex-wrap items-center gap-2">
          <Button disabled={busy || locked} onClick={() => void save()}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />} Save
          </Button>
          {data.status !== "SUBMITTED" && (
            <Button variant="secondary" disabled={busy || locked} onClick={() => void submit()}>
              Submit declaration
            </Button>
          )}
          {saved && <span className="text-sm text-emerald-400">Saved</span>}
          {data.status === "SUBMITTED" && data.submittedAt && (
            <span className="text-sm text-fg/40">
              Declared on {new Date(data.submittedAt).toLocaleDateString()}
            </span>
          )}
        </div>
      </Card>
    </div>
  );
}

/** Both regimes priced on the employee's own numbers, with the cheaper one called out. */
function RegimePicker({ regime, computation, currency, disabled, onChange }: {
  regime: TaxRegime;
  computation: TaxComputation | null;
  currency: string;
  disabled: boolean;
  onChange: (r: TaxRegime) => void;
}) {
  const comparison = computation?.comparison;
  const cost = useMemo(() => ({
    OLD: comparison?.oldRegimeTax ?? null,
    NEW: comparison?.newRegimeTax ?? null,
  }), [comparison]);

  return (
    <div className="mt-6 grid gap-3 sm:grid-cols-2">
      {(["NEW", "OLD"] as TaxRegime[]).map((r) => {
        const chosen = regime === r;
        const cheaper = comparison?.cheaper === r && comparison.saving > 0;
        return (
          <button
            key={r}
            type="button"
            disabled={disabled}
            onClick={() => !chosen && onChange(r)}
            className={`rounded-xl border p-4 text-left transition-colors disabled:opacity-60 ${
              chosen ? "border-violet bg-violet/5" : "border-fg/10 hover:border-fg/25"
            }`}
          >
            <div className="flex items-center justify-between gap-2">
              <p className="font-medium">{r === "NEW" ? "New regime" : "Old regime"}</p>
              {chosen && <span className="rounded-full bg-violet/15 px-2 py-0.5 text-xs text-violet">Chosen</span>}
              {!chosen && cheaper && (
                <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs text-emerald-400">
                  Saves {money(comparison!.saving, currency)}
                </span>
              )}
            </div>
            <p className="mt-1 text-xs text-fg/50">
              {r === "NEW"
                ? "Wider slabs, ₹75,000 standard deduction, nothing tax-free up to ₹12 lakh — but almost no deductions."
                : "Steeper slabs, ₹50,000 standard deduction — but 80C, 80D, home loan interest and the rest all count."}
            </p>
            <p className="mt-3 text-xl font-semibold tabular-nums">
              {cost[r] === null ? "—" : money(cost[r]!, currency)}
              <span className="ml-1 text-sm font-normal text-fg/40">tax this year</span>
            </p>
          </button>
        );
      })}
    </div>
  );
}

function statusLabel(status: TaxDeclaration["status"]): string {
  if (status === "SUBMITTED") return "declared";
  if (status === "DRAFT") return "draft — not submitted yet";
  return "not started";
}
