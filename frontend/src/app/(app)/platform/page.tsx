"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useRouter } from "next/navigation";
import { Loader2, Plus, XCircle, MoreHorizontal, Search } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { CompanySummary } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { money } from "@/lib/format";

const STATUS_DOT: Record<string, string> = {
  ACTIVE: "bg-emerald-400",
  TRIALING: "bg-sky-400",
  // Created by an agency and waiting on you to switch billing on.
  PENDING: "bg-amber-400",
  PAST_DUE: "bg-amber-400",
  CANCELLED: "bg-red-400",
  NONE: "bg-fg/25",
};

/** "PAST_DUE" → "Past due". */
function sentence(status: string): string {
  const s = status.replace(/_/g, " ").toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/**
 * The customer list — the platform console's home.
 *
 * <p>Requests, agencies and pricing are their own pages under Platform in the sidebar rather than
 * sections of this one, so the widest table in the app gets the full width instead of sharing its
 * row with a second navigation.
 */
export default function PlatformCompaniesPage() {
  const router = useRouter();
  const [companies, setCompanies] = useState<CompanySummary[] | null>(null);
  const [waiting, setWaiting] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  function load() {
    api.platformCompanies().then(setCompanies).catch((e) => {
      setCompanies([]);
      setError(e instanceof ApiError ? e.message : "Failed to load companies");
    });
    // Both queues, as one number. This page does not show them, but something has to say they are
    // there — a request nobody is told about is the failure the queue exists to prevent.
    Promise.all([
      api.platformSeatRequests().catch(() => []),
      api.platformTrialRequests().catch(() => []),
    ]).then(([seats, trials]) => {
      setWaiting(seats.length + trials.filter((t) => t.status === "NEW").length);
    });
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
          <h1 className="text-2xl font-semibold tracking-tight">Companies</h1>
          <p className="mt-1 text-fg/50">Every company on Orbit, and its subscription.</p>
        </div>
        <Button onClick={() => setCreating((v) => !v)}><Plus className="h-4 w-4" /> New company</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {companies === null ? (
        <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <>
          {/* One quiet strip rather than five separate boxes, each with its own border, shadow and
              coloured icon. Five tiles competed with each other and with the table below; the numbers
              are context you glance at, so they should read as one line, not five objects. */}
          <Card className="mt-6 p-0">
            <dl className="grid grid-cols-2 divide-fg/10 sm:grid-cols-3 lg:grid-cols-5 lg:divide-x">
              <Stat label="Companies" value={String(companies.length)} />
              <Stat label="Employees" value={String(totalEmployees)} />
              <Stat label="Active" value={String(active)} />
              <Stat label="Monthly revenue" value={money(mrr)} />
              <Stat label="Waiting on you" value={String(waiting)} tone={waiting > 0 ? "attention" : undefined}
                onClick={() => router.push("/platform/requests")} />
            </dl>
          </Card>

          {creating && <CreateCompanyForm onCreated={() => { setCreating(false); load(); }} onCancel={() => setCreating(false)} />}

          <CompaniesTable companies={companies} busyId={busyId} act={act} />
        </>
      )}
    </div>
  );
}

const STATUS_FILTERS = [
  { id: "all", label: "All companies" },
  { id: "active", label: "Active" },
  { id: "pending", label: "Awaiting activation" },
  { id: "ended", label: "Ended" },
  { id: "none", label: "No subscription" },
] as const;

type StatusFilter = (typeof STATUS_FILTERS)[number]["id"];

/**
 * <p>Filtering exists because the list is not all customers: signup probes and abandoned trials land
 * here too, and once a few accumulate the real accounts are hard to pick out. Filtering rather than
 * deleting, deliberately — the strip above counts everything on the platform, and hiding a row must
 * not quietly change what the platform is reported to contain.
 */
function CompaniesTable({ companies, busyId, act }: {
  companies: CompanySummary[];
  busyId: string | null;
  act: (id: string, fn: () => Promise<unknown>) => void;
}) {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<StatusFilter>("all");

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return companies.filter((c) => {
      const matchesQuery = q === ""
        || c.name.toLowerCase().includes(q)
        || c.adminEmail.toLowerCase().includes(q)
        || (c.agencyName ?? "").toLowerCase().includes(q);
      const matchesStatus =
        status === "all" ? true
          : status === "active" ? !c.locked && c.subscriptionStatus !== "NONE"
            : status === "pending" ? c.subscriptionStatus === "PENDING"
              : status === "ended" ? c.locked && c.subscriptionStatus !== "PENDING"
                : c.subscriptionStatus === "NONE";
      return matchesQuery && matchesStatus;
    });
  }, [companies, query, status]);

  const filtering = query.trim() !== "" || status !== "all";

  return (
    <>
      <div className="mt-6 flex flex-wrap items-center gap-2">
        <div className="relative min-w-[200px] flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg/30" />
          <Input value={query} onChange={(e) => setQuery(e.target.value)} className="pl-9"
            placeholder="Search company, admin email or agency" aria-label="Search companies" />
        </div>
        {/* A select rather than a row of pills: five always-visible buttons, one of them filled
            violet, drew more attention than the table they filter. */}
        <select value={status} onChange={(e) => setStatus(e.target.value as StatusFilter)}
          aria-label="Filter by subscription status"
          className="h-11 rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet">
          {STATUS_FILTERS.map((f) => (
            <option key={f.id} value={f.id}>{f.label}</option>
          ))}
        </select>
      </div>

      {filtering && (
        <p className="mt-2 text-xs text-fg/40">
          Showing {shown.length} of {companies.length}.{" "}
          <button type="button" onClick={() => { setQuery(""); setStatus("all"); }}
            className="text-violet hover:underline">Clear filters</button>
        </p>
      )}

      <Card className="mt-4 overflow-x-auto p-0">
        <table className="w-full min-w-[720px] border-collapse text-sm">
          <thead>
            <tr className="border-b border-fg/10 text-left text-xs text-fg/40">
              <th className="px-5 py-3 font-medium">Company</th>
              <th className="px-3 py-3 font-medium">Admin</th>
              <th className="px-3 py-3 font-medium">Seats</th>
              <th className="px-3 py-3 font-medium">Billing</th>
              <th className="px-3 py-3 font-medium">Subscription</th>
              <th className="px-5 py-3 text-right font-medium">{/* actions */}</th>
            </tr>
          </thead>
          <tbody>
            {shown.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-5 py-8 text-center text-fg/50">
                  {companies.length === 0 ? "No companies yet. Create your first customer." : "No company matches these filters."}
                </td>
              </tr>
            ) : shown.map((c) => (
              <tr key={c.companyId} className="border-b border-fg/5 last:border-0 hover:bg-fg/[0.02]">
                {/* Headcount and who sold it belong to the company, not to columns of their own —
                    two of the eight columns existed to hold one short phrase each. */}
                <td className="px-5 py-3">
                  <p className="font-medium">{c.name}</p>
                  <p className="text-xs text-fg/40">
                    {c.headcount} employee{c.headcount === 1 ? "" : "s"}
                    {c.agencyName ? ` · via ${c.agencyName}` : ""}
                  </p>
                </td>
                <td className="px-3 py-3">
                  <p className="text-fg/80">{c.adminName}</p>
                  <p className="text-xs text-fg/40">{c.adminEmail}</p>
                </td>
                <td className="px-3 py-3 tabular-nums">
                  <span className={cn(c.headcount > c.seats ? "text-red-400" : "text-fg/80")}>{c.headcount}</span>
                  <span className="text-fg/40"> / {c.seats}</span>
                </td>
                {/* Where the rate came from matters as much as the rate: a company on an agreed price
                    is one that publishing a new price list will NOT move. */}
                <td className="px-3 py-3">
                  <p className="tabular-nums text-fg/80">{c.monthlyRevenue != null ? money(c.monthlyRevenue) : "—"}<span className="text-xs text-fg/40">/mo</span></p>
                  <p className="text-xs text-fg/40">
                    {c.pricePerEmployee != null ? `${money(c.pricePerEmployee)}/seat` : ""}
                    {c.customPrice && <span className="text-fg/50"> · agreed</span>}
                  </p>
                </td>
                {/* Status and expiry read as one fact — "active until March" — so they share a cell.
                    A dot rather than a filled pill: seventeen coloured pills down a page is the noise
                    itself, while a dot still carries the state at a glance. */}
                <td className="px-3 py-3">
                  <p className="flex items-center gap-1.5 text-fg/80">
                    <span className={cn("h-1.5 w-1.5 shrink-0 rounded-full", STATUS_DOT[c.subscriptionStatus] ?? STATUS_DOT.NONE)} />
                    {/* PENDING is locked too, but "Ended" would be wrong — it never started. */}
                    {c.subscriptionStatus === "PENDING" ? "Awaiting activation"
                      : c.locked ? "Ended" : sentence(c.subscriptionStatus)}
                  </p>
                  {c.endsAt && !c.locked && (
                    <p className="mt-0.5 text-xs text-fg/40">
                      until {c.endsAt}
                      {c.daysLeft != null && (
                        <span className={cn(c.daysLeft <= 14 ? "text-amber-400" : "")}> · {c.daysLeft}d</span>
                      )}
                    </p>
                  )}
                </td>
                <td className="px-5 py-3">
                  <RowActions company={c} busy={busyId === c.companyId} act={act} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </>
  );
}

/**
 * One visible action plus a menu.
 *
 * <p>Six controls per row meant the widest column on the page was the one carrying the least
 * information, and the destructive "End" sat the same size and weight as changing a price. Only the
 * decision that belongs to the row's current state stays out — activate what is locked, end what is
 * running — and everything adjustable moves behind the menu.
 */
function RowActions({ company: c, busy, act }: {
  company: CompanySummary; busy: boolean; act: (id: string, fn: () => Promise<unknown>) => void;
}) {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<null | "date" | "seats" | "price">(null);
  const [at, setAt] = useState<{ top: number; left: number } | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  const MENU_WIDTH = 224; // w-56

  function openMenu() {
    const r = triggerRef.current?.getBoundingClientRect();
    if (!r) return;
    // Clamp to the viewport so a menu on the right-hand edge is not pushed off-screen.
    setAt({ top: r.bottom + 4, left: Math.max(8, Math.min(r.right - MENU_WIDTH, window.innerWidth - MENU_WIDTH - 8)) });
    setOpen(true);
  }

  // Close on an outside click or Escape — a menu that can only be closed by choosing something
  // forces a decision the person may have opened it only to reconsider. The menu is portalled, so
  // "outside" has to mean outside BOTH the trigger and the menu itself; testing only the trigger
  // would close it on mousedown before the item's click could ever land.
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      const t = e.target as Node;
      if (!triggerRef.current?.contains(t) && !menuRef.current?.contains(t)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    // Positioned against the viewport, so any scroll or resize invalidates it. Closing is honest;
    // a menu left floating beside the row it no longer belongs to invites acting on the wrong one.
    const onMove = () => setOpen(false);
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    window.addEventListener("resize", onMove);
    window.addEventListener("scroll", onMove, true);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
      window.removeEventListener("resize", onMove);
      window.removeEventListener("scroll", onMove, true);
    };
  }, [open]);

  if (editing) {
    const config = {
      date: { type: "date" as const, initial: c.endsAt ?? "", onSet: (v: string) => act(c.companyId, () => api.setCompanyEndDate(c.companyId, v)) },
      seats: { type: "number" as const, initial: String(c.seats), onSet: (v: string) => act(c.companyId, () => api.setCompanySeats(c.companyId, Number(v) || c.seats)) },
      price: { type: "number" as const, initial: String(c.pricePerEmployee ?? 100), onSet: (v: string) => act(c.companyId, () => api.setCompanyPrice(c.companyId, Number(v))) },
    }[editing];
    return (
      <InlineEditor label={editing} type={config.type} initial={config.initial} busy={busy}
        onSet={(v) => { config.onSet(v); setEditing(null); }} onCancel={() => setEditing(null)} />
    );
  }

  const items: { label: string; run: () => void; danger?: boolean }[] = [
    { label: "Extend by 12 months", run: () => act(c.companyId, () => api.renewCompanySubscription(c.companyId, 12)) },
    { label: "Change end date", run: () => setEditing("date") },
    { label: "Change seats", run: () => setEditing("seats") },
    { label: c.customPrice ? "Change agreed price" : "Agree a custom price", run: () => setEditing("price") },
  ];
  // Only offered once there is something to undo. The backend has always accepted a null price to
  // put a company back on the published list; nothing in the console could send one, so agreeing a
  // custom price was a one-way door.
  if (c.customPrice) {
    items.push({ label: "Back to standard price list", run: () => act(c.companyId, () => api.setCompanyPrice(c.companyId, null)) });
  }
  if (!c.locked) {
    items.push({ label: "End subscription", run: () => act(c.companyId, () => api.endCompanySubscription(c.companyId)), danger: true });
  }

  return (
    <div className="flex items-center justify-end gap-1.5">
      {c.locked ? (
        <Button size="sm" disabled={busy} onClick={() => act(c.companyId, () => api.renewCompanySubscription(c.companyId, 12))}>
          {c.subscriptionStatus === "PENDING" ? "Activate" : "Reactivate"}
        </Button>
      ) : (
        <Button size="sm" variant="ghost" disabled={busy} onClick={() => act(c.companyId, () => api.endCompanySubscription(c.companyId))}>
          <XCircle className="h-3.5 w-3.5" /> End
        </Button>
      )}

      <button ref={triggerRef} type="button" onClick={() => (open ? setOpen(false) : openMenu())} disabled={busy}
        aria-label={`More actions for ${c.name}`} aria-haspopup="menu" aria-expanded={open}
        className="rounded-md p-1.5 text-fg/40 transition-colors hover:bg-fg/10 hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet disabled:opacity-40">
        {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <MoreHorizontal className="h-4 w-4" />}
      </button>

      {/* Portalled to the body because the table scrolls inside `overflow-x-auto`, and an overflow
          container clips absolutely-positioned descendants — the menu would have been cut off at the
          edge of the card, or added a stray scrollbar. */}
      {open && at && createPortal(
        <div ref={menuRef} role="menu" style={{ top: at.top, left: at.left, width: MENU_WIDTH }}
          className="fixed z-50 overflow-hidden rounded-lg border border-fg/10 bg-surface p-1 shadow-xl shadow-black/20">
          {items.map((item) => (
            <button key={item.label} role="menuitem" type="button"
              onClick={() => { setOpen(false); item.run(); }}
              className={cn(
                "block w-full rounded-md px-3 py-2 text-left text-sm transition-colors",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet",
                item.danger ? "text-red-400 hover:bg-red-500/10" : "text-fg/80 hover:bg-fg/5",
              )}>
              {item.label}
            </button>
          ))}
        </div>,
        document.body,
      )}
    </div>
  );
}

