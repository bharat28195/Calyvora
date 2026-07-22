"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2, TrendingUp, AlertTriangle, Target } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { SprintReport, Velocity } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/**
 * The sprint review in one screen: commitment vs capacity, a burndown drawn from recorded daily
 * snapshots, per-person load, and the project's velocity history.
 */
export function SprintReportView({ projectId, sprintId }: { projectId: string; sprintId: string | null }) {
  const [report, setReport] = useState<SprintReport | null>(null);
  const [velocity, setVelocity] = useState<Velocity | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setReport(null);
    if (sprintId) {
      api.sprintReport(sprintId)
        .then(setReport)
        .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load the report"));
    }
    api.velocity(projectId).then(setVelocity).catch(() => {});
  }, [projectId, sprintId]);

  if (!sprintId) {
    return <Card className="text-sm text-fg/50">Start a sprint to see its report.</Card>;
  }
  if (error) return <Alert tone="error">{error}</Alert>;
  if (!report) return <Card><Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" /></Card>;

  const overCommitted = report.capacityPoints != null && report.committedPoints > report.capacityPoints;
  const progress = report.committedPoints === 0
    ? 0 : Math.round((report.completedPoints / report.committedPoints) * 100);

  return (
    <div className="flex flex-col gap-6">
      <div className="grid gap-3 sm:grid-cols-4">
        <Tile label="Committed" value={`${report.committedPoints} pts`}
          hint={report.capacityPoints != null ? `capacity ${report.capacityPoints}` : "no capacity set"}
          tone={overCommitted ? "text-amber-400" : ""} />
        <Tile label="Completed" value={`${report.completedPoints} pts`} hint={`${progress}% of commitment`}
          tone="text-emerald-400" />
        <Tile label="Remaining" value={`${report.remainingPoints} pts`}
          hint={`${report.totalTasks - report.doneTasks} of ${report.totalTasks} tasks`} />
        <Tile label="Day" value={`${Math.min(report.daysElapsed, report.daysTotal)} / ${report.daysTotal}`}
          hint={report.status.toLowerCase()} />
      </div>

      {overCommitted && (
        <Alert tone="warning">
          <AlertTriangle className="mr-1 inline h-4 w-4" />
          Committed {report.committedPoints} points against a capacity of {report.capacityPoints}.
          Something will likely carry over.
        </Alert>
      )}
      {report.unestimatedTasks > 0 && (
        <p className="text-xs text-fg/40">
          {report.unestimatedTasks} task{report.unestimatedTasks === 1 ? " has" : "s have"} no estimate —
          the burndown only counts sized work.
        </p>
      )}

      <Card>
        <CardTitle>Burndown</CardTitle>
        <Burndown report={report} />
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardTitle>Who&apos;s carrying what</CardTitle>
          {report.byAssignee.length === 0 ? (
            <p className="mt-3 text-sm text-fg/40">Nothing assigned yet.</p>
          ) : (
            <div className="mt-3 flex flex-col gap-2.5">
              {report.byAssignee.map((m) => (
                <div key={m.employeeId}>
                  <div className="flex items-center justify-between text-sm">
                    <span className="truncate">{m.name}</span>
                    <span className="shrink-0 text-fg/50">
                      {m.donePoints}/{m.points} pts · {m.tasks} task{m.tasks === 1 ? "" : "s"}
                    </span>
                  </div>
                  <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-fg/10">
                    <div className="h-full rounded-full bg-violet"
                      style={{ width: `${m.points === 0 ? 0 : (m.donePoints / m.points) * 100}%` }} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card>
          <div className="flex items-center justify-between">
            <CardTitle>Velocity</CardTitle>
            {velocity && velocity.sprints.length > 0 && (
              <span className="inline-flex items-center gap-1 text-xs text-fg/50">
                <TrendingUp className="h-3.5 w-3.5" /> avg {velocity.averageVelocity} pts
              </span>
            )}
          </div>
          {!velocity || velocity.sprints.length === 0 ? (
            <p className="mt-3 text-sm text-fg/40">
              No completed sprints yet. Velocity appears once you finish one.
            </p>
          ) : (
            <>
              <VelocityChart velocity={velocity} />
              <p className="mt-3 inline-flex items-center gap-1.5 text-xs text-fg/50">
                <Target className="h-3.5 w-3.5 text-violet" />
                Suggested commitment for the next sprint: <strong className="text-fg">{velocity.suggestedCommitment} pts</strong>
              </p>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}

/** Two lines on one grid: the ideal slope and what actually remained, drawn as plain SVG. */
function Burndown({ report }: { report: SprintReport }) {
  const { idealPath, actualPath, maxY } = useMemo(() => {
    const pts = report.burndown;
    if (pts.length === 0) return { idealPath: "", actualPath: "", maxY: 0 };
    const top = Math.max(report.committedPoints, ...pts.map((p) => p.remainingPoints ?? 0), 1);
    const x = (i: number) => (i / Math.max(1, pts.length - 1)) * 100;
    const y = (v: number) => 100 - (v / top) * 100;

    const ideal = pts.map((p, i) => `${i === 0 ? "M" : "L"} ${x(i)} ${y(p.ideal)}`).join(" ");
    const actualPoints = pts
      .map((p, i) => ({ p, i }))
      .filter(({ p }) => p.remainingPoints !== null);
    const actual = actualPoints
      .map(({ p, i }, n) => `${n === 0 ? "M" : "L"} ${x(i)} ${y(p.remainingPoints!)}`)
      .join(" ");
    return { idealPath: ideal, actualPath: actual, maxY: top };
  }, [report]);

  if (report.burndown.length === 0) {
    return <p className="mt-3 text-sm text-fg/40">Set a start and end date on the sprint to see a burndown.</p>;
  }

  return (
    <div className="mt-3">
      <div className="relative h-44 w-full">
        <svg viewBox="0 0 100 100" preserveAspectRatio="none" className="h-full w-full">
          {[0, 25, 50, 75, 100].map((g) => (
            <line key={g} x1="0" y1={g} x2="100" y2={g} stroke="currentColor" strokeWidth="0.3"
              className="text-fg/10" />
          ))}
          <path d={idealPath} fill="none" stroke="currentColor" strokeWidth="0.8" strokeDasharray="2 2"
            className="text-fg/30" vectorEffect="non-scaling-stroke" />
          <path d={actualPath} fill="none" stroke="currentColor" strokeWidth="1.6"
            className="text-violet" vectorEffect="non-scaling-stroke" />
        </svg>
        <span className="absolute left-0 top-0 text-[10px] text-fg/30">{maxY}</span>
        <span className="absolute bottom-0 left-0 text-[10px] text-fg/30">0</span>
      </div>
      <div className="mt-1 flex justify-between text-[10px] text-fg/30">
        <span>{report.burndown[0].date.slice(5)}</span>
        <span>{report.burndown[report.burndown.length - 1].date.slice(5)}</span>
      </div>
      <div className="mt-2 flex gap-4 text-xs text-fg/40">
        <span className="inline-flex items-center gap-1">
          <span className="h-0.5 w-4 bg-violet" /> actual
        </span>
        <span className="inline-flex items-center gap-1">
          <span className="h-0.5 w-4 border-t border-dashed border-fg/40" /> ideal
        </span>
      </div>
    </div>
  );
}

function VelocityChart({ velocity }: { velocity: Velocity }) {
  const max = Math.max(1, ...velocity.sprints.map((s) => Math.max(s.committedPoints, s.completedPoints)));
  return (
    <div className="mt-4 flex items-end gap-3">
      {velocity.sprints.slice(-6).map((s) => (
        <div key={s.sprintId} className="flex min-w-0 flex-1 flex-col items-center gap-1">
          <div className="flex h-24 w-full items-end justify-center gap-1">
            <div className="w-1/3 rounded-t bg-fg/15" style={{ height: `${(s.committedPoints / max) * 100}%` }}
              title={`${s.committedPoints} committed`} />
            <div className="w-1/3 rounded-t bg-violet" style={{ height: `${(s.completedPoints / max) * 100}%` }}
              title={`${s.completedPoints} completed`} />
          </div>
          <span className="w-full truncate text-center text-[10px] text-fg/40" title={s.name}>{s.name}</span>
        </div>
      ))}
    </div>
  );
}

function Tile({ label, value, hint, tone = "" }: { label: string; value: string; hint?: string; tone?: string }) {
  return (
    <Card className="p-4">
      <p className="text-sm text-fg/50">{label}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${tone}`}>{value}</p>
      {hint && <p className="mt-0.5 text-xs text-fg/40">{hint}</p>}
    </Card>
  );
}
