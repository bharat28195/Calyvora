"use client";

import { useState } from "react";
import { Loader2, Star, Check, Target, CircleDollarSign, AlertTriangle } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { PerformanceReview, HikeType, ReviewStatus, ReviewQuestion, ReviewAnswer } from "@/lib/types";
import { money as fmtMoney, formatDate } from "@/lib/format";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Alert } from "@/components/ui/alert";
import { cn } from "@/lib/utils";

const STATUS_META: Record<ReviewStatus, { label: string; tone: string }> = {
  PENDING_SELF: { label: "Self-assessment due", tone: "bg-amber-500/15 text-amber-700 dark:text-amber-300" },
  PENDING_MANAGER: { label: "Manager review due", tone: "bg-aqua/15 text-aqua" },
  SUBMITTED: { label: "Awaiting approval", tone: "bg-violet/15 text-violet" },
  APPROVED: { label: "Approved", tone: "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300" },
  CLOSED: { label: "Closed", tone: "bg-fg/10 text-fg/50" },
};

const TEXTAREA = "w-full rounded-lg border border-fg/15 bg-fg/5 p-3 text-sm text-fg placeholder:text-fg/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

// Company-currency formatting (Settings → Localization); the record currency is ignored for display.
function money(_currency: string, n: number): string {
  return fmtMoney(n);
}

const asks = (q: ReviewQuestion, manager: boolean) => q.audience === "BOTH" || q.audience === (manager ? "MANAGER" : "SELF");

/**
 * One review, editable from whichever side the viewer is on (PD-66). `perspective` decides which half
 * is live: the employee answers their questions; the manager answers theirs beside the employee's,
 * gives the overall rating and proposes the outcome — hike, promotion, effective date. An admin
 * approving sees both sides' answers question by question, and approval applies the outcome.
 *
 * <p>A cycle from before questions existed has none, and keeps the free-text form it was opened with.
 */
export function ReviewCard({
  review,
  perspective,
  canApprove,
  onChange,
  showEmployeeName = perspective === "manager",
  readOnly = false,
}: {
  review: PerformanceReview;
  perspective: "self" | "manager";
  canApprove: boolean;
  onChange: (r: PerformanceReview) => void;
  showEmployeeName?: boolean;
  /** An overview: nothing editable, whoever is looking. */
  readOnly?: boolean;
}) {
  const meta = STATUS_META[review.status];
  const cycleOpen = review.cycleStatus !== "CLOSED";
  const questions = review.questions ?? [];
  const selfEditable = !readOnly && perspective === "self" && cycleOpen
    && (review.status === "PENDING_SELF" || review.status === "PENDING_MANAGER");
  const managerEditable = !readOnly && perspective === "manager" && cycleOpen && review.status !== "APPROVED"
    && review.status !== "CLOSED";

  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <CardTitle>{showEmployeeName ? review.employeeName : review.cycleName}</CardTitle>
          <p className="mt-0.5 text-sm text-fg/50">
            {showEmployeeName
              ? `${review.jobTitle ?? ""}${review.jobTitle ? " · " : ""}${review.cycleName}`
              : review.periodStart && review.periodEnd
                ? `${formatDate(review.periodStart)} → ${formatDate(review.periodEnd)}`
                : ""}
          </p>
        </div>
        <span className={cn("shrink-0 rounded-full px-2.5 py-1 text-xs font-medium", meta.tone)}>{meta.label}</span>
      </div>

      <GoalsRollup review={review} />

      {review.currentSalary != null && (
        <p className="mt-3 flex items-center gap-1.5 text-sm text-fg/60">
          <CircleDollarSign className="h-4 w-4 text-fg/40" />
          Current pay <span className="font-medium text-fg/80">{money(review.currency, review.currentSalary)}</span>/yr
        </p>
      )}

      {questions.length > 0 ? (
        <>
          {selfEditable ? <SelfQuestions review={review} onChange={onChange} />
            : !managerEditable && <SideBySide review={review} />}
          {managerEditable && <ManagerQuestions review={review} onChange={onChange} />}
          {!managerEditable && <OutcomeReadonly review={review} />}
        </>
      ) : (
        <>
          <SelfSection review={review} editable={selfEditable} onChange={onChange} />
          {managerEditable ? <ManagerSection review={review} onChange={onChange} /> : <ManagerReadonly review={review} />}
        </>
      )}

      {canApprove && review.status === "SUBMITTED" && (
        <ApproveBar review={review} onChange={onChange} />
      )}
    </Card>
  );
}

