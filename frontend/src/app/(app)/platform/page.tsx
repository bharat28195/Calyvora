"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  Loader2, Plus, Building2, Users, CheckCircle2, XCircle, Clock, Wallet,
  MoreHorizontal, Search, Tags, Network, Inbox,
} from "lucide-react";
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

/**
 * The console's four jobs, split by what you are doing rather than by what the data is: the customer
 * list you look at daily, the queue of things waiting on a decision, the groups that resell, and the
 * price list you change a few times a year.
 *
 * <p>They used to be one long scroll, which put the price-list editor — the rarest and most
 * consequential control here — directly above the table used every day, and buried both approval
 * queues in the middle where a request could sit unnoticed. A section also carries its pending count,
 * so nothing waiting on you depends on scrolling to find it.
 */
const SECTIONS = [
  { id: "companies", label: "Companies", icon: Building2 },
  { id: "requests", label: "Requests", icon: Inbox },
  { id: "agencies", label: "Agencies", icon: Network },
  { id: "pricing", label: "Pricing", icon: Tags },
] as const;

type Section = (typeof SECTIONS)[number]["id"];

function isSection(v: string): v is Section {
  return SECTIONS.some((s) => s.id === v);
}

/** Platform-owner (vendor) console — manage every customer company and its subscription. OWNER only. */
export default function PlatformPage() {
  const [companies, setCompanies] = useState<CompanySummary[] | null>(null);
  const [requests, setRequests] = useState<SeatRequest[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [section, setSection] = useState<Section>("companies");
  const [trialsWaiting, setTrialsWaiting] = useState(0);

  // The section lives in the URL fragment so a reload, a bookmark or a shared link lands where you
  // were. A fragment rather than a query parameter because it needs no router involvement and so no
  // Suspense boundary — this page is prerendered.
  useEffect(() => {
    const fromHash = window.location.hash.replace("#", "");
    if (isSection(fromHash)) setSection(fromHash);
  }, []);

  function go(next: Section) {
    setSection(next);
    history.replaceState(null, "", next === "companies" ? window.location.pathname : `#${next}`);
  }

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
  const waiting = requests.length + trialsWaiting;

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Platform</h1>
          <p className="mt-1 text-fg/50">Every company on Orbit, and its subscription.</p>
        </div>
        {/* Only offered where it makes sense — creating a company from the pricing list is a non-sequitur. */}
        {section === "companies" && (
          <Button onClick={() => setCreating((v) => !v)}><Plus className="h-4 w-4" /> New company</Button>
        )}
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {companies === null ? (
        <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <>
          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            <Kpi label="Companies" value={String(companies.length)} icon={<Building2 className="h-4 w-4 text-violet" />} />
            <Kpi label="Employees" value={String(totalEmployees)} icon={<Users className="h-4 w-4 text-aqua" />} />
            <Kpi label="Active" value={String(active)} icon={<CheckCircle2 className="h-4 w-4 text-emerald-400" />} />
            <Kpi label="Monthly revenue" value={money(mrr)} icon={<Wallet className="h-4 w-4 text-emerald-400" />} />
            {/* One number for everything awaiting a decision, and a way straight to it. Two separate
                counts made you work out which queue a thing was in before you could act on it. */}
            <Kpi label="Waiting on you" value={String(waiting)} onClick={() => go("requests")}
              icon={<Clock className={cn("h-4 w-4", waiting > 0 ? "text-amber-400" : "text-fg/30")} />} />
          </div>

          <div className="mt-6 flex flex-col gap-6 lg:flex-row">
            <SectionNav current={section} onSelect={go} waiting={waiting} />

            {/* min-w-0 so the wide companies table scrolls inside its own card rather than stretching
                this column and pushing the whole page sideways. */}
            <div className="min-w-0 flex-1">
              {section === "companies" && (
                <>
                  {creating && <CreateCompanyForm onCreated={() => { setCreating(false); load(); }} onCancel={() => setCreating(false)} />}
                  <CompaniesTable companies={companies} busyId={busyId} act={act} />
                </>
              )}

              {/* Kept mounted rather than unmounted so its "waiting on you" count is known before the
                  section is ever opened — a badge you must click to populate defeats its purpose. */}
              <div className={cn(section === "requests" ? "" : "hidden")}>
                <SeatRequestsCard requests={requests} busyId={busyId} act={act} />
                <TrialRequestsSection onChanged={load} onWaitingCount={setTrialsWaiting} />
              </div>

              {section === "agencies" && <AgenciesSection onChanged={load} />}

              {section === "pricing" && <PricingEditor />}
            </div>
          </div>
        </>
      )}
    </div>
  );
}

/**
 * The console's own navigation. A second nav rather than four entries in the app sidebar, because
 * these sections exist only for the vendor — putting them in the main nav would enlarge, for every
 * company admin, a menu none of them can use.
 *
 * <p>Horizontal and scrollable on a narrow screen, vertical from `lg` up.
 */
