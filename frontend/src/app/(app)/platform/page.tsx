"use client";

import { useEffect, useState } from "react";
import { Loader2, Plus, Building2, Users, CheckCircle2, XCircle, Clock, Wallet } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { AgencySummary, CompanySummary, SeatRequest } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { money } from "@/lib/format";
import { PricingEditor } from "@/components/platform/pricing-editor";
import { TrialRequestsSection } from "@/components/platform/trial-requests";

const STATUS_TONE: Record<string, string> = {
  ACTIVE: "bg-emerald-500/15 text-emerald-400",
  TRIALING: "bg-sky-500/15 text-sky-400",
  // Created by an agency and waiting on you to switch billing on.
  PENDING: "bg-amber-500/15 text-amber-300",
  PAST_DUE: "bg-amber-500/15 text-amber-300",
  CANCELLED: "bg-red-500/15 text-red-400",
  NONE: "bg-fg/10 text-fg/50",
};

/** Platform-owner (vendor) console — manage every customer company and its subscription. OWNER only. */
export default function PlatformPage() {
  const [companies, setCompanies] = useState<CompanySummary[] | null>(null);
  const [requests, setRequests] = useState<SeatRequest[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  function load() {
    api.platformCompanies().then(setCompanies).catch((e) => { setCompanies([]); setError(e instanceof ApiError ? e.message : "Failed to load companies"); });
    api.platformSeatRequests().then(setRequests).catch(() => setRequests([]));
  }
  useEffect(() => { load(); }, []);

  async function act(id: string, fn: () => Promise<unknown>) {
    setBusyId(id); setError(null);
    try { await fn(); load(); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Action failed"); }
    finally { setBusyId(null); }
  }

  const totalEmployees = companies?.reduce((s, c) => s + c.headcount, 0) ?? 0;
  const active = companies?.filter((c) => !c.locked).length ?? 0;
  const mrr = companies?.reduce((s, c) => s + (c.monthlyRevenue ?? 0), 0) ?? 0;

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Platform</h1>
          <p className="mt-1 text-fg/50">Every company on Priority HR, and its subscription.</p>
        </div>
        <Button onClick={() => setCreating((v) => !v)}><Plus className="h-4 w-4" /> New company</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {creating && <CreateCompanyForm onCreated={() => { setCreating(false); load(); }} onCancel={() => setCreating(false)} />}

      {companies === null ? (
        <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <>
          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            <Kpi label="Companies" value={String(companies.length)} icon={<Building2 className="h-4 w-4 text-violet" />} />
            <Kpi label="Employees" value={String(totalEmployees)} icon={<Users className="h-4 w-4 text-aqua" />} />
            <Kpi label="Active" value={String(active)} icon={<CheckCircle2 className="h-4 w-4 text-emerald-400" />} />
            <Kpi label="Monthly revenue" value={money(mrr)} icon={<Wallet className="h-4 w-4 text-emerald-400" />} />
            <Kpi label="Seat requests" value={String(requests.length)} icon={<Clock className="h-4 w-4 text-amber-400" />} />
          </div>

          {requests.length > 0 && (
            <Card className="mt-6">
              <CardTitle>Seat requests</CardTitle>
              <div className="mt-3 flex flex-col divide-y divide-fg/5">
                {requests.map((r) => (
                  <div key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                    <div className="min-w-0">
                      <p className="text-sm font-medium">{r.companyName} · {r.currentSeats} → {r.requestedSeats} seats</p>
                      {r.note && <p className="truncate text-xs text-fg/40">{r.note}</p>}
                    </div>
                    <div className="flex gap-2">
                      <Button size="sm" disabled={busyId === r.id} onClick={() => act(r.id, () => api.approveSeatRequest(r.id))}>Approve</Button>
                      <Button size="sm" variant="ghost" disabled={busyId === r.id} onClick={() => act(r.id, () => api.declineSeatRequest(r.id))}>Decline</Button>
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          )}

          <TrialRequestsSection onChanged={load} />

          <AgenciesSection onChanged={load} />

          <PricingEditor />

          <Card className="mt-6 overflow-x-auto p-0">
            <table className="w-full min-w-[980px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-fg/10 text-left text-xs uppercase tracking-wide text-fg/40">
                  <th className="px-5 py-3 font-medium">Company</th>
                  <th className="px-3 py-3 font-medium">Sold via</th>
                  <th className="px-3 py-3 font-medium">Admin</th>
                  <th className="px-3 py-3 font-medium">Seats</th>
                  <th className="px-3 py-3 font-medium">Billing</th>
                  <th className="px-3 py-3 font-medium">Subscription</th>
                  <th className="px-3 py-3 font-medium">Ends</th>
                  <th className="px-5 py-3 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {companies.length === 0 ? (
                  <tr><td colSpan={8} className="px-5 py-8 text-center text-fg/50">No companies yet. Create your first customer.</td></tr>
                ) : companies.map((c) => (
                  <tr key={c.companyId} className="border-b border-fg/5 last:border-0">
                    <td className="px-5 py-3">
                      <p className="font-medium">{c.name}</p>
                      <p className="text-xs text-fg/40">{c.headcount} employee{c.headcount === 1 ? "" : "s"}</p>
                    </td>
                    {/* Direct sale or through a group — the two ways a company gets here. */}
                    <td className="px-3 py-3">
                      {c.agencyName
                        ? <span className="rounded-full bg-violet/15 px-2 py-0.5 text-xs font-medium text-violet">{c.agencyName}</span>
                        : <span className="text-xs text-fg/40">Direct</span>}
                    </td>
                    <td className="px-3 py-3">
                      <p className="text-fg/80">{c.adminName}</p>
                      <p className="text-xs text-fg/40">{c.adminEmail}</p>
                    </td>
                    <td className="px-3 py-3 tabular-nums">
                      <span className={cn(c.headcount > c.seats ? "text-red-400" : "text-fg/80")}>{c.headcount}</span>
                      <span className="text-fg/40"> / {c.seats}</span>
                    </td>
                    <td className="px-3 py-3">
                      <p className="tabular-nums text-fg/80">{c.monthlyRevenue != null ? money(c.monthlyRevenue) : "—"}<span className="text-xs text-fg/40">/mo</span></p>
                      <p className="text-xs text-fg/40">{c.pricePerEmployee != null ? `${money(c.pricePerEmployee)}/seat` : ""}</p>
                    </td>
                    <td className="px-3 py-3">
                      <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", STATUS_TONE[c.subscriptionStatus] ?? STATUS_TONE.NONE)}>
                        {/* PENDING is locked too, but "Ended" would be wrong — it never started. */}
                        {c.subscriptionStatus === "PENDING" ? "awaiting activation"
                          : c.locked ? "Ended" : c.subscriptionStatus.toLowerCase()}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-fg/70">
                      {c.endsAt ?? "—"}
                      {c.daysLeft != null && !c.locked && (
                        <span className={cn("ml-1 text-xs", c.daysLeft <= 14 ? "text-amber-400" : "text-fg/40")}>
                          ({c.daysLeft}d)
                        </span>
                      )}
                    </td>
                    <td className="px-5 py-3">
                      <div className="flex flex-wrap gap-1.5">
                        {c.locked ? (
                          <Button size="sm" disabled={busyId === c.companyId} onClick={() => act(c.companyId, () => api.renewCompanySubscription(c.companyId, 12))}>
                            {c.subscriptionStatus === "PENDING" ? "Activate" : "Reactivate"}
                          </Button>
                        ) : (
                          <Button size="sm" variant="ghost" disabled={busyId === c.companyId} onClick={() => act(c.companyId, () => api.endCompanySubscription(c.companyId))}><XCircle className="h-3.5 w-3.5" /> End</Button>
                        )}
                        <Button size="sm" variant="ghost" disabled={busyId === c.companyId} onClick={() => act(c.companyId, () => api.renewCompanySubscription(c.companyId, 12))}>+12mo</Button>
                        <InlineEditor label="Date" type="date" initial={c.endsAt ?? ""} busy={busyId === c.companyId}
                          onSet={(v) => act(c.companyId, () => api.setCompanyEndDate(c.companyId, v))} />
                        <InlineEditor label="Seats" type="number" initial={String(c.seats)} busy={busyId === c.companyId}
                          onSet={(v) => act(c.companyId, () => api.setCompanySeats(c.companyId, Number(v) || c.seats))} />
                        <InlineEditor label="Price" type="number" initial={String(c.pricePerEmployee ?? 100)} busy={busyId === c.companyId}
                          onSet={(v) => act(c.companyId, () => api.setCompanyPrice(c.companyId, Number(v)))} />
                      </div>
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

/**
 * Agencies — customers who run several companies (PD-18). Optional: a company sold direct has no
 * agency and simply appears in the table below as "Direct". This section only exists once you sell
 * to a group, so the console stays quiet for the common case.
 */
function AgenciesSection({ onChanged }: { onChanged: () => void }) {
  const [agencies, setAgencies] = useState<AgencySummary[] | null>(null);
  const [creating, setCreating] = useState(false);

  const load = () => void api.platformAgencies().then(setAgencies).catch(() => setAgencies([]));
  useEffect(() => { load(); }, []);

  return (
    <Card className="mt-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <CardTitle>Agencies</CardTitle>
          <p className="mt-1 text-xs text-fg/40">
            Groups that run several companies. They provision their own companies; you decide when
            billing starts.
          </p>
        </div>
        <Button size="sm" variant="secondary" onClick={() => setCreating((v) => !v)}>
          <Plus className="h-4 w-4" /> New agency
        </Button>
      </div>

      {creating && (
        <CreateAgencyForm
          onCreated={() => { setCreating(false); load(); onChanged(); }}
          onCancel={() => setCreating(false)}
        />
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
                <span className="tabular-nums text-emerald-400">{money(a.monthlyRevenue ?? 0)}/mo</span>
              </div>
            </div>
          ))}
        </div>
      )}
      {agencies && agencies.length === 0 && !creating && (
        <p className="mt-3 text-sm text-fg/50">
          No agencies yet — every company is sold direct.
        </p>
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

/** A compact "click a label → inline field → Set" editor, reused for end-date, seats and price. */
function InlineEditor({ label, type, initial, busy, onSet }: {
  label: string; type: "date" | "number"; initial: string; busy: boolean; onSet: (v: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [val, setVal] = useState(initial);
  useEffect(() => setVal(initial), [initial]);
  if (!open) return <Button size="sm" variant="ghost" onClick={() => setOpen(true)}>{label}</Button>;
  return (
    <span className="inline-flex items-center gap-1">
      <input type={type} min={type === "number" ? 1 : undefined} value={val} onChange={(e) => setVal(e.target.value)}
        className={cn("h-8 rounded-md border border-fg/15 bg-fg/5 px-2 text-sm text-fg", type === "date" ? "w-36" : "w-20")} />
      <Button size="sm" disabled={busy} onClick={() => { if (val) onSet(val); setOpen(false); }}>Set</Button>
      <button type="button" onClick={() => setOpen(false)} className="text-xs text-fg/40 hover:text-fg/70">×</button>
    </span>
  );
}

function CreateCompanyForm({ onCreated, onCancel }: { onCreated: () => void; onCancel: () => void }) {
  const [f, setF] = useState({ companyName: "", adminFirstName: "", adminLastName: "", adminEmail: "", password: "demopass123", seats: "10", months: "12" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!f.companyName.trim() || !f.adminEmail.trim()) return;
    setBusy(true); setError(null);
    try {
      await api.createCompany({
        companyName: f.companyName.trim(), adminFirstName: f.adminFirstName.trim() || "Admin",
        adminLastName: f.adminLastName.trim() || "User", adminEmail: f.adminEmail.trim(),
        password: f.password, seats: Number(f.seats) || 5, months: Number(f.months) || 12,
      });
      onCreated();
    } catch (err) { setError(err instanceof ApiError ? err.message : "Couldn't create the company"); setBusy(false); }
  }

  return (
    <Card className="mt-6">
      <CardTitle>New company</CardTitle>
      <p className="mt-1 text-sm text-fg/50">Provisions the company and its first admin. Share the login with them.</p>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      <form onSubmit={submit} className="mt-3 grid gap-3 sm:grid-cols-2">
        <div className="sm:col-span-2"><Field label="Company name" htmlFor="c-name"><Input id="c-name" value={f.companyName} onChange={set("companyName")} placeholder="e.g. Acme Logistics" autoFocus /></Field></div>
        <Field label="Admin first name" htmlFor="c-first"><Input id="c-first" value={f.adminFirstName} onChange={set("adminFirstName")} /></Field>
        <Field label="Admin last name" htmlFor="c-last"><Input id="c-last" value={f.adminLastName} onChange={set("adminLastName")} /></Field>
        <div className="sm:col-span-2"><Field label="Admin email" htmlFor="c-email"><Input id="c-email" type="email" value={f.adminEmail} onChange={set("adminEmail")} placeholder="admin@company.com" /></Field></div>
        <Field label="Temp password" htmlFor="c-pw"><Input id="c-pw" value={f.password} onChange={set("password")} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Seats" htmlFor="c-seats"><Input id="c-seats" type="number" min={1} value={f.seats} onChange={set("seats")} /></Field>
          <Field label="Months" htmlFor="c-months"><Input id="c-months" type="number" min={1} value={f.months} onChange={set("months")} /></Field>
        </div>
        <div className="flex gap-2 sm:col-span-2">
          <Button type="submit" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Create company</Button>
          <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
        </div>
      </form>
    </Card>
  );
}

function Kpi({ label, value, icon }: { label: string; value: string; icon: React.ReactNode }) {
  return (
    <Card className="py-4">
      <div className="flex items-center gap-2 text-xs text-fg/50">{icon} {label}</div>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
    </Card>
  );
}
