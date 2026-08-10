"use client";

import { useEffect, useState } from "react";
import { Loader2, Plus, Building2, Users, Wallet, Lock } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { AgencyOverview, CompanySummary } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { money } from "@/lib/format";

const STATUS_TONE: Record<string, string> = {
  ACTIVE: "bg-emerald-500/15 text-emerald-400",
  TRIALING: "bg-sky-500/15 text-sky-400",
  PENDING: "bg-amber-500/15 text-amber-300",
  PAST_DUE: "bg-amber-500/15 text-amber-300",
  CANCELLED: "bg-red-500/15 text-red-400",
  NONE: "bg-fg/10 text-fg/50",
};

/**
 * The agency console (PD-18) — one customer's view of the companies they run.
 *
 * <p>Deliberately narrower than the platform console: no employee data (an agency sees headcount, not
 * people) and no way to switch billing on. A new company arrives awaiting activation, which only the
 * vendor can grant.
 */
export default function AgencyPage() {
  const [overview, setOverview] = useState<AgencyOverview | null>(null);
  const [companies, setCompanies] = useState<CompanySummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [seatsFor, setSeatsFor] = useState<string | null>(null);

  function load() {
    api.agencyOverview().then(setOverview).catch(() => setOverview(null));
    api.agencyCompanies()
      .then(setCompanies)
      .catch((e) => {
        setCompanies([]);
        setError(e instanceof ApiError ? e.message : "Failed to load your companies");
      });
  }
  useEffect(() => { load(); }, []);

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            {overview?.agencyName ?? "My companies"}
          </h1>
          <p className="mt-1 text-fg/50">
            Every company you run, and what each one costs. Employee data stays inside each company.
          </p>
        </div>
        <Button onClick={() => setCreating((v) => !v)}><Plus className="h-4 w-4" /> Add a company</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {creating && <CreateCompanyForm onCreated={() => { setCreating(false); load(); }} onCancel={() => setCreating(false)} />}

      {companies === null ? (
        <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <>
          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Kpi label="Companies" value={String(overview?.companies ?? companies.length)} icon={<Building2 className="h-4 w-4 text-violet" />} />
            <Kpi label="Employees" value={String(overview?.headcount ?? 0)} icon={<Users className="h-4 w-4 text-aqua" />} />
            <Kpi label="Monthly spend" value={money(overview?.monthlySpend ?? 0)} icon={<Wallet className="h-4 w-4 text-emerald-400" />} />
            <Kpi label="Awaiting activation" value={String(overview?.lockedCompanies ?? 0)} icon={<Lock className="h-4 w-4 text-amber-400" />} />
          </div>

          {(overview?.lockedCompanies ?? 0) > 0 && (
            <Alert tone="info" className="mt-4">
              A new company stays locked until Calyvora activates its subscription. Its admin can sign
              in and will see why — nothing else works until then.
            </Alert>
          )}

          <Card className="mt-6 overflow-x-auto p-0">
            <table className="w-full min-w-[860px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-fg/10 text-left text-xs uppercase tracking-wide text-fg/40">
                  <th className="px-5 py-3 font-medium">Company</th>
                  <th className="px-3 py-3 font-medium">Admin</th>
                  <th className="px-3 py-3 font-medium">Seats</th>
                  <th className="px-3 py-3 font-medium">Cost</th>
                  <th className="px-3 py-3 font-medium">Subscription</th>
                  <th className="px-3 py-3 font-medium">Renews</th>
                  <th className="px-5 py-3 font-medium">Seats</th>
                </tr>
              </thead>
              <tbody>
                {companies.length === 0 ? (
                  <tr><td colSpan={7} className="px-5 py-8 text-center text-fg/50">No companies yet. Add your first one.</td></tr>
                ) : companies.map((c) => (
                  <tr key={c.companyId} className="border-b border-fg/5 last:border-0">
                    <td className="px-5 py-3">
                      <p className="font-medium">{c.name}</p>
                      <p className="text-xs text-fg/40">{c.headcount} employee{c.headcount === 1 ? "" : "s"}</p>
                    </td>
                    <td className="px-3 py-3">
                      <p className="text-fg/80">{c.adminName}</p>
                      <p className="text-xs text-fg/40">{c.adminEmail}</p>
                    </td>
                    <td className="px-3 py-3 tabular-nums">
                      <span className={cn(c.headcount > c.seats ? "text-red-400" : "text-fg/80")}>{c.headcount}</span>
                      <span className="text-fg/40"> / {c.seats}</span>
                    </td>
                    <td className="px-3 py-3 tabular-nums text-fg/80">
                      {c.monthlyRevenue != null ? money(c.monthlyRevenue) : "—"}
                      <span className="text-xs text-fg/40">/mo</span>
                    </td>
                    <td className="px-3 py-3">
                      <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", STATUS_TONE[c.subscriptionStatus] ?? STATUS_TONE.NONE)}>
                        {c.subscriptionStatus === "PENDING" ? "awaiting activation"
                          : c.locked ? "Ended" : c.subscriptionStatus.toLowerCase()}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-fg/70">{c.endsAt ?? "—"}</td>
                    <td className="px-5 py-3">
                      <Button size="sm" variant="ghost" onClick={() => setSeatsFor(seatsFor === c.companyId ? null : c.companyId)}>
                        Request more
                      </Button>
                      {seatsFor === c.companyId && (
                        <RequestSeatsForm
                          company={c}
                          onDone={() => { setSeatsFor(null); load(); }}
                          onError={setError}
                        />
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        </>
      )}
    </div>
  );
}

function Kpi({ label, value, icon }: { label: string; value: string; icon: React.ReactNode }) {
  return (
    <Card className="p-4">
      <div className="flex items-center gap-2 text-xs uppercase tracking-wide text-fg/40">{icon}{label}</div>
      <p className="mt-1.5 text-2xl font-semibold tabular-nums tracking-tight">{value}</p>
    </Card>
  );
}

function RequestSeatsForm({ company, onDone, onError }: {
  company: CompanySummary;
  onDone: () => void;
  onError: (m: string) => void;
}) {
  const [seats, setSeats] = useState(String(company.seats + 5));
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);

  return (
    <div className="mt-2 flex flex-col gap-2">
      <Input value={seats} onChange={(e) => setSeats(e.target.value)} className="w-24" />
      <Input value={note} placeholder="Why?" onChange={(e) => setNote(e.target.value)} />
      <Button
        size="sm"
        disabled={saving}
        onClick={async () => {
          setSaving(true);
          try {
            await api.agencyRequestSeats(company.companyId, Number(seats), note);
            onDone();
          } catch (e) {
            onError(e instanceof ApiError ? e.message : "Couldn't send the request");
          } finally {
            setSaving(false);
          }
        }}
      >
        {saving && <Loader2 className="h-4 w-4 animate-spin" />} Send
      </Button>
    </div>
  );
}

function CreateCompanyForm({ onCreated, onCancel }: { onCreated: () => void; onCancel: () => void }) {
  const [v, setV] = useState({
    companyName: "", adminFirstName: "", adminLastName: "", adminEmail: "", password: "", seats: "10",
  });
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
      await api.agencyCreateCompany({
        companyName: v.companyName,
        adminFirstName: v.adminFirstName,
        adminLastName: v.adminLastName,
        adminEmail: v.adminEmail,
        password: v.password,
        seats: Number(v.seats) || 1,
        months: 12,
      });
      onCreated();
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors);
        setError(Object.keys(err.fieldErrors).length === 0 ? err.message : "Please correct the highlighted fields.");
      } else {
        setError("Couldn't create the company");
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card className="mt-6">
      <CardTitle>Add a company</CardTitle>
      <p className="mt-1 text-xs text-fg/40">
        You set up the workspace and its first admin. Calyvora activates the subscription — until then
        the company is locked.
      </p>
      {error && <Alert tone="error" className="mt-4">{error}</Alert>}
      <form onSubmit={submit} className="mt-5 flex flex-col gap-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Company name" htmlFor="companyName" error={fieldErrors.companyName}>
            <Input id="companyName" value={v.companyName} onChange={set("companyName")} />
          </Field>
          <Field label="Seats" htmlFor="seats" error={fieldErrors.seats}>
            <Input id="seats" value={v.seats} onChange={set("seats")} />
          </Field>
          <Field label="Admin first name" htmlFor="adminFirstName" error={fieldErrors.adminFirstName}>
            <Input id="adminFirstName" value={v.adminFirstName} onChange={set("adminFirstName")} />
          </Field>
          <Field label="Admin last name" htmlFor="adminLastName" error={fieldErrors.adminLastName}>
            <Input id="adminLastName" value={v.adminLastName} onChange={set("adminLastName")} />
          </Field>
          <Field label="Admin email" htmlFor="adminEmail" error={fieldErrors.adminEmail}>
            <Input id="adminEmail" type="email" value={v.adminEmail} onChange={set("adminEmail")} />
          </Field>
          <Field label="Temporary password" htmlFor="password" error={fieldErrors.password}>
            <Input id="password" value={v.password} onChange={set("password")} />
          </Field>
        </div>
        <div className="flex gap-2">
          <Button type="submit" disabled={saving}>
            {saving && <Loader2 className="h-4 w-4 animate-spin" />}
            {saving ? "Creating…" : "Create company"}
          </Button>
          <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
        </div>
      </form>
    </Card>
  );
}
