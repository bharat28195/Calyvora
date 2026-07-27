"use client";

import { useState } from "react";
import { Loader2, Star, Check, Target, CircleDollarSign } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { PerformanceReview, HikeType, ReviewStatus } from "@/lib/types";
import { money as fmtMoney } from "@/lib/format";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Alert } from "@/components/ui/alert";
import { cn } from "@/lib/utils";

const STATUS_META: Record<ReviewStatus, { label: string; tone: string }> = {
  PENDING_SELF: { label: "Self-assessment due", tone: "bg-amber-500/15 text-amber-300" },
  PENDING_MANAGER: { label: "Manager review due", tone: "bg-aqua/15 text-aqua" },
  SUBMITTED: { label: "Awaiting approval", tone: "bg-violet/15 text-violet" },
  APPROVED: { label: "Approved", tone: "bg-emerald-500/15 text-emerald-300" },
  CLOSED: { label: "Closed", tone: "bg-fg/10 text-fg/50" },
};

const TEXTAREA = "w-full rounded-lg border border-fg/15 bg-fg/5 p-3 text-sm text-fg placeholder:text-fg/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

// Company-currency formatting (Settings → Localization); the record currency is ignored for display.
function money(_currency: string, n: number): string {
  return fmtMoney(n);
}

/**
 * One review, editable from whichever side the viewer is on. `perspective` decides which half is
 * live: the member fills the self-assessment; the manager (or an admin) fills the rating and hike.
 * An admin viewing a SUBMITTED review can approve it, which applies any recommended raise to pay.
 */
