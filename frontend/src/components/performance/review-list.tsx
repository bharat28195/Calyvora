"use client";

import { useMemo, useState } from "react";
import { ChevronRight, Star } from "lucide-react";
import type { PerformanceReview, ReviewQuestion, ReviewStatus } from "@/lib/types";
import { ReviewCard } from "@/components/performance/review-card";
import { cn } from "@/lib/utils";

const FILTERS: { id: "ALL" | ReviewStatus; label: string }[] = [
  { id: "ALL", label: "Everyone" },
  { id: "PENDING_SELF", label: "Waiting on self-assessment" },
  { id: "PENDING_MANAGER", label: "Waiting on manager" },
  { id: "SUBMITTED", label: "To approve" },
  { id: "APPROVED", label: "Approved" },
];

const STATUS: Record<ReviewStatus, { label: string; tone: string }> = {
  PENDING_SELF: { label: "Self-assessment due", tone: "text-amber-700 dark:text-amber-300" },
  PENDING_MANAGER: { label: "Manager review due", tone: "text-aqua" },
  SUBMITTED: { label: "To approve", tone: "text-violet" },
  APPROVED: { label: "Approved", tone: "text-emerald-600 dark:text-emerald-400" },
  CLOSED: { label: "Closed", tone: "text-fg/40" },
};

/**
 * Many reviews as a compact list — one line a person, filterable by where each stands — opening one
 * at a time (PD-66). A cycle of three hundred reviews rendered as three hundred full cards was a page
 * nobody could find anything on.
 */
export function ReviewList({ reviews, perspective, canApprove, onChange, readOnly = false }: {
  reviews: PerformanceReview[];
  perspective: "self" | "manager";
  canApprove: boolean;
  onChange: (r: PerformanceReview) => void;
  readOnly?: boolean;
}) {
  const [filter, setFilter] = useState<"ALL" | ReviewStatus>("ALL");
  const [query, setQuery] = useState("");
  const [openId, setOpenId] = useState<string | null>(null);
  const counts = useMemo(() => {
    const c: Record<string, number> = { ALL: reviews.length };
    for (const r of reviews) c[r.status] = (c[r.status] ?? 0) + 1;
    return c;
  }, [reviews]);
  const shown = reviews.filter((r) => (filter === "ALL" || r.status === filter)
    && (!query.trim() || r.employeeName.toLowerCase().includes(query.trim().toLowerCase())));

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap gap-1">
          {FILTERS.filter((f) => f.id === "ALL" || (counts[f.id] ?? 0) > 0).map((f) => (
            <button key={f.id} onClick={() => setFilter(f.id)}
              className={cn("rounded-full px-3 py-1 text-xs", filter === f.id ? "bg-violet text-white" : "bg-fg/5 text-fg/60 hover:bg-fg/10")}>
              {f.label} <span className="tabular-nums opacity-70">{counts[f.id] ?? 0}</span>
            </button>
          ))}
        </div>
        {reviews.length > 8 && (
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Find someone…"
            className="w-48 rounded-lg border border-fg/15 bg-fg/5 px-3 py-1.5 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet" />
        )}
      </div>
      <div className="mt-3 divide-y divide-fg/5 rounded-lg border border-fg/10">
        {shown.length === 0 && <p className="px-4 py-6 text-center text-sm text-fg/45">Nobody here.</p>}
        {shown.map((r) => (
          <div key={r.id}>
            <button onClick={() => setOpenId(openId === r.id ? null : r.id)}
              className="flex w-full items-center gap-3 px-4 py-2.5 text-left hover:bg-fg/[0.03]">
              <ChevronRight className={cn("h-4 w-4 shrink-0 text-fg/35 transition-transform", openId === r.id && "rotate-90")} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{r.employeeName}</p>
                <p className="truncate text-xs text-fg/45">{r.jobTitle ?? ""}{r.managerName ? `${r.jobTitle ? " · " : ""}reviewed by ${r.managerName}` : ""}</p>
              </div>
              <Progress review={r} />
              {r.rating != null && (
                <span className="inline-flex items-center gap-0.5 text-xs text-fg/60"><Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />{r.rating}</span>
              )}
              {r.hikeType === "PERCENT" && r.hikePercent != null && <span className="text-xs text-emerald-600 dark:text-emerald-400">+{r.hikePercent}%</span>}
              <span className={cn("w-32 shrink-0 text-right text-xs font-medium", STATUS[r.status].tone)}>{STATUS[r.status].label}</span>
            </button>
            {openId === r.id && (
              <div className="border-t border-fg/5 bg-fg/[0.015] p-3">
                <ReviewCard review={r} perspective={perspective} canApprove={canApprove} onChange={onChange} readOnly={readOnly} />
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

/** How many of each side's questions are answered — at a glance, who is holding things up. */
function Progress({ review }: { review: PerformanceReview }) {
  const qs: ReviewQuestion[] = review.questions ?? [];
  if (qs.length === 0) return null;
  const selfQs = qs.filter((q) => q.audience !== "MANAGER");
  const mgrQs = qs.filter((q) => q.audience !== "SELF");
  const done = (q: ReviewQuestion, a: Record<string, { rating?: number | null; text?: string | null }> | undefined) =>
    q.kind === "RATING" ? !!a?.[q.id]?.rating : !!a?.[q.id]?.text;
  const s = selfQs.filter((q) => done(q, review.selfAnswers)).length;
  const m = mgrQs.filter((q) => done(q, review.managerAnswers)).length;
  return (
    <span className="hidden shrink-0 text-[11px] text-fg/45 sm:inline">
      self {s}/{selfQs.length} · manager {m}/{mgrQs.length}
    </span>
  );
}
