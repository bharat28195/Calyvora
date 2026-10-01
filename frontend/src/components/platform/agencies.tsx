"use client";

import { useEffect, useState } from "react";
import { Loader2, Plus } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { AgencySummary } from "@/lib/types";
import { Card, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";
import { money } from "@/lib/format";

/**
 * Agencies — customers who run several companies (PD-18). Optional: a company sold direct has no
 * agency and simply appears in the companies table as a plain entry.
 */
export function AgenciesSection() {
  const [agencies, setAgencies] = useState<AgencySummary[] | null>(null);
  const [creating, setCreating] = useState(false);

  const load = () => void api.platformAgencies().then(setAgencies).catch(() => setAgencies([]));
  useEffect(() => { load(); }, []);

  return (
    <Card>
      {/* No "Agencies" title here — the page already carries it. */}
      <div className="flex flex-wrap items-start justify-between gap-2">
        <CardDescription className="mt-0 max-w-xl">
          Groups that run several companies. They provision their own companies; you decide when
          billing starts.
        </CardDescription>
        <Button size="sm" variant="secondary" onClick={() => setCreating((v) => !v)}>
          <Plus className="h-4 w-4" /> New agency
        </Button>
      </div>

      {creating && (
        <CreateAgencyForm
          onCreated={() => { setCreating(false); load(); }}
          onCancel={() => setCreating(false)}
        />
      )}

      {agencies === null && (
        <div className="mt-3 flex items-center gap-2 text-sm text-fg/50">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </div>
      )}

      {agencies && agencies.length > 0 && (
        <div className="mt-3 flex flex-col divide-y divide-fg/5">
          {agencies.map((a) => (
            <div key={a.agencyId} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
              <div className="min-w-0">
                <p className="text-sm font-medium">{a.name}</p>
                <p className="truncate text-xs text-fg/40">{a.ownerName} · {a.ownerEmail}</p>
              </div>
              <div className="flex items-center gap-4 text-sm">
                <span className="text-fg/60">{a.companyCount} companies</span>
                <span className="text-fg/60">{a.headcount} employees</span>
                <span className="tabular-nums text-fg/80">{money(a.monthlyRevenue ?? 0)}/mo</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {agencies && agencies.length === 0 && !creating && (
        <p className="mt-3 text-sm text-fg/50">No agencies yet — every company is sold direct.</p>
      )}
    </Card>
  );
}

function CreateAgencyForm({ onCreated, onCancel }: { onCreated: () => void; onCancel: () => void }) {
  const [v, setV] = useState({ agencyName: "", ownerFirstName: "", ownerLastName: "", ownerEmail: "", password: "" });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const set = (k: keyof typeof v) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setV((prev) => ({ ...prev, [k]: e.target.value }));
    setFieldErrors((f) => (f[k] ? { ...f, [k]: "" } : f));
  };

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true); setError(null); setFieldErrors({});
    try {
      await api.createAgency(v);
      onCreated();
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors);
        setError(Object.keys(err.fieldErrors).length === 0 ? err.message : "Please correct the highlighted fields.");
      } else {
        setError("Couldn't create the agency");
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className="mt-4 flex flex-col gap-4 rounded-xl border border-fg/10 p-4">
      {error && <Alert tone="error">{error}</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Agency name" htmlFor="agencyName" error={fieldErrors.agencyName}>
          <Input id="agencyName" value={v.agencyName} onChange={set("agencyName")} />
        </Field>
        <Field label="Owner email" htmlFor="ownerEmail" error={fieldErrors.ownerEmail}>
          <Input id="ownerEmail" type="email" value={v.ownerEmail} onChange={set("ownerEmail")} />
        </Field>
        <Field label="Owner first name" htmlFor="ownerFirstName" error={fieldErrors.ownerFirstName}>
          <Input id="ownerFirstName" value={v.ownerFirstName} onChange={set("ownerFirstName")} />
        </Field>
        <Field label="Owner last name" htmlFor="ownerLastName" error={fieldErrors.ownerLastName}>
          <Input id="ownerLastName" value={v.ownerLastName} onChange={set("ownerLastName")} />
        </Field>
        <Field label="Temporary password" htmlFor="agencyPassword" error={fieldErrors.password}>
          <Input id="agencyPassword" value={v.password} onChange={set("password")} />
        </Field>
      </div>
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={saving}>
          {saving && <Loader2 className="h-4 w-4 animate-spin" />} Create agency
        </Button>
        <Button type="button" size="sm" variant="ghost" onClick={onCancel}>Cancel</Button>
      </div>
    </form>
  );
}
