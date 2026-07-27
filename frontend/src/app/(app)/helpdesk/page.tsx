"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, Plus, MessageSquare, ArrowRight } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { HelpdeskTicket, TicketCategory, TicketPriority, TicketStatus, RaiseTicketInput } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { STATUS_TONE, STATUS_LABEL, PRIORITY_TONE, CATEGORIES, CATEGORY_LABEL } from "@/lib/helpdesk";

/** HR Helpdesk — employees raise & track queries; HR agents run a queue. */
export default function HelpdeskPage() {
  const { me } = useSession();
  const isAgent = me?.user.role === "ADMIN" || me?.user.role === "HR" || me?.user.role === "OWNER";
  const [tab, setTab] = useState<"queue" | "mine">(isAgent ? "queue" : "mine");
  const [status, setStatus] = useState<TicketStatus | "">("");
  const [tickets, setTickets] = useState<HelpdeskTicket[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [raising, setRaising] = useState(false);

  const load = useCallback(() => {
    setTickets(null);
    const p = tab === "queue" && isAgent ? api.helpdeskQueue(status || undefined) : api.myTickets();
    p.then(setTickets).catch((e) => { setTickets([]); setError(e instanceof ApiError ? e.message : "Failed to load"); });
  }, [tab, status, isAgent]);
  useEffect(() => { load(); }, [load]);

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Helpdesk</h1>
          <p className="mt-1 text-fg/50">{isAgent ? "Employee queries — raise, assign and resolve." : "Ask HR, payroll or IT a question and track it."}</p>
        </div>
        <Button onClick={() => setRaising((v) => !v)}><Plus className="h-4 w-4" /> Raise ticket</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {raising && <RaiseForm onCreated={() => { setRaising(false); setTab("mine"); load(); }} onCancel={() => setRaising(false)} />}

      {isAgent && (
        <div className="mt-6 flex flex-wrap items-center gap-2">
          <Tabs value={tab} onChange={setTab} />
          {tab === "queue" && (
            <div className="ml-auto flex flex-wrap gap-1">
              {(["", "OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"] as const).map((s) => (
                <button key={s || "all"} onClick={() => setStatus(s)}
                  className={cn("rounded-full px-3 py-1 text-xs", status === s ? "bg-violet/15 text-violet" : "text-fg/50 hover:bg-fg/5")}>
                  {s === "" ? "All" : STATUS_LABEL[s]}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {tickets === null ? (
        <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : tickets.length === 0 ? (
        <Card className="mt-6"><p className="text-sm text-fg/50">No tickets here yet. Raise one to get started.</p></Card>
      ) : (
        <div className="mt-4 flex flex-col gap-2">
          {tickets.map((t) => (
            <Link key={t.id} href={`/helpdesk/${t.id}`}>
              <Card className="py-3 transition-colors hover:border-fg/20">
                <div className="flex items-center gap-3">
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-2">
                      <span className="truncate font-medium">{t.subject}</span>
                      <span className="shrink-0 rounded-full bg-fg/10 px-2 py-0.5 text-[11px] text-fg/60">{CATEGORY_LABEL[t.category]}</span>
                    </span>
                    <span className="mt-0.5 block text-xs text-fg/40">
                      {isAgent && tab === "queue" ? `${t.raisedByName} · ` : ""}
                      <span className={PRIORITY_TONE[t.priority]}>{t.priority.toLowerCase()}</span>
                      {t.assigneeName ? ` · ${t.assigneeName}` : ""}
                      {t.commentCount > 0 ? <> · <MessageSquare className="inline h-3 w-3" /> {t.commentCount}</> : ""}
                    </span>
                  </span>
                  <span className={cn("shrink-0 rounded-full px-2 py-0.5 text-xs font-medium", STATUS_TONE[t.status])}>{STATUS_LABEL[t.status]}</span>
                  <ArrowRight className="h-4 w-4 shrink-0 text-fg/30" />
                </div>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

function Tabs({ value, onChange }: { value: "queue" | "mine"; onChange: (v: "queue" | "mine") => void }) {
  return (
    <div className="inline-flex rounded-lg border border-fg/10 bg-fg/5 p-0.5 text-sm">
      {(["queue", "mine"] as const).map((v) => (
        <button key={v} onClick={() => onChange(v)}
          className={cn("rounded-md px-3 py-1", value === v ? "bg-violet text-white" : "text-fg/60 hover:text-fg")}>
          {v === "queue" ? "All tickets" : "My tickets"}
        </button>
      ))}
    </div>
  );
}

function RaiseForm({ onCreated, onCancel }: { onCreated: () => void; onCancel: () => void }) {
  const [f, setF] = useState<RaiseTicketInput>({ category: "HR", subject: "", description: "", priority: "MEDIUM" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const selectCls = "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!f.subject.trim()) return;
    setBusy(true); setError(null);
    try { await api.raiseTicket({ ...f, subject: f.subject.trim() }); onCreated(); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Couldn't raise the ticket"); setBusy(false); }
  }

  return (
    <Card className="mt-6">
      <CardTitle>Raise a ticket</CardTitle>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      <form onSubmit={submit} className="mt-3 grid gap-3 sm:grid-cols-2">
        <Field label="Category" htmlFor="t-cat">
          <select id="t-cat" className={selectCls} value={f.category} onChange={(e) => setF({ ...f, category: e.target.value as TicketCategory })}>
            {CATEGORIES.map((c) => <option key={c} value={c} className="bg-surface">{CATEGORY_LABEL[c]}</option>)}
          </select>
        </Field>
        <Field label="Priority" htmlFor="t-pri">
          <select id="t-pri" className={selectCls} value={f.priority} onChange={(e) => setF({ ...f, priority: e.target.value as TicketPriority })}>
            {(["LOW", "MEDIUM", "HIGH", "URGENT"] as const).map((p) => <option key={p} value={p} className="bg-surface">{p.toLowerCase()}</option>)}
          </select>
        </Field>
        <div className="sm:col-span-2"><Field label="Subject" htmlFor="t-subj"><Input id="t-subj" value={f.subject} onChange={(e) => setF({ ...f, subject: e.target.value })} placeholder="e.g. PF not reflecting in payslip" autoFocus /></Field></div>
        <div className="sm:col-span-2">
          <label className="block text-xs text-fg/50">Description</label>
          <textarea value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} rows={3}
            className="mt-1 w-full rounded-lg border border-fg/15 bg-fg/5 p-3 text-sm text-fg placeholder:text-fg/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
            placeholder="Give HR the details they'll need…" />
        </div>
        <div className="flex gap-2 sm:col-span-2">
          <Button type="submit" disabled={busy || !f.subject.trim()}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Submit ticket</Button>
          <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
        </div>
      </form>
    </Card>
  );
}