function SectionNav({ current, onSelect, waiting }: {
  current: Section; onSelect: (s: Section) => void; waiting: number;
}) {
  return (
    <nav aria-label="Platform sections"
      className="flex gap-1 overflow-x-auto border-b border-fg/10 pb-2 lg:w-48 lg:shrink-0 lg:flex-col lg:overflow-visible lg:border-b-0 lg:pb-0">
      {SECTIONS.map(({ id, label, icon: Icon }) => {
        const selected = current === id;
        return (
          <button key={id} type="button" onClick={() => onSelect(id)} aria-current={selected ? "page" : undefined}
            className={cn(
              "flex items-center gap-2.5 whitespace-nowrap rounded-lg px-3 py-2 text-sm transition-colors",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet",
              selected ? "bg-violet/15 font-medium text-violet" : "text-fg/60 hover:bg-fg/5 hover:text-fg",
            )}>
            <Icon className="h-4 w-4 shrink-0" />
            {label}
            {id === "requests" && waiting > 0 && (
              <span className="ml-auto rounded-full bg-amber-500/15 px-1.5 py-0.5 text-xs font-medium tabular-nums text-amber-300">
                {waiting}
              </span>
            )}
          </button>
        );
      })}
    </nav>
  );
}

function SeatRequestsCard({ requests, busyId, act }: {
  requests: SeatRequest[]; busyId: string | null; act: (id: string, fn: () => Promise<unknown>) => void;
}) {
  return (
    <Card>
      <CardTitle>Seat requests</CardTitle>
      <p className="mt-1 text-xs text-fg/40">Companies that have outgrown the seats they pay for.</p>
      {requests.length === 0 ? (
        <p className="mt-3 text-sm text-fg/50">Nothing waiting.</p>
      ) : (
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
      )}
    </Card>
  );
}

const STATUS_FILTERS = [
  { id: "all", label: "All" },
  { id: "active", label: "Active" },
  { id: "pending", label: "Awaiting activation" },
  { id: "ended", label: "Ended" },
  { id: "none", label: "No subscription" },
] as const;

type StatusFilter = (typeof STATUS_FILTERS)[number]["id"];

/**
 * The customer list.
 *
 * <p>Filtering exists because the list is not all customers: signup probes and abandoned trials land
 * here too, and once a few accumulate the real accounts are hard to pick out. Filtering rather than
 * deleting, deliberately — the counts above are of everything on the platform, and hiding a row must
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
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-[200px] flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg/30" />
          <Input value={query} onChange={(e) => setQuery(e.target.value)} className="pl-9"
            placeholder="Search company, admin email or agency" aria-label="Search companies" />
        </div>
        <div className="flex gap-1 overflow-x-auto rounded-lg border border-fg/10 bg-fg/5 p-0.5">
          {STATUS_FILTERS.map((f) => (
            <button key={f.id} type="button" onClick={() => setStatus(f.id)}
              className={cn(
                "whitespace-nowrap rounded-md px-3 py-1.5 text-sm transition-colors",
                status === f.id ? "bg-violet text-white" : "text-fg/60 hover:text-fg",
              )}>
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {filtering && (
        <p className="mt-2 text-xs text-fg/40">
          Showing {shown.length} of {companies.length}.{" "}
          <button type="button" onClick={() => { setQuery(""); setStatus("all"); }}
            className="text-violet hover:underline">Clear filters</button>
        </p>
      )}

      <Card className="mt-4 overflow-x-auto p-0">
        <table className="w-full min-w-[860px] border-collapse text-sm">
          <thead>
            <tr className="border-b border-fg/10 text-left text-xs uppercase tracking-wide text-fg/40">
              <th className="px-5 py-3 font-medium">Company</th>
              <th className="px-3 py-3 font-medium">Sold via</th>
              <th className="px-3 py-3 font-medium">Admin</th>
              <th className="px-3 py-3 font-medium">Seats</th>
              <th className="px-3 py-3 font-medium">Billing</th>
              <th className="px-3 py-3 font-medium">Subscription</th>
              <th className="px-3 py-3 font-medium">Ends</th>
              <th className="px-5 py-3 text-right font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {shown.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-5 py-8 text-center text-fg/50">
                  {companies.length === 0 ? "No companies yet. Create your first customer." : "No company matches these filters."}
                </td>
              </tr>
            ) : shown.map((c) => (
              <tr key={c.companyId} className="border-b border-fg/5 last:border-0 hover:bg-fg/[0.02]">
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

  const MENU_WIDTH = 208; // w-52

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
    { label: "Change price", run: () => setEditing("price") },
  ];
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

/**
 * Agencies — customers who run several companies (PD-18). Optional: a company sold direct has no
 * agency and simply appears in the companies table as "Direct".
 */
function AgenciesSection({ onChanged }: { onChanged: () => void }) {
  const [agencies, setAgencies] = useState<AgencySummary[] | null>(null);
  const [creating, setCreating] = useState(false);

  const load = () => void api.platformAgencies().then(setAgencies).catch(() => setAgencies([]));
  useEffect(() => { load(); }, []);

  return (
    <Card>
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
    <Card className="mb-4">
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

function Kpi({ label, value, icon, onClick }: {
  label: string; value: string; icon: React.ReactNode; onClick?: () => void;
}) {
  const body = (
    <>
      <div className="flex items-center gap-2 text-xs text-fg/50">{icon} {label}</div>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
    </>
  );
  // A number you can act on should be reachable from the number itself. Rendered as a real button so
  // it is keyboard-reachable, rather than a card with a click handler bolted on.
  if (onClick) {
    return (
      <Card className="p-0">
        <button type="button" onClick={onClick}
          className="w-full rounded-[inherit] px-5 py-4 text-left transition-colors hover:bg-fg/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet">
          {body}
        </button>
      </Card>
    );
  }
  return <Card className="py-4">{body}</Card>;
}
