"use client";

import { useEffect, useState } from "react";
import { Loader2, Pencil, Plus, Tag, Trash2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { PriceListVersion } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";
import { money } from "@/lib/format";

type DraftTier = { toEmployee: string; rate: string };

/**
 * Edit what the platform charges, without a deploy.
 *
 * <p>Prices are versioned by the date they start, so publishing a change never rewrites an invoice
 * that has already been issued — the history list below makes that visible rather than asking anyone
 * to take it on trust.
 */
export function PricingEditor() {
  const [versions, setVersions] = useState<PriceListVersion[] | null>(null);
  const [tiers, setTiers] = useState<DraftTier[]>([]);
  const [effectiveFrom, setEffectiveFrom] = useState(today());
  const [note, setNote] = useState("");
  const [minimum, setMinimum] = useState("0");
  const [annualMonths, setAnnualMonths] = useState("12");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  // Read first, change second: the editor opens only when asked for.
  const [editing, setEditing] = useState(false);

  function load() {
    api.platformPricing()
      .then((v) => {
        setVersions(v);
        const current = v.find((x) => x.current) ?? v[0];
        if (current) {
          setTiers(current.tiers.map((t: PriceListVersion["tiers"][number]) => ({
            toEmployee: t.toEmployee == null ? "" : String(t.toEmployee),
            rate: String(t.rate),
          })));
          setMinimum(String(current.monthlyMinimum ?? 0));
          setAnnualMonths(String(current.annualMonthsCharged ?? 12));
        }
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : "Couldn't load pricing"));
  }
  useEffect(load, []);

  function setTier(i: number, patch: Partial<DraftTier>) {
    setTiers((ts) => ts.map((t, k) => (k === i ? { ...t, ...patch } : t)));
    setSaved(false);
  }

  async function publish() {
    setSaving(true);
    setError(null);
    try {
      await api.publishPricing({
        effectiveFrom,
        note: note.trim() || undefined,
        // The last tier is always open-ended — there must be no headcount without a price.
        tiers: tiers.map((t, i) => ({
          toEmployee: i === tiers.length - 1 ? null : Number(t.toEmployee),
          rate: Number(t.rate),
        })),
        monthlyMinimum: Number(minimum || 0),
        annualMonthsCharged: Number(annualMonths || 12),
      });
      setSaved(true);
      setNote("");
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Couldn't publish the price list");
    } finally {
      setSaving(false);
    }
  }

  const current = versions?.find((v) => v.current);

  return (
    <Card>
      {/* No title of its own — the page it sits on already says "Pricing", and saying it twice on the
          same screen is the kind of duplication that appears the moment a section becomes a page. */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <CardTitle>{editing ? "Edit the price list" : "Current price list"}</CardTitle>
        {current && (
          <span className="inline-flex items-center gap-1.5 text-xs text-fg/50">
            <Tag className="h-3 w-3" /> in force since {current.effectiveFrom}
          </span>
        )}
      </div>

      {error && <Alert tone="error" className="mt-4">{error}</Alert>}
      {saved && <Alert tone="success" className="mt-4">Price list published.</Alert>}

      {versions === null ? (
        <div className="mt-6 flex justify-center"><Loader2 className="h-5 w-5 animate-spin text-violet" /></div>
      ) : !editing ? (
        <>
          <CurrentPriceList current={current} onEdit={() => { setEditing(true); setSaved(false); }} />
          <History versions={versions} />
        </>
      ) : (
        <>
          <div className="mt-5 flex flex-col gap-3">
            {tiers.map((t, i) => {
              const last = i === tiers.length - 1;
              const from = i === 0 ? 1 : Number(tiers[i - 1].toEmployee || 0) + 1;
              return (
                <div key={i} className="flex flex-wrap items-end gap-3 rounded-lg border border-fg/10 bg-fg/[0.02] p-3">
                  <div className="min-w-[5rem] pb-2 text-sm text-fg/50">
                    From <span className="font-medium text-fg/80 tabular-nums">{from}</span>
                  </div>
                  <Field label="Up to employee" htmlFor={`upto-${i}`} className="w-36">
                    <Input
                      id={`upto-${i}`}
                      type="number"
                      min={1}
                      value={last ? "" : t.toEmployee}
                      onChange={(e) => setTier(i, { toEmployee: e.target.value })}
                      disabled={last}
                      placeholder={last ? "no limit" : "100"}
                    />
                  </Field>
                  <Field label="Rate / employee / mo" htmlFor={`rate-${i}`} className="w-44">
                    <Input
                      id={`rate-${i}`}
                      type="number"
                      min={0}
                      step="0.01"
                      value={t.rate}
                      onChange={(e) => setTier(i, { rate: e.target.value })}
                    />
                  </Field>
                  {tiers.length > 1 && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="mb-1"
                      onClick={() => { setTiers((ts) => ts.filter((_, k) => k !== i)); setSaved(false); }}
                      aria-label={`Remove tier ${i + 1}`}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>
              );
            })}
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="self-start"
              onClick={() => {
                // The new tier becomes the open-ended one, so the previous last needs a limit.
                setTiers((ts) => {
                  const next = [...ts];
                  const lastIndex = next.length - 1;
                  if (lastIndex >= 0 && !next[lastIndex].toEmployee) {
                    next[lastIndex] = { ...next[lastIndex], toEmployee: "" };
                  }
                  return [...next, { toEmployee: "", rate: "" }];
                });
                setSaved(false);
              }}
            >
              <Plus className="h-4 w-4" /> Add a tier
            </Button>
          </div>

          {/* Commercial terms that aren't per-employee rates but belong to the same versioned list. */}
          <div className="mt-5 flex flex-wrap items-end gap-3 border-t border-fg/10 pt-5">
            <Field
              label="Monthly minimum"
              htmlFor="minimum"
              className="w-48"
              hint="The floor, whatever the headcount. 0 = none."
            >
              <Input id="minimum" type="number" min={0} step="1" value={minimum}
                onChange={(e) => { setMinimum(e.target.value); setSaved(false); }} />
            </Field>
            <Field
              label="Annual billing charges"
              htmlFor="annualMonths"
              className="w-56"
              hint="Months per prepaid year. 10 = two months free."
            >
              <Input id="annualMonths" type="number" min={1} max={12} value={annualMonths}
                onChange={(e) => { setAnnualMonths(e.target.value); setSaved(false); }} />
            </Field>
            <p className="mb-2 text-xs text-fg/40">
              {Number(annualMonths) < 12
                ? `Paying yearly costs ${12 - Number(annualMonths)} month${12 - Number(annualMonths) === 1 ? "" : "s"} less than paying monthly.`
                : "No discount for paying yearly."}
            </p>
          </div>

          <div className="mt-5 flex flex-wrap items-end gap-3 border-t border-fg/10 pt-5">
            <Field
              label="Takes effect from"
              htmlFor="effectiveFrom"
              className="w-48"
              hint="Months already invoiced keep their old price."
            >
              <Input id="effectiveFrom" type="date" value={effectiveFrom}
                onChange={(e) => { setEffectiveFrom(e.target.value); setSaved(false); }} />
            </Field>
            <Field label="Note (optional)" htmlFor="note" className="min-w-[14rem] flex-1">
              <Input id="note" value={note} placeholder="Why this changed"
                onChange={(e) => setNote(e.target.value)} />
            </Field>
            <Button type="button" onClick={publish} disabled={saving} className="mb-1">
              {saving && <Loader2 className="h-4 w-4 animate-spin" />}
              {saving ? "Publishing…" : "Publish price list"}
            </Button>
            {/* Reloads rather than just hiding the form: leaving edits in memory would mean reopening
                the editor showed changes that were never published, and the table would disagree. */}
            <Button type="button" variant="ghost" className="mb-1" disabled={saving}
              onClick={() => { setEditing(false); setError(null); load(); }}>
              Cancel
            </Button>
          </div>

          <History versions={versions} />
        </>
      )}
    </Card>
  );
}

/**
 * What the price list says, as a table you can read at a glance.
 *
 * <p>The editor used to be the only view, so eight always-live inputs stood permanently on screen
 * for something changed a few times a year — and a form is harder to read than a table, because every
 * value sits in a box that invites typing into it. Reading and changing are different jobs.
 */
function CurrentPriceList({ current, onEdit }: { current: PriceListVersion | undefined; onEdit: () => void }) {
  if (!current) {
    return (
      <div className="mt-5">
        <p className="text-sm text-fg/50">No price list published yet.</p>
        <Button type="button" size="sm" className="mt-3" onClick={onEdit}>Set the first price list</Button>
      </div>
    );
  }
  return (
    <div className="mt-5">
      <div className="overflow-x-auto rounded-lg border border-fg/10">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-fg/10 text-left text-xs text-fg/40">
              <th className="px-4 py-2.5 font-medium">Employees</th>
              <th className="px-4 py-2.5 text-right font-medium">Rate / employee / month</th>
            </tr>
          </thead>
          <tbody>
            {current.tiers.map((t, i) => {
              const from = i === 0 ? 1 : (current.tiers[i - 1].toEmployee ?? 0) + 1;
              return (
                <tr key={i} className="border-b border-fg/5 last:border-0">
                  <td className="px-4 py-2.5 tabular-nums text-fg/80">
                    {t.toEmployee == null ? `${from} and above` : `${from} – ${t.toEmployee}`}
                  </td>
                  <td className="px-4 py-2.5 text-right tabular-nums text-fg/80">{money(t.rate)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <dl className="mt-3 flex flex-wrap gap-x-8 gap-y-1 text-sm">
        <div className="flex gap-2">
          <dt className="text-fg/50">Monthly minimum</dt>
          <dd className="tabular-nums text-fg/80">{current.monthlyMinimum > 0 ? money(current.monthlyMinimum) : "none"}</dd>
        </div>
        <div className="flex gap-2">
          <dt className="text-fg/50">Paying yearly</dt>
          <dd className="text-fg/80">
            {current.annualMonthsCharged < 12
              ? `${12 - current.annualMonthsCharged} month${12 - current.annualMonthsCharged === 1 ? "" : "s"} free`
              : "no discount"}
          </dd>
        </div>
      </dl>

      <Button type="button" size="sm" variant="secondary" className="mt-4" onClick={onEdit}>
        <Pencil className="h-4 w-4" /> Edit price list
      </Button>
    </div>
  );
}

function History({ versions }: { versions: PriceListVersion[] }) {
  if (versions.length === 0) return null;
  return (
    <div className="mt-6 border-t border-fg/10 pt-4">
      <p className="text-xs uppercase tracking-wide text-fg/40">History</p>
      <div className="mt-2 flex flex-col divide-y divide-fg/5">
        {versions.map((v) => (
          <div key={v.id} className="flex flex-wrap items-baseline justify-between gap-2 py-2">
            <span className="text-sm">
              <span className="tabular-nums text-fg/80">{v.effectiveFrom}</span>
              {v.current && <span className="ml-2 text-xs text-fg/50">current</span>}
              {v.note && <span className="ml-2 text-fg/40">{v.note}</span>}
            </span>
            <span className="text-xs text-fg/50">
              {v.tiers.map((t) =>
                t.toEmployee ? `≤${t.toEmployee}: ${money(t.rate)}` : `then ${money(t.rate)}`,
              ).join(" · ")}
              {v.monthlyMinimum > 0 && ` · min ${money(v.monthlyMinimum)}`}
              {v.annualMonthsCharged < 12 && ` · yearly ×${v.annualMonthsCharged}`}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}