export function ReviewCard({
  review,
  perspective,
  canApprove,
  onChange,
  showEmployeeName = perspective === "manager",
}: {
  review: PerformanceReview;
  perspective: "self" | "manager";
  canApprove: boolean;
  onChange: (r: PerformanceReview) => void;
  showEmployeeName?: boolean;
}) {
  const meta = STATUS_META[review.status];
  const cycleOpen = review.cycleStatus !== "CLOSED";
  const selfEditable = perspective === "self" && cycleOpen
    && (review.status === "PENDING_SELF" || review.status === "PENDING_MANAGER");
  const managerEditable = perspective === "manager" && cycleOpen && review.status !== "APPROVED"
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
                ? `${review.periodStart} → ${review.periodEnd}`
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

      <SelfSection review={review} editable={selfEditable} onChange={onChange} />
      <ManagerSection review={review} editable={managerEditable} onChange={onChange} />

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

function ManagerSection({ review, editable, onChange }: {
  review: PerformanceReview; editable: boolean; onChange: (r: PerformanceReview) => void;
}) {
  const [rating, setRating] = useState<number>(review.rating ?? 0);
  const [summary, setSummary] = useState(review.summary ?? "");
  const [strengths, setStrengths] = useState(review.strengths ?? "");
  const [improvements, setImprovements] = useState(review.improvements ?? "");
  const [hikeType, setHikeType] = useState<HikeType>(review.hikeType ?? "NONE");
  const [hikePercent, setHikePercent] = useState<string>(review.hikePercent?.toString() ?? "");
  const [proposedSalary, setProposedSalary] = useState<string>(review.proposedSalary?.toString() ?? "");
  const [hikeNote, setHikeNote] = useState(review.hikeNote ?? "");
  const [busy, setBusy] = useState<"save" | "submit" | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function persist(submit: boolean) {
    setBusy(submit ? "submit" : "save"); setError(null);
    try {
      const updated = await api.saveManagerReview(review.id, {
        rating: rating || undefined,
        summary, strengths, improvements, hikeNote,
        hikeType,
        hikePercent: hikeType === "PERCENT" && hikePercent ? Number(hikePercent) : undefined,
        proposedSalary: hikeType === "NEW_SALARY" && proposedSalary ? Number(proposedSalary) : undefined,
        submit,
      });
      onChange(updated);
    } catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't save"); }
    finally { setBusy(null); }
  }

  const projected = hikeType === "PERCENT" && hikePercent && review.currentSalary != null
    ? review.currentSalary * (1 + Number(hikePercent) / 100)
    : hikeType === "NEW_SALARY" && proposedSalary ? Number(proposedSalary) : null;

  if (!editable) return <ManagerReadonly review={review} />;

  return (
    <div className="mt-5 border-t border-fg/10 pt-4">
      <h4 className="text-sm font-medium text-fg/80">Manager review</h4>
      {error && <Alert tone="error" className="mt-2">{error}</Alert>}

      <div className="mt-2">
        <p className="text-xs text-fg/50">Rating</p>
        <div className="mt-1 flex items-center gap-1">
          {[1, 2, 3, 4, 5].map((n) => (
            <button key={n} type="button" onClick={() => setRating(n)} aria-label={`${n} star`}>
              <Star className={cn("h-6 w-6 transition-colors", n <= rating ? "fill-amber-400 text-amber-400" : "text-fg/20 hover:text-fg/40")} />
            </button>
          ))}
          {rating > 0 && <span className="ml-2 text-sm text-fg/50">{rating} of 5</span>}
        </div>
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

      {/* Hike recommendation */}
      <div className="mt-4 rounded-lg border border-fg/10 bg-fg/[0.02] p-3">
        <p className="text-sm font-medium text-fg/80">Hike recommendation</p>
        <p className="mt-0.5 text-xs text-fg/40">On approval this is applied to their compensation history.</p>
        <div className="mt-2 flex flex-wrap gap-2">
          {(["NONE", "PERCENT", "NEW_SALARY"] as HikeType[]).map((t) => (
            <button key={t} type="button" onClick={() => setHikeType(t)}
              className={cn("rounded-lg px-3 py-1.5 text-sm transition-colors",
                hikeType === t ? "bg-violet/15 font-medium text-violet" : "text-fg/60 hover:bg-fg/5")}>
              {t === "NONE" ? "No hike" : t === "PERCENT" ? "Percent" : "New salary"}
            </button>
          ))}
        </div>
        {hikeType === "PERCENT" && (
          <div className="mt-2 flex items-center gap-2">
            <Input type="number" value={hikePercent} onChange={(e) => setHikePercent(e.target.value)}
              placeholder="10" className="w-24" min={0} step="0.5" />
            <span className="text-sm text-fg/50">% increase</span>
          </div>
        )}
        {hikeType === "NEW_SALARY" && (
          <div className="mt-2 flex items-center gap-2">
            <Input type="number" value={proposedSalary} onChange={(e) => setProposedSalary(e.target.value)}
              placeholder="150000" className="w-36" min={0} />
            <span className="text-sm text-fg/50">new annual salary</span>
          </div>
        )}
        {projected != null && review.currentSalary != null && (
          <p className="mt-2 text-sm text-fg/60">
            New pay ≈ <span className="font-medium text-emerald-400">{money(review.currency, projected)}</span>/yr
            {projected > review.currentSalary && (
              <span className="text-fg/40"> (+{money(review.currency, projected - review.currentSalary)})</span>
            )}
          </p>
        )}
        {hikeType !== "NONE" && (
          <Input value={hikeNote} onChange={(e) => setHikeNote(e.target.value)} className="mt-2"
            placeholder="Note for the approver (optional)" />
        )}
      </div>

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
  if (!hasContent) {
    return (
      <div className="mt-5 border-t border-fg/10 pt-4">
        <h4 className="text-sm font-medium text-fg/80">Manager review</h4>
        <p className="mt-1 text-sm text-fg/40">Not written yet.</p>
      </div>
    );
  }
  return (
    <div className="mt-5 border-t border-fg/10 pt-4">
      <h4 className="text-sm font-medium text-fg/80">Manager review</h4>
      {review.rating != null && (
        <div className="mt-1 flex items-center gap-1">
          {[1, 2, 3, 4, 5].map((n) => (
            <Star key={n} className={cn("h-5 w-5", n <= review.rating! ? "fill-amber-400 text-amber-400" : "text-fg/20")} />
          ))}
          <span className="ml-2 text-sm text-fg/50">{review.rating} of 5</span>
        </div>
      )}
      {review.summary && <p className="mt-2 whitespace-pre-wrap text-sm text-fg/70">{review.summary}</p>}
      <div className="mt-2 grid gap-2 sm:grid-cols-2">
        {review.strengths && <ReadField label="Strengths" value={review.strengths} />}
        {review.improvements && <ReadField label="Areas to grow" value={review.improvements} />}
      </div>
      {review.hikeType && review.hikeType !== "NONE" && (
        <p className="mt-2 text-sm text-fg/70">
          <span className="text-fg/50">Hike: </span>
          {review.hikeType === "PERCENT"
            ? `${review.hikePercent}% increase`
            : `new salary ${money(review.currency, review.proposedSalary ?? 0)}`}
          {review.hikeNote && <span className="text-fg/40"> — {review.hikeNote}</span>}
        </p>
      )}
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

function ApproveBar({ review, onChange }: { review: PerformanceReview; onChange: (r: PerformanceReview) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const raise = review.hikeType && review.hikeType !== "NONE";

  async function approve() {
    setBusy(true); setError(null);
    try { onChange(await api.approveReview(review.id)); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't approve"); setBusy(false); }
  }

  return (
    <div className="mt-4 flex flex-wrap items-center gap-3 border-t border-fg/10 pt-4">
      {error && <Alert tone="error" className="w-full">{error}</Alert>}
      <Button size="sm" onClick={approve} disabled={busy}>
        {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
        {raise ? "Approve & apply hike" : "Approve review"}
      </Button>
      <p className="text-xs text-fg/40">
        {raise ? "Applies the recommended raise to their compensation history." : "Finalizes the review."}
      </p>
    </div>
  );
}
