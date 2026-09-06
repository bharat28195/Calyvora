"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Check, Loader2, Minus, Plus, X } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { CompanySummary, FeatureState, Plan } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

const selectCls =
  "h-9 rounded-lg border border-fg/15 bg-fg/5 px-2 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

/**
 * Plans and per-customer features — what each package includes, what it costs, and who has what.
 *
 * <p>Two halves, in the order they are used: the catalogue you sell from, then one customer at a
 * time. The second half is the one that gets opened during a support call, so it shows not just
 * whether a module is on but <em>why</em> — an override, an agency, a plan or a default. Only one of
 * those four is ever a mistake, and without the reason nobody can tell which.
 */
export default function PlansPage() {
  const [plans, setPlans] = useState<Plan[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setPlans(await api.plans());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load plans");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Plans &amp; features</h1>
        <p className="mt-1 text-fg/50">
          What each package includes and costs, and which modules any one customer can use.
        </p>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {note && <Alert tone="success" className="mt-6">{note}</Alert>}

      <h2 className="mt-8 text-sm font-medium uppercase tracking-wide text-fg/40">The catalogue</h2>
      <div className="mt-3 flex flex-col gap-3">
        {plans === null ? (
          <Card><Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" /></Card>
        ) : (
          plans.map((p) => (
            <PlanCard key={p.code} plan={p} onSaved={(m) => { setNote(m); setError(null); void load(); }}
              onError={(m) => { setError(m); setNote(null); }} />
          ))
        )}
        <NewPlan onCreated={(m) => { setNote(m); void load(); }} onError={setError} />
      </div>

      <h2 className="mt-10 text-sm font-medium uppercase tracking-wide text-fg/40">One customer</h2>
      <CompanyFeatures plans={plans ?? []} onError={setError} onNote={setNote} />
    </div>
  );
}

/** One plan: its price and the features it includes, saved on its own. */
function PlanCard({ plan, onSaved, onError }: {
  plan: Plan; onSaved: (message: string) => void; onError: (message: string) => void;
}) {
  const [price, setPrice] = useState(plan.pricePerEmployee == null ? "" : String(plan.pricePerEmployee));
  const [features, setFeatures] = useState<string[]>(plan.features);
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(false);

  const dirty = useMemo(
    () =>
      (price === "" ? null : Number(price)) !== plan.pricePerEmployee ||
      features.length !== plan.features.length ||
      features.some((f) => !plan.features.includes(f)),
    [price, features, plan],
  );

  async function save() {
    setBusy(true);
    try {
      await api.updatePlan(plan.code, {
        // Blank means "no plan price, charge the published list" — a real state, not a missing value.
        pricePerEmployee: price === "" ? null : Number(price),
        features,
      });
      onSaved(`${plan.name} saved.`);
    } catch (e) {
      onError(e instanceof ApiError ? e.message : "Failed to save the plan");
    } finally {
      setBusy(false);
    }
  }

  async function toggleActive() {
    setBusy(true);
    try {
      await api.updatePlan(plan.code, { active: !plan.active });
      onSaved(`${plan.name} ${plan.active ? "retired" : "reactivated"}.`);
    } catch (e) {
      onError(e instanceof ApiError ? e.message : "Failed to update the plan");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className={plan.active ? "" : "opacity-60"}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle>
            {plan.name}
            {!plan.active && <span className="ml-2 text-xs font-normal text-fg/40">retired</span>}
          </CardTitle>
          <p className="mt-1 text-xs text-fg/50">{plan.description}</p>
          <p className="mt-1 text-xs text-fg/40">
            {plan.features.length} module{plan.features.length === 1 ? "" : "s"} · code {plan.code}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <label className="flex items-center gap-1 text-xs text-fg/50">
            per employee
            <Input type="number" min={0} className="w-24" value={price} placeholder="list price"
              onChange={(e) => setPrice(e.target.value)} />
          </label>
          <Button variant="ghost" onClick={() => setOpen(!open)}>{open ? "Hide" : "Modules"}</Button>
          <Button onClick={save} disabled={busy || !dirty}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />}
            Save
          </Button>
        </div>
      </div>

      {open && (
        <div className="mt-4 border-t border-fg/10 pt-4">
          <FeaturePicker selected={features} onChange={setFeatures} />
          <button type="button" onClick={toggleActive}
            className="mt-4 text-xs text-fg/40 underline underline-offset-2 hover:text-fg/70">
            {plan.active ? "Retire this plan" : "Reactivate this plan"}
          </button>
          {plan.active && (
            <p className="mt-1 text-[11px] text-fg/40">
              Retiring stops it being assigned to anybody new. Customers already on it keep it — plans
              are never deleted, because a company pointing at a missing plan would silently get
              everything.
            </p>
          )}
        </div>
      )}
    </Card>
  );
}

