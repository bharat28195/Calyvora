"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  Loader2, DoorOpen, Plus, CheckCircle2, Undo2, FileText, AlertTriangle,
} from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Employee, ExitView, Page } from "@/lib/types";
import { useSession } from "@/hooks/useSession";
import { KIND_LABELS } from "@/lib/documents";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

const selectCls =
  "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

/**
 * Exit formalities (PD-20). HR starts an exit; the leaver's manager works the clearance list; HR
 * completes it, and the relieving letter and experience certificate are issued off the letterpad.
 *
 * <p>One screen rather than a step in each person's profile, because the question a manager actually
 * has is "what have I got to close out this month", and that is a list.
 */
export default function ExitsPage() {
  const { me } = useSession();
  const [exits, setExits] = useState<ExitView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);

  // A manager works the checklist; deciding that someone is leaving, and declaring them left, is HR's.
  const canStart = me?.user.role === "ADMIN" || me?.user.role === "HR" || me?.user.role === "OWNER";

  const load = useCallback(async () => {
    try {
      setExits(await api.exits());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load exits");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Exits</h1>
          <p className="mt-1 text-fg/50">
            Everyone serving notice, and the clearance still to be done before their last day.
          </p>
        </div>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {canStart && <StartExitCard onStarted={(e) => { void load(); setOpenId(e.employeeId); }} />}

      {exits === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : exits.length === 0 ? (
        <Card className="mt-6 text-sm text-fg/50">
          <DoorOpen className="mb-2 h-5 w-5 text-fg/30" />
          Nobody is serving notice. When someone resigns, start their exit above and the clearance
          checklist appears here for their manager.
        </Card>
      ) : (
        <div className="mt-6 flex flex-col gap-3">
          {exits.map((e) => (
            <ExitCard
              key={e.employeeId}
              exit={e}
              open={openId === e.employeeId}
              onToggleOpen={() => setOpenId(openId === e.employeeId ? null : e.employeeId)}
              onChanged={load}
              canComplete={!!canStart}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function StartExitCard({ onStarted }: { onStarted: (e: ExitView) => void }) {
  const [people, setPeople] = useState<Employee[]>([]);
  const [employeeId, setEmployeeId] = useState("");
  const [lastWorkingDay, setLastWorkingDay] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // One page of the directory is enough to pick from; the search box below narrows it.
    api.directoryPage("", 0, 200)
      .then((p: Page<Employee>) => setPeople(p.content))
      .catch(() => setPeople([]));
  }, []);

  const selectable = useMemo(
    () => people.filter((p) => p.employmentStatus !== "TERMINATED" && p.employmentStatus !== "NOTICE"),
    [people],
  );

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      onStarted(await api.startExit(employeeId, { lastWorkingDay, reason: reason || undefined }));
      setEmployeeId("");
      setLastWorkingDay("");
      setReason("");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not start the exit");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="mt-6">
      <CardTitle>Start an exit</CardTitle>
      <p className="mt-1 text-sm text-fg/50">
        Records the last working day, moves them to notice, and raises the clearance checklist for
        their manager.
      </p>
      {error && <Alert tone="error" className="mt-4">{error}</Alert>}
      <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Who is leaving" htmlFor="x-who">
          <select id="x-who" className={selectCls} value={employeeId}
            onChange={(e) => setEmployeeId(e.target.value)}>
            <option value="" className="bg-surface">Select someone…</option>
            {selectable.map((p) => (
              <option key={p.id} value={p.id} className="bg-surface">
                {p.firstName} {p.lastName}{p.jobTitle ? ` — ${p.jobTitle}` : ""}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Last working day" htmlFor="x-lwd">
          <Input id="x-lwd" type="date" value={lastWorkingDay}
            onChange={(e) => setLastWorkingDay(e.target.value)} />
        </Field>
        <Field label="Reason" htmlFor="x-reason" className="lg:col-span-1">
          <Input id="x-reason" value={reason} placeholder="Resignation"
            onChange={(e) => setReason(e.target.value)} />
        </Field>
        <div className="flex items-end">
          <Button onClick={submit} disabled={!employeeId || !lastWorkingDay || busy} className="w-full">
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />} Start exit
          </Button>
        </div>
      </div>
    </Card>
  );
}

function ExitCard({
  exit, open, onToggleOpen, onChanged, canComplete,
}: {
  exit: ExitView;
  open: boolean;
  onToggleOpen: () => void;
  onChanged: () => Promise<void>;
  canComplete: boolean;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [newTask, setNewTask] = useState("");
  const pct = exit.tasksTotal === 0 ? 0 : Math.round((exit.tasksDone / exit.tasksTotal) * 100);

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      await onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "That didn't work");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <button onClick={onToggleOpen} className="text-left">
          <div className="font-medium">{exit.employeeName ?? "Unnamed"}</div>
          <div className="text-xs text-fg/50">
            Last day {exit.lastWorkingDay ?? "—"}
            {exit.managerName && <> · manager {exit.managerName}</>}
            {exit.reason && <> · {exit.reason}</>}
          </div>
        </button>
        <div className="flex items-center gap-4">
          <div className="w-40">
            <div className="flex justify-between text-xs text-fg/50">
              <span>Clearance</span>
              <span>{exit.tasksDone}/{exit.tasksTotal}</span>
            </div>
            <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-fg/10">
              <div
                className={`h-full rounded-full ${exit.checklistComplete ? "bg-emerald-400" : "bg-violet"}`}
                style={{ width: `${pct}%` }}
              />
            </div>
          </div>
          <Button variant="ghost" size="sm" onClick={onToggleOpen}>
            {open ? "Hide" : "Open"}
          </Button>
        </div>
      </div>

      {open && (
        <div className="mt-5 border-t border-fg/10 pt-5">
          {error && <Alert tone="error" className="mb-4">{error}</Alert>}

          <div className="flex flex-col gap-1.5">
            {exit.checklist.map((t) => (
              <label key={t.id}
                className="flex items-center gap-3 rounded-lg px-2 py-1.5 text-sm hover:bg-fg/5">
                <input
                  type="checkbox"
                  checked={t.completed}
                  disabled={busy}
                  onChange={(e) => run(() => api.toggleExitTask(t.id, e.target.checked))}
                  className="h-4 w-4 rounded border-fg/20 bg-fg/5 accent-violet"
                />
                <span className={t.completed ? "text-fg/40 line-through" : "text-fg/80"}>{t.title}</span>
              </label>
            ))}
            {exit.checklist.length === 0 && (
              <p className="text-sm text-fg/50">No clearance items. Add what this exit needs below.</p>
            )}
          </div>

          <div className="mt-4 flex gap-2">
            <Input
              value={newTask}
              placeholder="Add a clearance item…"
              onChange={(e) => setNewTask(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && newTask.trim()) {
                  void run(() => api.addExitTask(exit.employeeId, newTask.trim())).then(() => setNewTask(""));
                }
              }}
            />
            <Button
              variant="secondary"
              disabled={!newTask.trim() || busy}
              onClick={() => run(() => api.addExitTask(exit.employeeId, newTask.trim())).then(() => setNewTask(""))}
            >
              <Plus className="h-4 w-4" /> Add
            </Button>
          </div>

          {exit.letters.length > 0 && (
            <div className="mt-5">
              <p className="text-xs font-medium uppercase tracking-wide text-fg/40">Issued</p>
              <div className="mt-2 flex flex-col gap-1">
                {exit.letters.map((l) => (
                  <Link key={l.id} href={`/documents/${l.id}`}
                    className="flex items-center gap-2 text-sm text-violet hover:underline">
                    <FileText className="h-3.5 w-3.5" />
                    {KIND_LABELS[l.kind]} — {l.title}
                  </Link>
                ))}
              </div>
            </div>
          )}

          {canComplete && (
            <div className="mt-5 flex flex-wrap items-center gap-2 border-t border-fg/10 pt-4">
              <Button
                disabled={busy}
                onClick={() => run(() => api.completeExit(exit.employeeId))}
              >
                {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
                Complete exit &amp; issue letters
              </Button>
              <Button
                variant="ghost"
                disabled={busy}
                onClick={() => {
                  if (confirm(`Cancel ${exit.employeeName}'s exit? The clearance list is deleted and they go back to active.`)) {
                    void run(() => api.cancelExit(exit.employeeId));
                  }
                }}
              >
                <Undo2 className="h-4 w-4" /> Resignation withdrawn
              </Button>
              {!exit.checklistComplete && exit.tasksTotal > 0 && (
                <Button
                  variant="ghost"
                  disabled={busy}
                  onClick={() => {
                    if (confirm("Complete the exit with clearance still open? The relieving letter says property was returned and dues settled.")) {
                      void run(() => api.completeExit(exit.employeeId, true));
                    }
                  }}
                >
                  <AlertTriangle className="h-4 w-4 text-amber-400" /> Complete anyway
                </Button>
              )}
            </div>
          )}
        </div>
      )}
    </Card>
  );
}