/** The inline field a row switches to when you choose a "Change …" action. */
function InlineEditor({ label, type, initial, busy, onSet, onCancel }: {
  label: string; type: "date" | "number"; initial: string; busy: boolean;
  onSet: (v: string) => void; onCancel: () => void;
}) {
  const [val, setVal] = useState(initial);
  useEffect(() => setVal(initial), [initial]);
  return (
    <span className="inline-flex items-center justify-end gap-1">
      <input type={type} min={type === "number" ? 1 : undefined} value={val} autoFocus
        aria-label={`New ${label}`}
        onChange={(e) => setVal(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && val) onSet(val);
          if (e.key === "Escape") onCancel();
        }}
        className={cn("h-8 rounded-md border border-fg/15 bg-fg/5 px-2 text-sm text-fg", type === "date" ? "w-36" : "w-20")} />
      <Button size="sm" disabled={busy} onClick={() => { if (val) onSet(val); }}>Set</Button>
      <button type="button" onClick={onCancel} aria-label="Cancel" className="px-1 text-xs text-fg/40 hover:text-fg/70">×</button>
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

/**
 * One number in the summary strip.
 *
 * <p>No icon and no colour unless something is actually waiting on you. Decoration on every tile
 * spends attention evenly, which leaves nothing to spend on the one tile that has news.
 */
function Stat({ label, value, tone, onClick }: {
  label: string; value: string; tone?: "attention"; onClick?: () => void;
}) {
  const body = (
    <>
      <dt className="text-xs text-fg/50">{label}</dt>
      <dd className={cn("mt-1 text-2xl font-semibold tabular-nums", tone === "attention" ? "text-amber-400" : "text-fg")}>
        {value}
      </dd>
    </>
  );
  // A number you can act on should be reachable from the number itself, and as a real button so it
  // is keyboard-reachable rather than a div with a click handler bolted on.
  if (onClick) {
    return (
      <div>
        <button type="button" onClick={onClick}
          className="w-full px-5 py-4 text-left transition-colors hover:bg-fg/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-violet">
          {body}
        </button>
      </div>
    );
  }
  return <div className="px-5 py-4">{body}</div>;
}
