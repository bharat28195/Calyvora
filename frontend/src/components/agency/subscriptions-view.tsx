"use client";

import { AlertTriangle, CalendarClock, CheckCircle2, Lock, Users, Wallet } from "lucide-react";
import type { CompanySummary } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { money } from "@/lib/format";
import { cn } from "@/lib/utils";

/**
 * An agency's subscriptions, all companies in one place (PD-75): what each is on, until when, how
 * many seats it uses of how many, at what rate, and what it costs a month and a year — with the ones
 * that need doing something about (ending soon, over their seats, waiting, ended) pulled to the top.
 *
 * <p>Company-level only, like the rest of the agency console: seats and money, never an employee.
 */
const SOON_DAYS = 30;

type Health = "active" | "soon" | "pending" | "ended" | "archived";

function healthOf(c: CompanySummary): Health {
  if (c.archivedAt) return "archived";
  if (c.subscriptionStatus === "PENDING") return "pending";
  if (c.locked) return "ended";
  if (c.daysLeft != null && c.daysLeft <= SOON_DAYS) return "soon";
  return "active";
}

const HEALTH: Record<Health, { label: string; tone: string }> = {
  active: { label: "Active", tone: "bg-emerald-500/15 text-emerald-500" },
  soon: { label: "Renews soon", tone: "bg-amber-500/15 text-amber-600 dark:text-amber-400" },
  pending: { label: "Awaiting activation", tone: "bg-sky-500/15 text-sky-500" },
  ended: { label: "Ended", tone: "bg-red-500/15 text-red-400" },
  archived: { label: "Archived", tone: "bg-fg/10 text-fg/50" },
};

const ORDER: Record<Health, number> = { ended: 0, soon: 1, pending: 2, active: 3, archived: 4 };

