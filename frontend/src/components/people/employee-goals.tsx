"use client";

import { useEffect, useState } from "react";
import { Loader2, Target, Plus, Check, Trash2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Goal } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

/** Employee goals with progress (feedback C8). Editable by admins or the goal owner (`canEdit`). */
export function EmployeeGoals({ employeeId, canEdit }: { employeeId: string; canEdit: boolean }) {
  const [goals, setGoals] = useState<Goal[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [title, setTitle] = useState("");
  const [target, setTarget] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.employeeGoals(employeeId).then(setGoals).catch(() => setGoals([]));
  }, [employeeId]);

  async function addGoal(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) return;
    setBusy(true); setError(null);
    try {
      const g = await api.createGoal(employeeId, { title: title.trim(), targetDate: target || undefined });
      setGoals((gs) => [g, ...(gs ?? [])]);
      setTitle(""); setTarget(""); setAdding(false);
    } catch (err) { setError(err instanceof ApiError ? err.message : "Failed to add goal"); }
    finally { setBusy(false); }
  }

  async function patch(goalId: string, p: Partial<Goal>) {
    setGoals((gs) => gs?.map((g) => (g.id === goalId ? { ...g, ...p } : g)) ?? null); // optimistic
    try {
      const updated = await api.updateGoal(employeeId, goalId, p);
      setGoals((gs) => gs?.map((g) => (g.id === goalId ? updated : g)) ?? null);
    } catch (err) { setError(err instanceof ApiError ? err.message : "Failed to update"); }
  }

  async function remove(goalId: string) {
    setGoals((gs) => gs?.filter((g) => g.id !== goalId) ?? null);
    try { await api.deleteGoal(employeeId, goalId); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Failed to delete"); }
  }

  return (
    <div className="mt-6">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="flex items-center gap-1.5 text-sm font-medium text-fg/80">
          <Target className="h-4 w-4 text-violet" /> Goals
        </h3>
        {canEdit && (
          <button onClick={() => setAdding((v) => !v)} className="inline-flex items-center gap-1 text-xs text-violet hover:underline">
            <Plus className="h-3.5 w-3.5" /> Add goal
          </button>
        )}
      </div>

      {error && <p className="mb-2 text-xs text-red-400">{error}</p>}

      {canEdit && adding && (
        <form onSubmit={addGoal} className="mb-3 flex flex-col gap-2 rounded-lg border border-fg/10 bg-fg/[0.02] p-3 sm:flex-row">
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Goal title" autoFocus className="flex-1" />
          <Input type="date" value={target} onChange={(e) => setTarget(e.target.value)} className="sm:w-40" />
          <Button type="submit" size="sm" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Add</Button>
        </form>
      )}

      {goals === null ? (
        <Loader2 className="h-4 w-4 animate-spin text-violet" />
      ) : goals.length === 0 ? (
        <p className="text-sm text-fg/40">No goals yet.</p>
      ) : (
        <div className="space-y-3">
          {goals.map((g) => (
            <div key={g.id} className="rounded-lg border border-fg/10 p-3">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className={"text-sm font-medium " + (g.status === "ACHIEVED" ? "text-fg/50 line-through" : "")}>{g.title}</p>
                  {g.targetDate && <p className="text-xs text-fg/40">target {g.targetDate}</p>}
                </div>
                <StatusChip status={g.status} />
              </div>

              <div className="mt-2 flex items-center gap-2">
                <div className="h-2 flex-1 overflow-hidden rounded-full bg-fg/10">
                  <div className={"h-full rounded-full " + (g.status === "ACHIEVED" ? "bg-emerald-400" : "bg-gradient-to-r from-violet to-aqua")}
                    style={{ width: `${g.progress}%` }} />
                </div>
                <span className="w-10 shrink-0 text-right text-xs tabular-nums text-fg/50">{g.progress}%</span>
              </div>

              {canEdit && (
                <div className="mt-2 flex items-center gap-3">
                  <input
                    type="range" min={0} max={100} defaultValue={g.progress}
                    onPointerUp={(e) => patch(g.id, { progress: Number(e.currentTarget.value) })}
                    onKeyUp={(e) => patch(g.id, { progress: Number(e.currentTarget.value) })}
                    className="h-1 flex-1 cursor-pointer accent-violet"
                  />
                  {g.status !== "ACHIEVED" && (
                    <button onClick={() => patch(g.id, { status: "ACHIEVED" })}
                      className="inline-flex items-center gap-1 text-xs text-emerald-400 hover:underline">
                      <Check className="h-3.5 w-3.5" /> Achieved
                    </button>
                  )}
                  <button onClick={() => remove(g.id)} className="text-fg/30 hover:text-red-400" aria-label="Delete goal">
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function StatusChip({ status }: { status: Goal["status"] }) {
  const map: Record<Goal["status"], string> = {
    OPEN: "bg-aqua/15 text-aqua",
    ACHIEVED: "bg-emerald-500/15 text-emerald-400",
    MISSED: "bg-red-500/15 text-red-400",
  };
  return <span className={"shrink-0 rounded-full px-2 py-0.5 text-xs " + map[status]}>{status.toLowerCase()}</span>;
}
