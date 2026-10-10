"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, Plus, ClipboardCheck, ChevronRight, Lock, Trash2, X } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { ReviewCycle, PerformanceReview, ReviewQuestion } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Alert } from "@/components/ui/alert";
import { ReviewList } from "@/components/performance/review-list";
import { cn } from "@/lib/utils";
import { can, canCompanyWide } from "@/lib/permissions";

/**
 * Performance cycles (Owner/Admin/HR). Open a cycle, watch it fill in, and approve reviews — approval
 * applies each recommended hike to compensation. The place to answer "who achieved what, and what
 * raise did they get" for the year.
 *
 * Performance is now a section everybody has and this is its root, so a member landing here would hit
 * a 403 on a screen the nav had just offered them. They are sent to their own goals instead.
 */
export default function PerformancePage() {
  const session = useSession();
  const router = useRouter();
  const [cycles, setCycles] = useState<ReviewCycle[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  const runsCycles = can(session.me, "PERFORMANCE_MANAGE");

  useEffect(() => {
    // Wait for the session: before it loads, "no permission" only means "not known yet".
    if (session.me && !runsCycles) router.replace("/performance/me");
  }, [session.me, runsCycles, router]);

  useEffect(() => {
    if (!runsCycles) return;
    api.reviewCycles().then(setCycles).catch((e) => {
      setCycles([]); setError(e instanceof ApiError ? e.message : "Failed to load cycles");
    });
  }, [runsCycles]);

  if (!runsCycles) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-violet" />
      </div>
    );
  }

  function upsertCycle(c: ReviewCycle) {
    setCycles((cur) => {
      const list = cur ?? [];
      return list.some((x) => x.id === c.id) ? list.map((x) => (x.id === c.id ? c : x)) : [c, ...list];
    });
  }

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Performance</h1>
          <p className="mt-1 text-fg/50">Run annual review cycles and approve raises based on what people delivered.</p>
        </div>
        <Button onClick={() => setAdding((v) => !v)}><Plus className="h-4 w-4" /> New cycle</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {adding && <NewCycleForm onCreated={(c) => { upsertCycle(c); setAdding(false); setOpenId(c.id); }} onCancel={() => setAdding(false)} />}

      {cycles === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : cycles.length === 0 ? (
        <Card className="mt-8"><p className="text-sm text-fg/50">No review cycles yet. Open one to kick off self-assessments across the company.</p></Card>
      ) : (
        <div className="mt-8 space-y-4">
          {cycles.map((c) => (
            <CycleRow key={c.id} cycle={c} open={openId === c.id}
              onToggle={() => setOpenId((id) => (id === c.id ? null : c.id))}
              onClosed={upsertCycle}
              onDeleted={() => setCycles((cur) => cur?.filter((x) => x.id !== c.id) ?? cur)} />
          ))}
        </div>
      )}
    </div>
  );
}

function NewCycleForm({ onCreated, onCancel }: { onCreated: (c: ReviewCycle) => void; onCancel: () => void }) {
  const thisYear = new Date().getFullYear();
  const [name, setName] = useState(`Annual Review ${thisYear}`);
  const [start, setStart] = useState(`${thisYear}-01-01`);
  const [end, setEnd] = useState(`${thisYear}-12-31`);
  const [questions, setQuestions] = useState<ReviewQuestion[]>(DEFAULT_QUESTIONS);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const setQ = (i: number, patch: Partial<ReviewQuestion>) => setQuestions((qs) => qs.map((q, j) => (j === i ? { ...q, ...patch } : q)));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try { onCreated(await api.createReviewCycle({ name: name.trim(), periodStart: start, periodEnd: end, questions })); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Couldn't create cycle"); setBusy(false); }
  }

  return (
    <Card className="mt-6">
      <CardTitle>New review cycle</CardTitle>
      <p className="mt-0.5 text-sm text-fg/50">Every active employee gets a review; each person&apos;s manager fills theirs in.</p>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      <form onSubmit={submit} className="mt-3 grid gap-3 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <label className="block text-xs text-fg/50">Name</label>
          <Input value={name} onChange={(e) => setName(e.target.value)} className="mt-1" placeholder="Annual Review 2026" />
        </div>
        <div>
          <label className="block text-xs text-fg/50">Period start</label>
          <Input type="date" value={start} onChange={(e) => setStart(e.target.value)} className="mt-1" />
        </div>
        <div>
          <label className="block text-xs text-fg/50">Period end</label>
          <Input type="date" value={end} onChange={(e) => setEnd(e.target.value)} className="mt-1" />
        </div>
        <div className="sm:col-span-2">
          <p className="text-xs font-medium text-fg/60">Questions</p>
          <p className="text-xs text-fg/45">Each is a 1–5 rating or a written answer, asked of the employee, the manager or both. Both see each other&apos;s answers side by side.</p>
          <div className="mt-2 space-y-2">
            {questions.map((q, i) => (
              <div key={i} className="flex flex-wrap items-center gap-2 rounded-lg border border-fg/10 p-2">
                <Input value={q.text} onChange={(e) => setQ(i, { text: e.target.value })} className="min-w-[14rem] flex-1" />
                <select value={q.kind} onChange={(e) => setQ(i, { kind: e.target.value as ReviewQuestion["kind"] })}
                  className="rounded-md border border-fg/15 bg-fg/5 px-2 py-2 text-xs text-fg">
                  <option value="RATING">Rating 1–5</option>
                  <option value="TEXT">Written</option>
                </select>
                <select value={q.audience} onChange={(e) => setQ(i, { audience: e.target.value as ReviewQuestion["audience"] })}
                  className="rounded-md border border-fg/15 bg-fg/5 px-2 py-2 text-xs text-fg">
                  <option value="BOTH">Both</option>
                  <option value="SELF">Employee only</option>
                  <option value="MANAGER">Manager only</option>
                </select>
                <button type="button" aria-label="Remove question" onClick={() => setQuestions((qs) => qs.filter((_, j) => j !== i))}
                  className="text-fg/30 hover:text-red-500"><X className="h-4 w-4" /></button>
              </div>
            ))}
            <button type="button" onClick={() => setQuestions((qs) => [...qs, { id: `q${Date.now()}`, text: "", kind: "TEXT", audience: "BOTH" }])}
              className="inline-flex items-center gap-1 text-sm text-violet hover:underline"><Plus className="h-4 w-4" /> Add a question</button>
          </div>
        </div>
        <div className="flex gap-2 sm:col-span-2">
          <Button type="submit" disabled={busy || !name.trim() || questions.every((q) => !q.text.trim())}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />} Open cycle
          </Button>
          <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
        </div>
      </form>
    </Card>
  );
}

