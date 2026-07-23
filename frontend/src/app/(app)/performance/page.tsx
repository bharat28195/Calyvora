"use client";

import { useEffect, useState } from "react";
import { Loader2, Plus, ClipboardCheck, ChevronRight, Lock } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { ReviewCycle, PerformanceReview } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Alert } from "@/components/ui/alert";
import { ReviewCard } from "@/components/performance/review-card";
import { cn } from "@/lib/utils";

/**
 * Performance cycles (Owner/Admin). Open a cycle, watch it fill in, and approve reviews — approval
 * applies each recommended hike to compensation. The place to answer "who achieved what, and what
 * raise did they get" for the year.
 */
export default function PerformancePage() {
  const [cycles, setCycles] = useState<ReviewCycle[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    api.reviewCycles().then(setCycles).catch((e) => {
      setCycles([]); setError(e instanceof ApiError ? e.message : "Failed to load cycles");
    });
  }, []);

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
              onClosed={upsertCycle} />
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
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try { onCreated(await api.createReviewCycle({ name: name.trim(), periodStart: start, periodEnd: end })); }
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
        <div className="flex gap-2 sm:col-span-2">
          <Button type="submit" disabled={busy || !name.trim()}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />} Open cycle
          </Button>
          <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
        </div>
      </form>
    </Card>
  );
}

function CycleRow({ cycle, open, onToggle, onClosed }: {
  cycle: ReviewCycle; open: boolean; onToggle: () => void; onClosed: (c: ReviewCycle) => void;
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
          {cycle.status === "OPEN" && (
            <div className="mb-4 flex justify-end">
              <Button size="sm" variant="secondary" onClick={close} disabled={closing}>
                {closing && <Loader2 className="h-4 w-4 animate-spin" />} Close cycle
              </Button>
            </div>
          )}
          {reviews === null ? (
            <div className="flex justify-center py-6"><Loader2 className="h-5 w-5 animate-spin text-violet" /></div>
          ) : (
            <div className="space-y-5">
              {reviews.map((r) => (
                <ReviewCard key={r.id} review={r} perspective="manager" canApprove
                  onChange={(u) => setReviews((cur) => cur?.map((x) => (x.id === u.id ? u : x)) ?? cur)} />
              ))}
            </div>
          )}
        </div>
      )}
    </Card>
  );
}