function GoalsRollup({ review }: { review: PerformanceReview }) {
  if (review.goalsTotal === 0) return null;
  return (
    <div className="mt-4 rounded-lg border border-fg/10 bg-fg/[0.02] p-3">
      <p className="flex items-center gap-1.5 text-sm font-medium text-fg/80">
        <Target className="h-4 w-4 text-violet" />
        Goals this period — {review.goalsAchieved} of {review.goalsTotal} achieved
      </p>
      <div className="mt-2 space-y-1.5">
        {review.goals.map((g) => (
          <div key={g.id} className="flex items-center gap-2 text-sm">
            <span className={cn("min-w-0 flex-1 truncate", g.status === "ACHIEVED" ? "text-fg/50 line-through" : "text-fg/70")}>
              {g.title}
            </span>
            <div className="h-1.5 w-20 shrink-0 overflow-hidden rounded-full bg-fg/10">
              <div className={cn("h-full rounded-full", g.status === "ACHIEVED" ? "bg-emerald-400" : "bg-violet")}
                style={{ width: `${g.progress}%` }} />
            </div>
            <span className="w-9 shrink-0 text-right text-xs tabular-nums text-fg/40">{g.progress}%</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ---- the question form --------------------------------------------------------------------------------

function Stars({ value, onChange, size = "h-6 w-6" }: { value: number; onChange?: (n: number) => void; size?: string }) {
  return (
    <div className="flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map((n) => onChange ? (
        <button key={n} type="button" onClick={() => onChange(n)} aria-label={`${n} of 5`}>
          <Star className={cn(size, "transition-colors", n <= value ? "fill-amber-400 text-amber-400" : "text-fg/20 hover:text-fg/40")} />
        </button>
      ) : (
        <Star key={n} className={cn(size, n <= value ? "fill-amber-400 text-amber-400" : "text-fg/20")} />
      ))}
      {value > 0 && <span className="ml-1.5 text-xs text-fg/50">{value}/5</span>}
    </div>
  );
}

function AnswerInput({ q, value, onChange }: { q: ReviewQuestion; value: ReviewAnswer | undefined; onChange: (a: ReviewAnswer) => void }) {
  return q.kind === "RATING"
    ? <Stars value={value?.rating ?? 0} onChange={(n) => onChange({ ...value, rating: n })} />
    : <textarea rows={3} value={value?.text ?? ""} onChange={(e) => onChange({ ...value, text: e.target.value })} className={TEXTAREA} />;
}

function AnswerView({ q, a }: { q: ReviewQuestion; a: ReviewAnswer | undefined }) {
  if (!a || (a.rating == null && !a.text)) return <span className="text-sm text-fg/35">—</span>;
  return q.kind === "RATING"
    ? <Stars value={a.rating ?? 0} size="h-4 w-4" />
    : <p className="whitespace-pre-wrap text-sm text-fg/75">{a.text}</p>;
}

const answered = (q: ReviewQuestion, a: ReviewAnswer | undefined) =>
  q.kind === "RATING" ? !!a?.rating : !!a?.text?.trim();

function SelfQuestions({ review, onChange }: { review: PerformanceReview; onChange: (r: PerformanceReview) => void }) {
  const mine = (review.questions ?? []).filter((q) => asks(q, false));
  const [answers, setAnswers] = useState<Record<string, ReviewAnswer>>(review.selfAnswers ?? {});
  const [busy, setBusy] = useState<"save" | "submit" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const done = mine.filter((q) => answered(q, answers[q.id])).length;

  async function persist(submit: boolean) {
    setBusy(submit ? "submit" : "save"); setError(null);
    try { onChange(await api.saveSelfAssessment(review.id, { selfAssessment: review.selfAssessment ?? "", answers, submit })); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't save"); }
    finally { setBusy(null); }
  }

  return (
    <div className="mt-5">
      <div className="flex items-baseline justify-between">
        <h4 className="text-sm font-medium text-fg/80">Your self-assessment</h4>
        <span className="text-xs text-fg/45">{done} of {mine.length} answered</span>
      </div>
      <p className="mt-0.5 text-xs text-fg/45">Your manager sees your answers next to theirs.</p>
      {error && <Alert tone="error" className="mt-2">{error}</Alert>}
      <div className="mt-3 space-y-4">
        {mine.map((q) => (
          <div key={q.id}>
            <p className="mb-1 text-sm text-fg/80">{q.text}</p>
            <AnswerInput q={q} value={answers[q.id]} onChange={(a) => setAnswers({ ...answers, [q.id]: a })} />
          </div>
        ))}
      </div>
      <div className="mt-3 flex gap-2">
        <Button size="sm" variant="secondary" disabled={busy !== null} onClick={() => persist(false)}>
          {busy === "save" && <Loader2 className="h-4 w-4 animate-spin" />} Save draft
        </Button>
        <Button size="sm" disabled={busy !== null || done < mine.length} onClick={() => persist(true)}>
          {busy === "submit" && <Loader2 className="h-4 w-4 animate-spin" />} Submit to my manager
        </Button>
      </div>
    </div>
  );
}

function ManagerQuestions({ review, onChange }: { review: PerformanceReview; onChange: (r: PerformanceReview) => void }) {
  const questions = review.questions ?? [];
  const self = review.selfAnswers ?? {};
  const theirs = questions.filter((q) => asks(q, true));
  const [answers, setAnswers] = useState<Record<string, ReviewAnswer>>(review.managerAnswers ?? {});
  const [rating, setRating] = useState<number>(review.rating ?? 0);
  const [summary, setSummary] = useState(review.summary ?? "");
  const outcome = useOutcome(review);
  const [busy, setBusy] = useState<"save" | "submit" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const done = theirs.filter((q) => answered(q, answers[q.id])).length;

  async function persist(submit: boolean) {
    setBusy(submit ? "submit" : "save"); setError(null);
    try {
      onChange(await api.saveManagerReview(review.id, {
        rating: rating || undefined, summary, answers, submit, ...outcome.payload(),
      }));
    } catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't save"); }
    finally { setBusy(null); }
  }

  return (
    <div className="mt-5 border-t border-fg/10 pt-4">
      <div className="flex items-baseline justify-between">
        <h4 className="text-sm font-medium text-fg/80">Your review</h4>
        <span className="text-xs text-fg/45">{done} of {theirs.length} answered</span>
      </div>
      {!review.selfSubmittedAt && (
        <p className="mt-1 flex items-center gap-1.5 text-xs text-amber-700 dark:text-amber-300">
          <AlertTriangle className="h-3.5 w-3.5" /> {review.employeeName} has not submitted their self-assessment yet.
        </p>
      )}
      {error && <Alert tone="error" className="mt-2">{error}</Alert>}
      <div className="mt-3 space-y-4">
        {questions.map((q) => (
          <div key={q.id} className="rounded-lg border border-fg/10 p-3">
            <p className="text-sm font-medium text-fg/80">{q.text}</p>
            <div className="mt-2 grid gap-3 sm:grid-cols-2">
              <div>
                <p className="mb-1 text-[11px] uppercase tracking-wide text-fg/40">{review.employeeName}</p>
                {asks(q, false) ? <AnswerView q={q} a={self[q.id]} /> : <span className="text-xs text-fg/35">Not asked of them</span>}
              </div>
              <div>
                <p className="mb-1 text-[11px] uppercase tracking-wide text-fg/40">You</p>
                {asks(q, true)
                  ? <AnswerInput q={q} value={answers[q.id]} onChange={(a) => setAnswers({ ...answers, [q.id]: a })} />
                  : <span className="text-xs text-fg/35">Their question only</span>}
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-4">
        <p className="text-xs text-fg/50">Overall rating</p>
        <div className="mt-1"><Stars value={rating} onChange={setRating} /></div>
      </div>
      <label className="mt-3 block text-xs text-fg/50">Overall comments</label>
      <textarea value={summary} onChange={(e) => setSummary(e.target.value)} rows={2} className={cn(TEXTAREA, "mt-1")}
        placeholder="The one paragraph they should read first" />

      <OutcomeForm review={review} outcome={outcome} />

      <div className="mt-3 flex gap-2">
        <Button size="sm" variant="secondary" disabled={busy !== null} onClick={() => persist(false)}>
          {busy === "save" && <Loader2 className="h-4 w-4 animate-spin" />} Save draft
        </Button>
        <Button size="sm" disabled={busy !== null || rating === 0 || done < theirs.length} onClick={() => persist(true)}>
          {busy === "submit" && <Loader2 className="h-4 w-4 animate-spin" />} Submit for approval
        </Button>
      </div>
      {(rating === 0 || done < theirs.length) && <p className="mt-1 text-xs text-fg/40">Answer every question and give an overall rating to submit.</p>}
    </div>
  );
}

/** Question by question, both sides — what an admin reads before approving, and what the employee sees after. */
function SideBySide({ review }: { review: PerformanceReview }) {
  const questions = review.questions ?? [];
  const self = review.selfAnswers ?? {};
  const mgr = review.managerAnswers ?? {};
  const anything = Object.keys(self).length > 0 || Object.keys(mgr).length > 0;
  if (!anything) return <p className="mt-5 text-sm text-fg/40">No answers yet.</p>;
  return (
    <div className="mt-5 overflow-x-auto">
      <table className="w-full min-w-[560px] text-sm">
        <thead className="text-left text-[11px] uppercase tracking-wide text-fg/40">
          <tr><th className="w-1/3 py-1.5 pr-3 font-medium">Question</th><th className="py-1.5 pr-3 font-medium">{review.employeeName}</th><th className="py-1.5 font-medium">{review.managerName ?? "Manager"}</th></tr>
        </thead>
        <tbody>
          {questions.map((q) => (
            <tr key={q.id} className="border-t border-fg/5 align-top">
              <td className="py-2 pr-3 text-fg/70">{q.text}</td>
              <td className="py-2 pr-3">{asks(q, false) ? <AnswerView q={q} a={self[q.id]} /> : <span className="text-xs text-fg/30">—</span>}</td>
              <td className="py-2">{asks(q, true) ? <AnswerView q={q} a={mgr[q.id]} /> : <span className="text-xs text-fg/30">—</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ---- the outcome: hike, promotion, effective date ----------------------------------------------------------

function useOutcome(review: PerformanceReview) {
  const [hikeType, setHikeType] = useState<HikeType>(review.hikeType ?? "NONE");
  const [hikePercent, setHikePercent] = useState<string>(review.hikePercent?.toString() ?? "");
  const [proposedSalary, setProposedSalary] = useState<string>(review.proposedSalary?.toString() ?? "");
  const [hikeNote, setHikeNote] = useState(review.hikeNote ?? "");
  const [newTitle, setNewTitle] = useState(review.newTitle ?? "");
  const [effectiveDate, setEffectiveDate] = useState(review.effectiveDate ?? "");
  return {
    hikeType, setHikeType, hikePercent, setHikePercent, proposedSalary, setProposedSalary, hikeNote, setHikeNote,
    newTitle, setNewTitle, effectiveDate, setEffectiveDate,
    payload: () => ({
      hikeType, hikeNote, newTitle, effectiveDate,
      hikePercent: hikeType === "PERCENT" && hikePercent ? Number(hikePercent) : undefined,
      proposedSalary: hikeType === "NEW_SALARY" && proposedSalary ? Number(proposedSalary) : undefined,
    }),
  };
}

function OutcomeForm({ review, outcome: o }: { review: PerformanceReview; outcome: ReturnType<typeof useOutcome> }) {
  const projected = o.hikeType === "PERCENT" && o.hikePercent && review.currentSalary != null
    ? review.currentSalary * (1 + Number(o.hikePercent) / 100)
    : o.hikeType === "NEW_SALARY" && o.proposedSalary ? Number(o.proposedSalary) : null;
  const pct = o.hikeType === "NEW_SALARY" && projected != null && review.currentSalary
    ? ((projected - review.currentSalary) / review.currentSalary) * 100 : null;
  return (
    <div className="mt-4 rounded-lg border border-fg/10 bg-fg/[0.02] p-3">
      <p className="text-sm font-medium text-fg/80">Outcome</p>
      <p className="mt-0.5 text-xs text-fg/40">Applied when HR approves the review.</p>
      <div className="mt-2 flex flex-wrap gap-2">
        {(["NONE", "PERCENT", "NEW_SALARY"] as HikeType[]).map((t) => (
          <button key={t} type="button" onClick={() => o.setHikeType(t)}
            className={cn("rounded-lg px-3 py-1.5 text-sm transition-colors",
              o.hikeType === t ? "bg-violet/15 font-medium text-violet" : "text-fg/60 hover:bg-fg/5")}>
            {t === "NONE" ? "No hike" : t === "PERCENT" ? "Hike by %" : "New salary"}
          </button>
        ))}
      </div>
      {o.hikeType === "PERCENT" && (
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <Input type="number" value={o.hikePercent} onChange={(e) => o.setHikePercent(e.target.value)} placeholder="10" className="w-24" min={0} step="0.5" />
          <span className="text-sm text-fg/50">% increase</span>
          {[5, 8, 10, 12, 15].map((p) => (
            <button key={p} type="button" onClick={() => o.setHikePercent(String(p))}
              className="rounded-md bg-fg/5 px-2 py-1 text-xs text-fg/60 hover:bg-fg/10">{p}%</button>
          ))}
        </div>
      )}
      {o.hikeType === "NEW_SALARY" && (
        <div className="mt-2 flex items-center gap-2">
          <Input type="number" value={o.proposedSalary} onChange={(e) => o.setProposedSalary(e.target.value)} placeholder="1500000" className="w-40" min={0} />
          <span className="text-sm text-fg/50">new annual salary</span>
        </div>
      )}
      {projected != null && review.currentSalary != null && (
        <p className="mt-2 text-sm text-fg/60">
          New pay <span className="font-medium text-emerald-600 dark:text-emerald-400">{money(review.currency, projected)}</span>/yr
          {projected > review.currentSalary && (
            <span className="text-fg/45"> (+{money(review.currency, projected - review.currentSalary)}{pct != null ? `, ${pct.toFixed(1)}%` : ""})</span>
          )}
        </p>
      )}
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1">
          <span className="text-xs text-fg/50">Promotion — new title (optional)</span>
          <Input value={o.newTitle} onChange={(e) => o.setNewTitle(e.target.value)} placeholder={review.jobTitle ? `Now ${review.jobTitle}` : "e.g. Senior Engineer"} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs text-fg/50">Effective from</span>
          <Input type="date" value={o.effectiveDate} onChange={(e) => o.setEffectiveDate(e.target.value)} />
        </label>
      </div>
      {(o.hikeType !== "NONE" || o.newTitle) && (
        <Input value={o.hikeNote} onChange={(e) => o.setHikeNote(e.target.value)} className="mt-2" placeholder="Note for HR (optional)" />
      )}
    </div>
  );
}

function OutcomeReadonly({ review }: { review: PerformanceReview }) {
  const raise = review.hikeType && review.hikeType !== "NONE";
  if (review.rating == null && !raise && !review.newTitle && !review.summary) return null;
  return (
    <div className="mt-4 rounded-lg border border-fg/10 bg-fg/[0.02] p-3 text-sm">
      {review.rating != null && <div className="flex items-center gap-2"><span className="text-fg/50">Overall</span><Stars value={review.rating} size="h-4 w-4" /></div>}
      {review.summary && <p className="mt-1.5 whitespace-pre-wrap text-fg/75">{review.summary}</p>}
      {(raise || review.newTitle) && (
        <p className="mt-1.5 text-fg/70">
          {raise && (review.hikeType === "PERCENT" ? `${review.hikePercent}% hike` : review.proposedSalary != null ? `New salary ${money(review.currency, review.proposedSalary)}` : "New salary")}
          {raise && review.newTitle && " · "}
          {review.newTitle && `Promotion to ${review.newTitle}`}
          {review.effectiveDate && ` · from ${formatDate(review.effectiveDate)}`}
          {review.hikeNote && <span className="text-fg/45"> — {review.hikeNote}</span>}
        </p>
      )}
    </div>
  );
}

// ---- the original free-text form, for cycles opened before questions ---------------------------------------

function SelfSection({ review, editable, onChange }: {
  review: PerformanceReview; editable: boolean; onChange: (r: PerformanceReview) => void;
}) {
  const [text, setText] = useState(review.selfAssessment ?? "");
  const [busy, setBusy] = useState<"save" | "submit" | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function persist(submit: boolean) {
    setBusy(submit ? "submit" : "save"); setError(null);
    try {
      const updated = await api.saveSelfAssessment(review.id, { selfAssessment: text, submit });
      onChange(updated);
    } catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't save"); }
    finally { setBusy(null); }
  }

  return (
    <div className="mt-5">
      <h4 className="text-sm font-medium text-fg/80">Self-assessment</h4>
      {error && <Alert tone="error" className="mt-2">{error}</Alert>}
      {editable ? (
        <>
          <p className="mb-2 mt-0.5 text-xs text-fg/40">What did you focus on and deliver this period? Your manager sees this.</p>
          <textarea value={text} onChange={(e) => setText(e.target.value)} rows={4} className={TEXTAREA}
            placeholder="Highlights, goals you moved, what you're proud of…" />
          <div className="mt-2 flex gap-2">
            <Button size="sm" variant="secondary" disabled={busy !== null} onClick={() => persist(false)}>
              {busy === "save" && <Loader2 className="h-4 w-4 animate-spin" />} Save draft
            </Button>
            <Button size="sm" disabled={busy !== null || !text.trim()} onClick={() => persist(true)}>
              {busy === "submit" && <Loader2 className="h-4 w-4 animate-spin" />} Submit self-assessment
            </Button>
          </div>
        </>
      ) : review.selfAssessment ? (
        <p className="mt-1 whitespace-pre-wrap text-sm text-fg/70">{review.selfAssessment}</p>
      ) : (
        <p className="mt-1 text-sm text-fg/40">Not written yet.</p>
      )}
    </div>
  );
}

function ManagerSection({ review, onChange }: { review: PerformanceReview; onChange: (r: PerformanceReview) => void }) {
  const [rating, setRating] = useState<number>(review.rating ?? 0);
  const [summary, setSummary] = useState(review.summary ?? "");
  const [strengths, setStrengths] = useState(review.strengths ?? "");
  const [improvements, setImprovements] = useState(review.improvements ?? "");
  const outcome = useOutcome(review);
  const [busy, setBusy] = useState<"save" | "submit" | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function persist(submit: boolean) {
    setBusy(submit ? "submit" : "save"); setError(null);
    try {
      onChange(await api.saveManagerReview(review.id, {
        rating: rating || undefined, summary, strengths, improvements, submit, ...outcome.payload(),
      }));
    } catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't save"); }
    finally { setBusy(null); }
  }

  return (
    <div className="mt-5 border-t border-fg/10 pt-4">
      <h4 className="text-sm font-medium text-fg/80">Manager review</h4>
      {error && <Alert tone="error" className="mt-2">{error}</Alert>}
      <div className="mt-2">
        <p className="text-xs text-fg/50">Rating</p>
        <div className="mt-1"><Stars value={rating} onChange={setRating} /></div>
      </div>
      <label className="mt-3 block text-xs text-fg/50">Overall summary</label>
      <textarea value={summary} onChange={(e) => setSummary(e.target.value)} rows={3} className={cn(TEXTAREA, "mt-1")}
        placeholder="How did they do this period?" />
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <div>
          <label className="block text-xs text-fg/50">Strengths</label>
          <textarea value={strengths} onChange={(e) => setStrengths(e.target.value)} rows={2} className={cn(TEXTAREA, "mt-1")} />
        </div>
        <div>
          <label className="block text-xs text-fg/50">Areas to grow</label>
          <textarea value={improvements} onChange={(e) => setImprovements(e.target.value)} rows={2} className={cn(TEXTAREA, "mt-1")} />
        </div>
      </div>
      <OutcomeForm review={review} outcome={outcome} />
      <div className="mt-3 flex gap-2">
        <Button size="sm" variant="secondary" disabled={busy !== null} onClick={() => persist(false)}>
          {busy === "save" && <Loader2 className="h-4 w-4 animate-spin" />} Save draft
        </Button>
        <Button size="sm" disabled={busy !== null || rating === 0} onClick={() => persist(true)}>
          {busy === "submit" && <Loader2 className="h-4 w-4 animate-spin" />} Submit for approval
        </Button>
      </div>
      {rating === 0 && <p className="mt-1 text-xs text-fg/40">Set a rating to submit.</p>}
    </div>
  );
}

function ManagerReadonly({ review }: { review: PerformanceReview }) {
  const hasContent = review.rating != null || review.summary || review.hikeType;
  return (
    <div className="mt-5 border-t border-fg/10 pt-4">
      <h4 className="text-sm font-medium text-fg/80">Manager review</h4>
      {!hasContent ? <p className="mt-1 text-sm text-fg/40">Not written yet.</p> : <>
        <div className="mt-2 grid gap-2 sm:grid-cols-2">
          {review.strengths && <ReadField label="Strengths" value={review.strengths} />}
          {review.improvements && <ReadField label="Areas to grow" value={review.improvements} />}
        </div>
        <OutcomeReadonly review={review} />
      </>}
    </div>
  );
}

function ReadField({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-fg/10 p-2">
      <p className="text-xs text-fg/40">{label}</p>
      <p className="mt-0.5 whitespace-pre-wrap text-sm text-fg/70">{value}</p>
    </div>
  );
}

// ---- approval ---------------------------------------------------------------------------------------------

function ApproveBar({ review, onChange }: { review: PerformanceReview; onChange: (r: PerformanceReview) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const raise = !!review.hikeType && review.hikeType !== "NONE";
  const changes = raise || !!review.newTitle;
  const [issueLetter, setIssueLetter] = useState(changes);
  const waiting = !review.selfSubmittedAt;

  async function approve(force: boolean) {
    setBusy(true); setError(null);
    try { onChange(await api.approveReview(review.id, { force, issueLetter: changes && issueLetter })); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't approve"); setBusy(false); }
  }

  return (
    <div className="mt-4 border-t border-fg/10 pt-4">
      {error && <Alert tone="error" className="mb-3">{error}</Alert>}
      {waiting && (
        <p className="mb-2 flex items-center gap-1.5 text-xs text-amber-700 dark:text-amber-300">
          <AlertTriangle className="h-3.5 w-3.5" /> {review.employeeName} has not submitted a self-assessment. Approving now decides without hearing from them.
        </p>
      )}
      {changes && (
        <label className="mb-3 flex items-center gap-2 text-sm text-fg/70">
          <input type="checkbox" className="accent-violet" checked={issueLetter} onChange={(e) => setIssueLetter(e.target.checked)} />
          Issue the {raise ? "increment" : "promotion"} letter on approval
        </label>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <Button size="sm" onClick={() => approve(waiting)} disabled={busy}>
          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
          {waiting ? "Approve anyway" : changes ? "Approve and apply" : "Approve review"}
        </Button>
        <p className="text-xs text-fg/40">
          {changes
            ? `Applies ${[raise && "the hike", review.newTitle && "the new title"].filter(Boolean).join(" and ")}${review.effectiveDate ? ` from ${formatDate(review.effectiveDate)}` : " from today"}.`
            : "Finalizes the review."}
        </p>
      </div>
    </div>
  );
}