function date(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

export function SubscriptionsView({ companies, onRequestSeats }: {
  companies: CompanySummary[];
  /** Opens the seat request for a company (the Companies tab's form). */
  onRequestSeats: (companyId: string) => void;
}) {
  const live = companies.filter((c) => !c.archivedAt);
  const rows = [...companies].sort((a, b) => ORDER[healthOf(a)] - ORDER[healthOf(b)]
    || (a.daysLeft ?? 9999) - (b.daysLeft ?? 9999) || a.name.localeCompare(b.name));

  // Money per currency, never added across currencies.
  const monthly = new Map<string, number>();
  for (const c of live) {
    const cur = c.currency || "INR";
    monthly.set(cur, (monthly.get(cur) ?? 0) + (c.monthlyRevenue ?? 0));
  }
  const seatsUsed = live.reduce((s, c) => s + c.headcount, 0);
  const seatsBought = live.reduce((s, c) => s + c.seats, 0);
  const count = (h: Health) => live.filter((c) => healthOf(c) === h).length;
  const attention = rows.filter((c) => {
    const h = healthOf(c);
    return h === "soon" || h === "ended" || h === "pending" || (!c.archivedAt && c.headcount > c.seats);
  });

  return (
    <div className="mt-6 flex flex-col gap-6">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        <Tile icon={<CheckCircle2 className="h-4 w-4 text-emerald-500" />} label="Active" value={String(count("active") + count("soon"))} />
        <Tile icon={<CalendarClock className="h-4 w-4 text-amber-500" />} label={`Renew in ${SOON_DAYS} days`} value={String(count("soon"))} />
        <Tile icon={<Lock className="h-4 w-4 text-red-400" />} label="Ended or waiting" value={String(count("ended") + count("pending"))} />
        <Tile icon={<Users className="h-4 w-4 text-aqua" />} label="Seats used" value={`${seatsUsed} / ${seatsBought}`} />
        <Tile icon={<Wallet className="h-4 w-4 text-violet" />} label="Monthly spend"
          value={monthly.size === 0 ? money(0) : [...monthly].map(([cur, v]) => money(v, cur)).join(" + ")} />
      </div>

      {attention.length > 0 && (
        <Card>
          <CardTitle className="flex items-center gap-2"><AlertTriangle className="h-4 w-4 text-amber-500" /> Needs attention</CardTitle>
          <ul className="mt-3 flex flex-col gap-2 text-sm">
            {attention.map((c) => {
              const h = healthOf(c);
              const over = c.headcount > c.seats;
              return (
                <li key={c.companyId} className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-fg/[0.03] px-3 py-2">
                  <span>
                    <b>{c.name}</b>{" "}
                    <span className="text-fg/60">
                      {h === "soon" && `renews ${date(c.endsAt)} — ${c.daysLeft} day${c.daysLeft === 1 ? "" : "s"} left`}
                      {h === "ended" && "subscription has ended — the company is locked"}
                      {h === "pending" && "waiting for Calyvora to activate it"}
                      {over && `${h === "active" ? "" : " · "}${c.headcount} people on ${c.seats} seats`}
                    </span>
                  </span>
                  {over && (
                    <button type="button" onClick={() => onRequestSeats(c.companyId)} className="text-xs font-medium text-violet hover:underline">
                      Request more seats
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
          <p className="mt-3 text-xs text-fg/45">Renewals and activations are done by Calyvora — write to sales@calyvora.in, or request seats here.</p>
        </Card>
      )}

      {/* Wide screens: one table. Phones: one card per company. */}
      <Card className="hidden p-0 md:block">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-fg/10 text-left text-xs uppercase tracking-wide text-fg/40">
              <th className="px-5 py-3 font-medium">Company</th>
              <th className="px-3 py-3 font-medium">Status</th>
              <th className="px-3 py-3 font-medium">Paid until</th>
              <th className="px-3 py-3 font-medium">Seats</th>
              <th className="px-3 py-3 font-medium">Rate</th>
              <th className="px-3 py-3 text-right font-medium">Monthly</th>
              <th className="px-5 py-3 text-right font-medium">Yearly</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr><td colSpan={7} className="px-5 py-8 text-center text-fg/50">No companies yet.</td></tr>
            ) : rows.map((c) => {
              const h = healthOf(c);
              return (
                <tr key={c.companyId} className={cn("border-b border-fg/5 last:border-0", h === "archived" && "opacity-55")}>
                  <td className="px-5 py-3 font-medium">{c.name}</td>
                  <td className="px-3 py-3"><span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", HEALTH[h].tone)}>{HEALTH[h].label}</span></td>
                  <td className="px-3 py-3">
                    <p>{date(c.endsAt)}</p>
                    {c.daysLeft != null && !c.locked && !c.archivedAt && (
                      <p className={cn("text-xs", c.daysLeft <= SOON_DAYS ? "text-amber-500" : "text-fg/40")}>{c.daysLeft} days left</p>
                    )}
                  </td>
                  <td className="px-3 py-3"><SeatBar used={c.headcount} total={c.seats} /></td>
                  <td className="px-3 py-3">
                    <p className="tabular-nums">{c.pricePerEmployee != null ? `${money(c.pricePerEmployee, c.currency)}/person` : "—"}</p>
                    <p className="text-xs text-fg/40">{c.customPrice ? "agreed rate" : "standard rate"}</p>
                  </td>
                  <td className="px-3 py-3 text-right tabular-nums">{money(c.monthlyRevenue ?? 0, c.currency)}</td>
                  <td className="px-5 py-3 text-right tabular-nums text-fg/70">{money((c.monthlyRevenue ?? 0) * 12, c.currency)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>

      <div className="flex flex-col gap-3 md:hidden">
        {rows.map((c) => {
          const h = healthOf(c);
          return (
            <Card key={c.companyId} className={cn("p-4", h === "archived" && "opacity-55")}>
              <div className="flex items-start justify-between gap-2">
                <p className="font-medium">{c.name}</p>
                <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", HEALTH[h].tone)}>{HEALTH[h].label}</span>
              </div>
              <dl className="mt-3 grid grid-cols-2 gap-x-3 gap-y-2 text-sm">
                <dt className="text-fg/45">Paid until</dt><dd>{date(c.endsAt)}</dd>
                <dt className="text-fg/45">Seats</dt><dd><SeatBar used={c.headcount} total={c.seats} /></dd>
                <dt className="text-fg/45">Rate</dt><dd>{c.pricePerEmployee != null ? `${money(c.pricePerEmployee, c.currency)}/person` : "—"}</dd>
                <dt className="text-fg/45">Monthly</dt><dd className="tabular-nums">{money(c.monthlyRevenue ?? 0, c.currency)}</dd>
              </dl>
            </Card>
          );
        })}
      </div>
    </div>
  );
}

function Tile({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <Card className="p-4">
      <div className="flex items-center gap-2 text-xs uppercase tracking-wide text-fg/40">{icon}{label}</div>
      <p className="mt-1.5 text-xl font-semibold tabular-nums tracking-tight">{value}</p>
    </Card>
  );
}

function SeatBar({ used, total }: { used: number; total: number }) {
  const pct = total > 0 ? Math.min(100, Math.round((used / total) * 100)) : 0;
  const over = used > total;
  return (
    <div className="min-w-[7rem]">
      <p className={cn("text-xs tabular-nums", over ? "font-medium text-red-400" : "text-fg/70")}>{used} of {total}</p>
      <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-fg/10">
        <div className={cn("h-full rounded-full", over ? "bg-red-400" : pct >= 90 ? "bg-amber-400" : "bg-emerald-400")} style={{ width: `${over ? 100 : pct}%` }} />
      </div>
    </div>
  );
}
