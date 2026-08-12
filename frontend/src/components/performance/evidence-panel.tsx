"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ChevronDown, Loader2, Quote, Ticket as TicketIcon, CheckCircle2, Flag } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { ContributionItem, ReviewEvidence } from "@/lib/types";
import { cn } from "@/lib/utils";

/**
 * What this person actually delivered during the review period (PD-22, D1).
 *
 * <p>The point of the panel is that nobody has to remember. It is evidence, not a score — no number
 * here feeds the rating, and the copy is careful never to imply that more items means better work.
 * Collapsed by default so the review form stays the primary thing on the page.
 */
export function EvidencePanel({ reviewId, className, onCite }: {
  reviewId: string;
  className?: string;
  /** Present only when the viewer has a narrative open to cite into. */
  onCite?: (line: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [evidence, setEvidence] = useState<ReviewEvidence | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // Fetch on first expand — a review list would otherwise fire one request per card.
  useEffect(() => {
    if (!open || evidence || loading) return;
    let cancelled = false;
    setLoading(true);
    api.reviewEvidence(reviewId)
      .then((e) => { if (!cancelled) setEvidence(e); })
      .catch((e) => { if (!cancelled) setError(e instanceof ApiError ? e.message : "Couldn't load the work record."); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [open, evidence, loading, reviewId]);

  const summary = evidence
    ? [
        evidence.work.tasksDelivered ? `${evidence.work.tasksDelivered} delivered` : null,
        evidence.work.ticketsResolved ? `${evidence.work.ticketsResolved} resolved` : null,
        evidence.work.storyPointsDelivered ? `${evidence.work.storyPointsDelivered} pts` : null,
      ].filter(Boolean).join(" · ")
    : null;

  return (
    <div className={cn("mt-4 rounded-lg border border-fg/10 bg-fg/[0.02]", className)}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-center gap-1.5 rounded-lg p-3 text-left text-sm font-medium text-fg/80 hover:bg-fg/[0.03]"
      >
        <CheckCircle2 className="h-4 w-4 shrink-0 text-aqua" />
        <span>Work delivered this period</span>
        {summary && <span className="truncate text-xs font-normal text-fg/40">— {summary}</span>}
        <ChevronDown className={cn("ml-auto h-4 w-4 shrink-0 text-fg/40 transition-transform", open && "rotate-180")} />
      </button>

      {open && (
        <div className="border-t border-fg/10 p-3">
          {loading && (
            <p className="flex items-center gap-2 text-sm text-fg/50">
              <Loader2 className="h-4 w-4 animate-spin" /> Gathering the record…
            </p>
          )}
          {error && <p className="text-sm text-red-400">{error}</p>}

          {evidence && !loading && (
            evidence.empty ? (
              <p className="text-sm text-fg/50">
                Nothing was closed against {evidence.employeeName.split(" ")[0]} in Work between{" "}
                {evidence.periodStart} and {evidence.periodEnd}. That is often true of work tracked
                elsewhere — this panel adds context, it does not measure the person.
              </p>
            ) : (
              <div className="space-y-4">
                <ItemList
                  title="Tasks completed"
                  icon={<CheckCircle2 className="h-3.5 w-3.5 text-emerald-400" />}
                  items={evidence.work.tasks}
                  onCite={onCite}
                />
                <ItemList
                  title="Tickets resolved"
                  icon={<TicketIcon className="h-3.5 w-3.5 text-aqua" />}
                  items={evidence.work.tickets}
                  onCite={onCite}
                />

                {evidence.work.sprints.length > 0 && (
                  <div>
                    <p className="mb-1.5 flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-fg/40">
                      <Flag className="h-3.5 w-3.5 text-violet" /> Sprints contributed to
                    </p>
                    <div className="space-y-1">
                      {evidence.work.sprints.map((s) => (
                        <div key={s.sprintId} className="flex items-center gap-2 text-sm">
                          <span className="min-w-0 flex-1 truncate text-fg/70">
                            {s.name}
                            {s.projectName && <span className="text-fg/35"> · {s.projectName}</span>}
                          </span>
                          <span className="shrink-0 text-xs tabular-nums text-fg/40">
                            {s.itemsDelivered} item{s.itemsDelivered === 1 ? "" : "s"}
                            {s.pointsDelivered > 0 && ` · ${s.pointsDelivered} pts`}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {evidence.attendance.daysRecorded > 0 && (
                  <p className="border-t border-fg/10 pt-3 text-xs text-fg/40">
                    Attendance over the period: {evidence.attendance.daysPresent} present
                    {evidence.attendance.daysWorkFromHome > 0 && `, ${evidence.attendance.daysWorkFromHome} from home`}
                    {evidence.attendance.daysOnLeave > 0 && `, ${evidence.attendance.daysOnLeave} on approved leave`}
                    {" "}of {evidence.attendance.daysRecorded} working days recorded. Context for the
                    conversation, not an input to the rating.
                  </p>
                )}
              </div>
            )
          )}
        </div>
      )}
    </div>
  );
}

function ItemList({ title, icon, items, onCite }: {
  title: string; icon: React.ReactNode; items: ContributionItem[]; onCite?: (line: string) => void;
}) {
  if (items.length === 0) return null;
  return (
    <div>
      <p className="mb-1.5 flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-fg/40">
        {icon} {title} ({items.length})
      </p>
      <div className="space-y-1">
        {items.map((item) => (
          <div key={`${item.ref}-${item.title}`} className="group flex items-baseline gap-2 text-sm">
            <Link
              href={item.link}
              className="shrink-0 font-mono text-xs text-violet hover:underline"
              title={item.projectName ?? undefined}
            >
              {item.ref}
            </Link>
            <span className="min-w-0 flex-1 truncate text-fg/70">{item.title}</span>
            {item.storyPoints != null && (
              <span className="shrink-0 text-xs tabular-nums text-fg/35">{item.storyPoints} pts</span>
            )}
            {item.finishedOn && (
              <span className="shrink-0 text-xs tabular-nums text-fg/35">{item.finishedOn}</span>
            )}
            {onCite && (
              <button
                type="button"
                onClick={() => onCite(`${item.ref} — ${item.title}`)}
                title="Add this line to what you're writing"
                className="shrink-0 rounded px-1.5 py-0.5 text-xs text-fg/0 transition-colors group-hover:text-fg/50 hover:!text-violet focus-visible:text-violet focus-visible:outline-none"
              >
                <span className="flex items-center gap-1"><Quote className="h-3 w-3" />Cite</span>
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
