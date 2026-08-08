"use client";

import { useEffect, useState } from "react";
import { Loader2, Plus, Tag, Trash2 } from "lucide-react";
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
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

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
    <Card className="mt-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <CardTitle>Pricing</CardTitle>
          <p className="mt-0.5 text-sm text-fg/50">
            What every company on the standard list pays. Takes effect on its start date — no deploy.
          </p>
        </div>
        {current && (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-fg/10 px-2.5 py-1 text-xs text-fg/60">
            <Tag className="h-3 w-3" /> in force since {current.effectiveFrom}
          </span>
        )}
      </div>

      {error && <Alert tone="error" className="mt-4">{error}</Alert>}
      {saved && <Alert tone="success" className="mt-4">Price list published.</Alert>}

      {versions === null ? (
        <div className="mt-6 flex justify-center"><Loader2 className="h-5 w-5 animate-spin text-violet" /></div>
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
          </div>

          {versions.length > 0 && (
            <div className="mt-6 border-t border-fg/10 pt-4">
              <p className="text-xs uppercase tracking-wide text-fg/40">History</p>
              <div className="mt-2 flex flex-col divide-y divide-fg/5">
                {versions.map((v) => (
                  <div key={v.id} className="flex flex-wrap items-baseline justify-between gap-2 py-2">
                    <span className="text-sm">
                      <span className="tabular-nums text-fg/80">{v.effectiveFrom}</span>
                      {v.current && (
                        <span className="ml-2 rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs text-emerald-400">
                          current
                        </span>
                      )}
                      {v.note && <span className="ml-2 text-fg/40">{v.note}</span>}
                    </span>
                    <span className="text-xs text-fg/50">
                      {v.tiers.map((t) =>
                        t.toEmployee ? `≤${t.toEmployee}: ${money(t.rate)}` : `then ${money(t.rate)}`,
                      ).join(" · ")}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </Card>
  );
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}