/**
 * The module checkboxes.
 *
 * <p>The list comes from whatever the backend reports for a company, so a feature added in Java
 * appears here without a second edit. Falls back to nothing rather than a hard-coded list that would
 * quietly drift out of date.
 */
function FeaturePicker({ selected, onChange }: { selected: string[]; onChange: (next: string[]) => void }) {
  const [all, setAll] = useState<FeatureState[]>([]);

  useEffect(() => {
    api.companyFeatures().then(setAll).catch(() => setAll([]));
  }, []);

  if (all.length === 0) {
    return <p className="text-xs text-fg/40">Loading modules…</p>;
  }

  return (
    <div className="grid gap-2 sm:grid-cols-2">
      {all.map((f) => {
        const on = selected.includes(f.feature);
        return (
          <label key={f.feature} className="flex items-start gap-2 text-sm">
            <input type="checkbox" className="mt-1" checked={on}
              onChange={() => onChange(on ? selected.filter((x) => x !== f.feature) : [...selected, f.feature])} />
            <span>
              <span className="font-medium">{f.label}</span>
              <span className="mt-0.5 block text-xs text-fg/40">{f.description}</span>
            </span>
          </label>
        );
      })}
    </div>
  );
}

function NewPlan({ onCreated, onError }: { onCreated: (m: string) => void; onError: (m: string) => void }) {
  const [open, setOpen] = useState(false);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [price, setPrice] = useState("");
  const [features, setFeatures] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  if (!open) {
    return (
      <Button variant="ghost" className="self-start" onClick={() => setOpen(true)}>
        <Plus className="h-4 w-4" /> New plan
      </Button>
    );
  }

  async function create() {
    setBusy(true);
    try {
      await api.createPlan({
        code, name,
        pricePerEmployee: price === "" ? null : Number(price),
        features,
      });
      onCreated(`${name} created.`);
      setOpen(false);
      setCode(""); setName(""); setPrice(""); setFeatures([]);
    } catch (e) {
      onError(e instanceof ApiError ? e.message : "Failed to create the plan");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-xs text-fg/50">
          Code
          <Input className="w-32" value={code} placeholder="TINY" onChange={(e) => setCode(e.target.value)} />
        </label>
        <label className="flex flex-col gap-1 text-xs text-fg/50">
          Name
          <Input className="w-48" value={name} placeholder="Tiny" onChange={(e) => setName(e.target.value)} />
        </label>
        <label className="flex flex-col gap-1 text-xs text-fg/50">
          Per employee
          <Input type="number" min={0} className="w-28" value={price} onChange={(e) => setPrice(e.target.value)} />
        </label>
        <Button onClick={create} disabled={busy || !code.trim() || !name.trim()}>
          {busy && <Loader2 className="h-4 w-4 animate-spin" />} Create
        </Button>
        <Button variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
      </div>
      <div className="mt-4 border-t border-fg/10 pt-4">
        <FeaturePicker selected={features} onChange={setFeatures} />
      </div>
    </Card>
  );
}

/** Pick a customer, see what they can use and why, and change it. */
function CompanyFeatures({ plans, onError, onNote }: {
  plans: Plan[]; onError: (m: string) => void; onNote: (m: string) => void;
}) {
  const [companies, setCompanies] = useState<CompanySummary[]>([]);
  const [companyId, setCompanyId] = useState("");
  const [features, setFeatures] = useState<FeatureState[] | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    api.platformCompanies().then(setCompanies).catch(() => setCompanies([]));
  }, []);

  const loadFeatures = useCallback(async (id: string) => {
    if (!id) { setFeatures(null); return; }
    try {
      setFeatures(await api.companyFeaturesFor(id));
    } catch (e) {
      onError(e instanceof ApiError ? e.message : "Failed to load this customer's features");
    }
  }, [onError]);

  useEffect(() => {
    void loadFeatures(companyId);
  }, [companyId, loadFeatures]);

  const company = companies.find((c) => c.companyId === companyId);

  async function setPlan(planCode: string) {
    setBusy("plan");
    try {
      setFeatures(await api.setCompanyPlan(companyId, planCode || null));
      onNote(planCode ? `Moved to ${planCode}.` : "Taken off any plan.");
    } catch (e) {
      onError(e instanceof ApiError ? e.message : "Failed to change the plan");
    } finally {
      setBusy(null);
    }
  }

  async function override(feature: string, enabled: boolean | null) {
    setBusy(feature);
    try {
      await api.setCompanyFeature(companyId, feature, enabled);
      await loadFeatures(companyId);
    } catch (e) {
      onError(e instanceof ApiError ? e.message : "Failed to change that module");
    } finally {
      setBusy(null);
    }
  }

  return (
    <Card className="mt-3">
      <div className="flex flex-wrap items-center gap-3">
        <select className={`${selectCls} min-w-[16rem]`} value={companyId}
          onChange={(e) => setCompanyId(e.target.value)} aria-label="Customer">
          <option value="" className="bg-surface">Choose a customer…</option>
          {companies.map((c) => (
            <option key={c.companyId} value={c.companyId} className="bg-surface">
              {c.name}{c.agencyName ? ` — ${c.agencyName}` : ""}
            </option>
          ))}
        </select>

        {companyId && (
          <label className="flex items-center gap-2 text-xs text-fg/50">
            Plan
            <select className={selectCls} disabled={busy === "plan"}
              value={plans.find((p) => features?.some((f) => f.planCode === p.code))?.code ?? ""}
              onChange={(e) => setPlan(e.target.value)}>
              <option value="" className="bg-surface">No plan (everything)</option>
              {plans.map((p) => (
                <option key={p.code} value={p.code} className="bg-surface" disabled={!p.active}>
                  {p.name}{p.active ? "" : " (retired)"}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {company && (
        <p className="mt-2 text-xs text-fg/40">
          {company.headcount} people · {company.seats} seats
          {company.agencyName && <> · under {company.agencyName}</>}
        </p>
      )}

      {features && (
        <div className="mt-5 flex flex-col gap-1.5">
          {features.map((f) => (
            <div key={f.feature} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-fg/10 px-3 py-2">
              <div className="min-w-[12rem]">
                <p className="text-sm font-medium">
                  {f.label}
                  <SourceTag state={f} />
                </p>
                <p className="text-xs text-fg/40">{f.description}</p>
              </div>
              <div className="flex items-center gap-1">
                <Toggle label="On" active={f.enabled && f.source === "COMPANY"} busy={busy === f.feature}
                  onClick={() => override(f.feature, true)} icon={<Check className="h-3.5 w-3.5" />} />
                <Toggle label="Off" active={!f.enabled && f.source === "COMPANY"} busy={busy === f.feature}
                  onClick={() => override(f.feature, false)} icon={<X className="h-3.5 w-3.5" />} />
                {/* The third state. Without it there is no way back to "whatever the plan says", and
                    an owner would have to remember what that was. */}
                <Toggle label="Plan" active={f.source !== "COMPANY"} busy={busy === f.feature}
                  onClick={() => override(f.feature, null)} icon={<Minus className="h-3.5 w-3.5" />} />
              </div>
            </div>
          ))}
        </div>
      )}

      {!companyId && (
        <p className="mt-4 text-sm text-fg/50">
          Pick a customer to see which modules they can use, and why.
        </p>
      )}
    </Card>
  );
}

/** Where a feature's state came from — the thing that makes a support call answerable. */
function SourceTag({ state }: { state: FeatureState }) {
  const text =
    state.source === "COMPANY" ? "set for them"
      : state.source === "AGENCY" ? "from their agency"
        : state.source === "PLAN" ? `from ${state.planCode}`
          : "default";
  return <span className="ml-2 text-[11px] font-normal text-fg/40">{text}</span>;
}

function Toggle({ label, active, busy, onClick, icon }: {
  label: string; active: boolean; busy: boolean; onClick: () => void; icon: React.ReactNode;
}) {
  return (
    <button type="button" onClick={onClick} disabled={busy}
      className={`inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs transition-colors disabled:opacity-40 ${
        active ? "bg-violet/20 text-violet" : "text-fg/50 hover:bg-fg/5 hover:text-fg"
      }`}>
      {icon}{label}
    </button>
  );
}
