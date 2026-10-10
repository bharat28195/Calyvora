"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Check, CheckCheck, ChevronRight, Inbox as InboxIcon, Loader2, X } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { AppNotification } from "@/lib/types";
import { NOTIFICATION_ICON, notificationAge } from "@/lib/notifications";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { money, formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";

/** What can be waiting on someone. Each kind comes from its own queue, which decides who sees what. */
type Kind = "LEAVE" | "ATTENDANCE" | "COMPOFF" | "EXPENSE" | "TAX" | "EXIT" | "TICKET";

/** One thing waiting on you, whatever kind it is. `decide` is set where it can be done from here. */
type Item = {
  key: string;
  kind: Kind;
  who: string;
  what: string;
  detail?: string | null;
  when: string;
  href: string;
  decide?: (approve: boolean) => Promise<unknown>;
};

const TABS: { id: "ALL" | Kind | "UPDATES"; label: string }[] = [
  { id: "ALL", label: "All" },
  { id: "LEAVE", label: "Leave" },
  { id: "ATTENDANCE", label: "Attendance" },
  { id: "COMPOFF", label: "Comp-off" },
  { id: "EXPENSE", label: "Expenses" },
  { id: "TAX", label: "Tax proofs" },
  { id: "EXIT", label: "Exits" },
  { id: "TICKET", label: "Tickets" },
  { id: "UPDATES", label: "Updates" },
];

const KIND_LABEL: Record<Kind, string> = {
  LEAVE: "Leave", ATTENDANCE: "Attendance correction", COMPOFF: "Comp-off", EXPENSE: "Expense",
  TAX: "Tax proofs", EXIT: "Exit", TICKET: "Ticket",
};

/** Never let one queue that is not yours (a 403) or slow to answer take the inbox down with it. */
async function quiet<T>(p: Promise<T>): Promise<T | null> {
  try { return await p; } catch { return null; }
}

/**
 * One inbox for everything that needs your decision (feedback items 4–5): leave, attendance
 * corrections, comp-off, expenses, tax proofs, exits and tickets, each with its own tab and count —
 * decided right here where that is a yes or a no, opened where it needs a closer look. Your
 * notifications sit in "Updates".
 *
 * <p>Built from each module's own approval queue rather than a parallel copy of them, so what
 * appears here is exactly what that person may decide, under the same rules (PD-32, PD-54).
 */
export default function InboxPage() {
  const router = useRouter();
  const [tab, setTab] = useState<"ALL" | Kind | "UPDATES">("ALL");
  const [items, setItems] = useState<Item[] | null>(null);
  const [updates, setUpdates] = useState<AppNotification[] | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [working, setWorking] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    const [leaveAll, leaveTeam, regs, compOff, expAll, expTeam, tax, tickets, exits, notes] = await Promise.all([
      quiet(api.allLeave({ status: "PENDING", size: 200 })),
      quiet(api.teamLeave(false)),
      quiet(api.pendingRegularizations()),
      quiet(api.pendingCompOff()),
      quiet(api.allExpenses({ status: "SUBMITTED" })),
      quiet(api.teamExpenses(false)),
      quiet(api.taxDeclarations()),
      quiet(api.helpdeskQueue({ status: "OPEN" })),
      quiet(api.exitRequests()),
      quiet(api.notifications(false)),
    ]);
    const out = new Map<string, Item>();
    const add = (i: Item) => out.set(i.key, i);

    for (const l of [...(leaveAll?.items ?? []), ...(leaveTeam ?? [])].filter((l) => l.status === "PENDING")) {
      add({
        key: `LEAVE:${l.id}`, kind: "LEAVE", who: l.employeeName, when: l.createdAt, href: "/people/time-off",
        what: `${l.type.charAt(0) + l.type.slice(1).toLowerCase().replace("_", " ")} leave · ${l.days} day${l.days === 1 ? "" : "s"}`,
        detail: `${formatDate(l.startDate)}${l.endDate !== l.startDate ? ` – ${formatDate(l.endDate)}` : ""}${l.reason ? ` · ${l.reason}` : ""}`,
        decide: (ok) => (ok ? api.approveLeave(l.id) : api.rejectLeave(l.id)),
      });
    }
    for (const r of (regs ?? []).filter((r) => r.status === "PENDING")) {
      add({
        key: `ATTENDANCE:${r.id}`, kind: "ATTENDANCE", who: r.employeeName, when: r.createdAt, href: "/regularizations",
        what: `Correct ${formatDate(r.date)}${r.checkIn ? ` · in ${r.checkIn.slice(0, 5)}` : ""}${r.checkOut ? ` · out ${r.checkOut.slice(0, 5)}` : ""}`,
        detail: r.reason,
        decide: (ok) => (ok ? api.approveRegularization(r.id) : api.rejectRegularization(r.id)),
      });
    }
    for (const c of (compOff ?? []).filter((c) => c.status === "PENDING")) {
      add({
        key: `COMPOFF:${c.id}`, kind: "COMPOFF", who: c.employeeName, when: c.createdAt, href: "/people/time-off",
        what: `Comp-off for working ${formatDate(c.workedOn)}`, detail: c.reason,
        decide: (ok) => api.decideCompOff(c.id, ok ? "approve" : "reject"),
      });
    }
    for (const e of [...(expAll?.claims ?? []), ...(expTeam ?? [])].filter((e) => e.status === "SUBMITTED")) {
      add({
        key: `EXPENSE:${e.id}`, kind: "EXPENSE", who: e.employeeName ?? "Someone", when: e.createdAt, href: "/expenses",
        what: `${e.title} · ${money(e.amount, e.currency)}`, detail: `Spent ${formatDate(e.spentOn)}${e.description ? ` · ${e.description}` : ""}`,
        decide: (ok) => api.decideExpense(e.id, ok ? "approve" : "reject"),
      });
    }
    for (const t of (tax ?? []).filter((t) => t.awaitingReview > 0)) {
      add({
        key: `TAX:${t.employeeId}`, kind: "TAX", who: t.employeeName, when: new Date().toISOString(),
        href: `/finance/tax/manage/${t.employeeId}`,
        what: `${t.awaitingReview} proof${t.awaitingReview === 1 ? "" : "s"} to review`, detail: "Accept, part-accept or reject each one",
      });
    }
    for (const x of exits ?? []) {
      add({
        key: `EXIT:${x.employeeId}`, kind: "EXIT", who: x.employeeName, when: x.requestedAt ?? new Date().toISOString(),
        href: "/people/exits",
        what: `Exit requested${x.lastWorkingDay ? ` · last day ${formatDate(x.lastWorkingDay)}` : ""}`,
        detail: [x.reason, x.requestedByName ? `asked by ${x.requestedByName}` : null].filter(Boolean).join(" · ") || null,
        decide: (ok) => (ok ? api.approveExit(x.employeeId) : api.rejectExit(x.employeeId)),
      });
    }
    for (const t of tickets?.items ?? []) {
      add({
        key: `TICKET:${t.id}`, kind: "TICKET", who: t.raisedByName, when: t.createdAt, href: `/helpdesk/${t.id}`,
        what: t.subject, detail: `${t.priority.charAt(0) + t.priority.slice(1).toLowerCase()} priority${t.assigneeName ? ` · with ${t.assigneeName}` : " · unassigned"}`,
      });
    }
    setItems([...out.values()].sort((a, b) => b.when.localeCompare(a.when)));
    setUpdates(notes ?? []);
    setSelected(new Set());
  }, []);

  useEffect(() => { void load(); }, [load]);

  const count = (k: "ALL" | Kind | "UPDATES") =>
    k === "UPDATES" ? (updates?.filter((n) => !n.read).length ?? 0)
      : k === "ALL" ? (items?.length ?? 0)
      : (items?.filter((i) => i.kind === k).length ?? 0);
  const shown = useMemo(() => (items ?? []).filter((i) => tab === "ALL" || i.kind === tab), [items, tab]);
  const decidable = shown.filter((i) => i.decide);

  async function decide(list: Item[], approve: boolean) {
    setWorking(new Set(list.map((i) => i.key)));
    setError(null); setNotice(null);
    let done = 0;
    const failed: string[] = [];
    for (const i of list) {
      try { await i.decide!(approve); done++; }
      catch (e) { failed.push(`${i.who}: ${e instanceof ApiError ? e.message : "could not be decided"}`); }
    }
    setWorking(new Set());
    if (done) setNotice(`${approve ? "Approved" : "Rejected"} ${done} request${done === 1 ? "" : "s"}.`);
    if (failed.length) setError(failed.join(" · "));
    await load();
  }

  async function openUpdate(n: AppNotification) {
    if (!n.read) await api.markNotificationRead(n.id).catch(() => {});
    if (n.link) router.push(n.link); else void load();
  }

  const visibleTabs = TABS.filter((t) => t.id === "ALL" || t.id === "UPDATES" || t.id === tab || count(t.id) > 0);

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Inbox</h1>
          <p className="mt-1 text-fg/50">Everything waiting on your decision, in one place.</p>
        </div>
        {tab === "UPDATES" && (updates?.some((n) => !n.read) ?? false) && (
          <Button variant="secondary" onClick={() => api.markAllNotificationsRead().then(load)}>
            <CheckCheck className="h-4 w-4" /> Mark all read
          </Button>
        )}
      </div>

      <div className="mt-6 flex gap-1 overflow-x-auto border-b border-fg/10">
        {visibleTabs.map((t) => (
          <button key={t.id} onClick={() => { setTab(t.id); setSelected(new Set()); }}
            className={cn("-mb-px inline-flex shrink-0 items-center gap-1.5 border-b-2 px-3 py-2 text-sm",
              tab === t.id ? "border-violet font-medium text-fg" : "border-transparent text-fg/50 hover:text-fg")}>
            {t.label}
            {count(t.id) > 0 && (
              <span className={cn("rounded-full px-1.5 text-[11px] tabular-nums", tab === t.id ? "bg-violet text-white" : "bg-fg/10 text-fg/60")}>{count(t.id)}</span>
            )}
          </button>
        ))}
      </div>

      {error && <Alert tone="error" className="mt-4">{error}</Alert>}
      {notice && <Alert tone="success" className="mt-4">{notice}</Alert>}

      {tab === "UPDATES" ? (
        updates === null ? <Spinner /> : updates.length === 0 ? <Empty text="No updates yet." /> : (
          <div className="mt-4 flex flex-col gap-2">
            {updates.map((n) => (
              <button key={n.id} onClick={() => void openUpdate(n)} className="text-left">
                <Card className={cn("flex items-start gap-3 p-4 transition-colors hover:border-fg/20", n.read && "opacity-60")}>
                  <span className="mt-0.5 shrink-0">{NOTIFICATION_ICON[n.type]}</span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{n.title}</p>
                    {n.body && <p className="truncate text-xs text-fg/50">{n.body}</p>}
                  </div>
                  <span className="shrink-0 text-xs text-fg/30">{notificationAge(n.createdAt)}</span>
                  {!n.read && <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-violet" />}
                </Card>
              </button>
            ))}
          </div>
        )
      ) : items === null ? <Spinner /> : shown.length === 0 ? (
        <Empty text={tab === "ALL" ? "Nothing is waiting on you. You're all caught up." : `No ${KIND_LABEL[tab as Kind].toLowerCase()} requests waiting.`} />
      ) : (
        <>
          {decidable.length > 1 && (
            <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm">
              <label className="flex items-center gap-2 text-fg/60">
                <input type="checkbox" className="accent-violet"
                  checked={decidable.every((i) => selected.has(i.key))}
                  onChange={(e) => setSelected(e.target.checked ? new Set(decidable.map((i) => i.key)) : new Set())} />
                Select all that can be decided here
              </label>
              {selected.size > 0 && (
                <div className="flex gap-2">
                  <Button size="sm" onClick={() => void decide(shown.filter((i) => selected.has(i.key)), true)}>
                    <Check className="h-4 w-4" /> Approve {selected.size}
                  </Button>
                  <Button size="sm" variant="secondary" onClick={() => void decide(shown.filter((i) => selected.has(i.key)), false)}>
                    <X className="h-4 w-4" /> Reject {selected.size}
                  </Button>
                </div>
              )}
            </div>
          )}
          <div className="mt-3 flex flex-col gap-2">
            {shown.map((i) => (
              <Card key={i.key} className="flex flex-wrap items-center gap-3 p-4">
                {i.decide && decidable.length > 1 ? (
                  <input type="checkbox" className="accent-violet" aria-label={`Select ${i.who}`}
                    checked={selected.has(i.key)}
                    onChange={(e) => setSelected((s) => { const n = new Set(s); if (e.target.checked) n.add(i.key); else n.delete(i.key); return n; })} />
                ) : null}
                <div className="min-w-0 flex-1">
                  <p className="text-sm">
                    <span className="font-medium">{i.who}</span>
                    <span className="ml-2 rounded-full bg-fg/5 px-2 py-0.5 text-[11px] text-fg/55">{KIND_LABEL[i.kind]}</span>
                  </p>
                  <p className="mt-0.5 text-sm text-fg/80">{i.what}</p>
                  {i.detail && <p className="mt-0.5 truncate text-xs text-fg/50">{i.detail}</p>}
                </div>
                <span className="text-xs text-fg/35">{notificationAge(i.when)}</span>
                {i.decide ? (
                  <div className="flex gap-1.5">
                    <Button size="sm" disabled={working.has(i.key)} onClick={() => void decide([i], true)}>
                      {working.has(i.key) ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />} Approve
                    </Button>
                    <Button size="sm" variant="secondary" disabled={working.has(i.key)} onClick={() => void decide([i], false)}>
                      <X className="h-4 w-4" /> Reject
                    </Button>
                  </div>
                ) : (
                  <Link href={i.href} className="inline-flex items-center gap-1 text-sm text-violet hover:underline">
                    Open <ChevronRight className="h-4 w-4" />
                  </Link>
                )}
              </Card>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function Spinner() {
  return <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;
}

function Empty({ text }: { text: string }) {
  return (
    <Card className="mt-6 text-center">
      <InboxIcon className="mx-auto h-8 w-8 text-fg/20" />
      <p className="mt-3 text-sm text-fg/50">{text}</p>
    </Card>
  );
}