function CycleRow({ cycle, open, onToggle, onClosed, onDeleted }: {
  cycle: ReviewCycle; open: boolean; onToggle: () => void; onClosed: (c: ReviewCycle) => void; onDeleted: () => void;
}) {
  const [reviews, setReviews] = useState<PerformanceReview[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [closing, setClosing] = useState(false);

  useEffect(() => {
    if (open && reviews === null) {
      api.cycleReviews(cycle.id).then(setReviews)
        .catch((e) => { setReviews([]); setError(e instanceof ApiError ? e.message : "Failed to load"); });
    }
  }, [open, reviews, cycle.id]);

  async function remove() {
    if (!confirm(`Delete "${cycle.name}" and its ${cycle.reviewCount} reviews? Nothing in it has been approved.`)) return;
    try { await api.deleteReviewCycle(cycle.id); onDeleted(); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't delete"); }
  }

  async function close() {
    setClosing(true);
    try { onClosed(await api.closeReviewCycle(cycle.id)); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't close"); }
    finally { setClosing(false); }
  }

  return (
    <Card>
      <button onClick={onToggle} className="flex w-full items-center gap-3 text-left">
        <ChevronRight className={cn("h-5 w-5 shrink-0 text-fg/40 transition-transform", open && "rotate-90")} />
        <ClipboardCheck className="h-5 w-5 shrink-0 text-violet" />
        <div className="min-w-0 flex-1">
          <p className="flex items-center gap-2 font-medium">
            {cycle.name}
            {cycle.status === "CLOSED" && <span className="inline-flex items-center gap-1 text-xs text-fg/40"><Lock className="h-3 w-3" /> closed</span>}
          </p>
          <p className="text-sm text-fg/50">{cycle.periodStart} → {cycle.periodEnd}</p>
        </div>
        <div className="shrink-0 text-right text-sm">
          <p className="font-medium text-fg/80">{cycle.approvedCount}/{cycle.reviewCount} approved</p>
          {cycle.submittedCount > 0 && <p className="text-xs text-amber-400">{cycle.submittedCount} awaiting you</p>}
        </div>
      </button>

      {open && (
        <div className="mt-4 border-t border-fg/10 pt-4">
          {error && <Alert tone="error" className="mb-3">{error}</Alert>}
          <div className="mb-4 flex justify-end gap-2">
            {cycle.approvedCount === 0 && (
              <Button size="sm" variant="ghost" onClick={remove}><Trash2 className="h-4 w-4 text-red-400" /> Delete</Button>
            )}
            {cycle.status === "OPEN" && (
              <Button size="sm" variant="secondary" onClick={close} disabled={closing}>
                {closing && <Loader2 className="h-4 w-4 animate-spin" />} Close cycle
              </Button>
            )}
          </div>
          {reviews === null ? (
            <div className="flex justify-center py-6"><Loader2 className="h-5 w-5 animate-spin text-violet" /></div>
          ) : (
            <ReviewList reviews={reviews} perspective="manager" canApprove
              onChange={(u) => setReviews((cur) => cur?.map((x) => (x.id === u.id ? u : x)) ?? cur)} />
          )}
        </div>
      )}
    </Card>
  );
}

/** The standard question set — the same as the server's ReviewForms.DEFAULTS, editable per cycle. */
const DEFAULT_QUESTIONS: ReviewQuestion[] = [
  { id: "achievements", text: "Key achievements this period", kind: "TEXT", audience: "BOTH" },
  { id: "goals", text: "How well were the period's goals met?", kind: "RATING", audience: "BOTH" },
  { id: "quality", text: "Ownership and quality of work", kind: "RATING", audience: "BOTH" },
  { id: "teamwork", text: "Collaboration and teamwork", kind: "RATING", audience: "BOTH" },
  { id: "strengths", text: "Strengths", kind: "TEXT", audience: "BOTH" },
  { id: "improve", text: "Areas to improve", kind: "TEXT", audience: "BOTH" },
  { id: "support", text: "Support or training wanted for the next period", kind: "TEXT", audience: "SELF" },
  { id: "readiness", text: "Readiness for more responsibility or a promotion", kind: "TEXT", audience: "MANAGER" },
];
