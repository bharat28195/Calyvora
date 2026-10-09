"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, Plus, ChevronLeft, ChevronRight, X } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Roster, Shift } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";

const DOW = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

/** Add days to an ISO date (yyyy-mm-dd) and return an ISO date. */
function shiftWeek(iso: string, days: number): string {
  const d = new Date(iso + "T00:00:00");
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}
function dayNum(iso: string): string {
  return String(Number(iso.slice(8, 10)));
}

/** Shift scheduling — reusable shift templates plus a weekly roster grid. Owner/Admin. */
export default function ShiftsPage() {
  const [weekStart, setWeekStart] = useState<string | null>(null);
  const [roster, setRoster] = useState<Roster | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [addingShift, setAddingShift] = useState(false);
  const [saving, setSaving] = useState<string | null>(null);

  const loadWeek = useCallback((ws?: string) => {
    api.roster(ws).then((r) => { setRoster(r); setWeekStart(r.weekStart); })
      .catch((e) => { setRoster({ weekStart: ws ?? "", days: [], shifts: [], employees: [], assignments: [] }); setError(e instanceof ApiError ? e.message : "Failed to load roster"); });
  }, []);

  useEffect(() => { loadWeek(); }, [loadWeek]);

  function refreshShifts() {
    if (weekStart) loadWeek(weekStart);
  }

  async function assign(employeeId: string, onDate: string, shiftId: string) {
    if (!roster) return;
    const key = employeeId + onDate;
    setSaving(key); setError(null);
    try {
      if (shiftId === "") {
        const existing = roster.assignments.find((a) => a.employeeId === employeeId && a.onDate === onDate);
        if (existing) { await api.unassignShift(existing.id); }
        setRoster({ ...roster, assignments: roster.assignments.filter((a) => !(a.employeeId === employeeId && a.onDate === onDate)) });
      } else {
        const entry = await api.assignShift(employeeId, onDate, shiftId);
        const rest = roster.assignments.filter((a) => !(a.employeeId === employeeId && a.onDate === onDate));
        setRoster({ ...roster, assignments: [...rest, entry] });
      }
    } catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't update the roster"); }
    finally { setSaving(null); }
  }

  const shiftById = new Map((roster?.shifts ?? []).map((s) => [s.id, s]));

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Shifts</h1>
          <p className="mt-1 text-fg/50">Shift templates and the weekly roster.</p>
        </div>
        <Button onClick={() => setAddingShift((v) => !v)}><Plus className="h-4 w-4" /> New shift</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      <WorkDayCard />
      {addingShift && <NewShiftForm onCreated={() => { setAddingShift(false); refreshShifts(); }} onCancel={() => setAddingShift(false)} />}

      {/* Shift templates */}
      <Card className="mt-6">
        <CardTitle>Shift templates</CardTitle>
        {roster && roster.shifts.length === 0 ? (
          <p className="mt-2 text-sm text-fg/50">No shifts yet. Create one to start rostering.</p>
        ) : (
          <div className="mt-3 flex flex-wrap gap-2">
            {roster?.shifts.map((s) => (
              <span key={s.id} className="inline-flex items-center gap-2 rounded-full border border-fg/10 bg-fg/5 py-1 pl-2.5 pr-1.5 text-sm">
                <span className="h-2.5 w-2.5 rounded-full" style={{ background: s.color ?? "#8b5cf6" }} />
                <span className="font-medium">{s.name}</span>
                <span className="text-xs text-fg/50">
                  {s.startTime}–{s.endTime}{s.workMinutes ? ` · ${hours(s.workMinutes)} work` : ""}
                </span>
                <button onClick={() => api.deleteShift(s.id).then(refreshShifts).catch(() => {})}
                  className="rounded-full p-0.5 text-fg/40 hover:bg-fg/10 hover:text-fg" aria-label={`Delete ${s.name}`}>
                  <X className="h-3.5 w-3.5" />
                </button>
              </span>
            ))}
          </div>
        )}
      </Card>

      {/* Week nav */}
      <div className="mt-6 flex items-center justify-between gap-2">
        <h2 className="text-sm font-medium text-fg/70">
          {weekStart ? `Week of ${weekStart}` : "Roster"}
        </h2>
        <div className="flex items-center gap-1">
          <Button variant="ghost" onClick={() => weekStart && loadWeek(shiftWeek(weekStart, -7))} aria-label="Previous week"><ChevronLeft className="h-4 w-4" /></Button>
          <Button variant="ghost" onClick={() => loadWeek()}>This week</Button>
          <Button variant="ghost" onClick={() => weekStart && loadWeek(shiftWeek(weekStart, 7))} aria-label="Next week"><ChevronRight className="h-4 w-4" /></Button>
        </div>
      </div>

      {/* Roster grid */}
      {roster === null ? (
        <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : roster.shifts.length === 0 ? (
        <Card className="mt-4"><p className="text-sm text-fg/50">Add a shift template above, then assign people below.</p></Card>
      ) : roster.employees.length === 0 ? (
        <Card className="mt-4"><p className="text-sm text-fg/50">No employees to roster yet.</p></Card>
      ) : (
        <Card className="mt-4 overflow-x-auto p-0">
          <table className="w-full min-w-[720px] border-collapse text-sm">
            <thead>
              <tr className="border-b border-fg/10">
                <th className="sticky left-0 z-10 bg-surface px-4 py-3 text-left font-medium text-fg/60">Employee</th>
                {roster.days.map((d, i) => (
                  <th key={d} className="px-2 py-3 text-center font-medium text-fg/60">
                    <div>{DOW[i]}</div>
                    <div className="text-xs font-normal text-fg/40">{dayNum(d)}</div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {roster.employees.map((emp) => (
                <tr key={emp.employeeId} className="border-b border-fg/5 last:border-0">
                  <td className="sticky left-0 z-10 bg-surface px-4 py-2">
                    <div className="font-medium">{emp.name}</div>
                    {emp.jobTitle && <div className="text-xs text-fg/40">{emp.jobTitle}</div>}
                  </td>
                  {roster.days.map((d) => {
                    const a = roster.assignments.find((x) => x.employeeId === emp.employeeId && x.onDate === d);
                    const s = a ? shiftById.get(a.shiftId) : undefined;
                    const key = emp.employeeId + d;
                    return (
                      <td key={d} className="px-1.5 py-1.5 text-center">
                        <RosterCell shift={s} shifts={roster.shifts} busy={saving === key}
                          onChange={(shiftId) => assign(emp.employeeId, d, shiftId)} />
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  );
}

function RosterCell({ shift, shifts, busy, onChange }: {
  shift: Shift | undefined; shifts: Shift[]; busy: boolean; onChange: (shiftId: string) => void;
}) {
  return (
    <div className="relative">
      <select
        value={shift?.id ?? ""}
        onChange={(e) => onChange(e.target.value)}
        disabled={busy}
        className="w-full min-w-[84px] cursor-pointer appearance-none rounded-md border px-2 py-1.5 text-center text-xs font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
        style={shift
          ? { borderColor: "transparent", background: (shift.color ?? "#8b5cf6") + "26", color: shift.color ?? "#8b5cf6" }
          : { borderColor: "rgba(127,127,127,0.15)", background: "transparent" }}
      >
        <option value="" className="bg-surface text-fg">—</option>
        {shifts.map((s) => <option key={s.id} value={s.id} className="bg-surface text-fg">{s.name}</option>)}
      </select>
      {busy && <Loader2 className="pointer-events-none absolute right-1 top-1.5 h-3 w-3 animate-spin text-fg/40" />}
    </div>
  );
}

function NewShiftForm({ onCreated, onCancel }: { onCreated: () => void; onCancel: () => void }) {
  const [name, setName] = useState("");
  const [startTime, setStartTime] = useState("09:00");
  const [endTime, setEndTime] = useState("17:00");
  const [color, setColor] = useState("#8b5cf6");
  const [workHours, setWorkHours] = useState("9");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    setBusy(true); setError(null);
    try {
      await api.createShift({ name: name.trim(), startTime, endTime, color,
        workMinutes: Math.round(Number(workHours) * 60) || 540 });
      onCreated();
    } catch (err) { setError(err instanceof ApiError ? err.message : "Couldn't create the shift"); setBusy(false); }
  }

  return (
    <Card className="mt-6">
      <CardTitle>New shift</CardTitle>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      <form onSubmit={submit} className="mt-3 grid gap-3 sm:grid-cols-4">
        <div className="sm:col-span-2"><Field label="Name" htmlFor="s-name"><Input id="s-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Morning" autoFocus /></Field></div>
        <Field label="Start" htmlFor="s-start"><Input id="s-start" type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} /></Field>
        <Field label="End" htmlFor="s-end"><Input id="s-end" type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} /></Field>
        <Field label="Work hours" htmlFor="s-hours" hint="Actual work, breaks excluded">
          <Input id="s-hours" type="number" min="0.5" max="24" step="0.5" value={workHours}
            onChange={(e) => setWorkHours(e.target.value)} />
        </Field>
        <Field label="Colour" htmlFor="s-color">
          <input id="s-color" type="color" value={color} onChange={(e) => setColor(e.target.value)}
            className="h-11 w-full cursor-pointer rounded-lg border border-fg/15 bg-fg/5 px-1" />
        </Field>
        <div className="flex items-end gap-2 sm:col-span-2">
          <Button type="submit" disabled={busy || !name.trim()}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Create shift</Button>
          <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
        </div>
      </form>
    </Card>
  );
}

/** "9h" or "8h 30m" from minutes. */
function hours(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m ? `${h}h ${m}m` : `${h}h`;
}

/**
 * The company's working day, for everyone not rostered onto a shift: when it starts, how many hours
 * of work it asks for, and how long after the start a person with no check-in counts as absent.
 * A day short of the hours becomes a half day, which payroll pays as half. Managers and HR can still
 * correct any day by hand.
 */
function WorkDayCard() {
  const [start, setStart] = useState("09:30");
  const [workHours, setWorkHours] = useState("9");
  const [graceHours, setGraceHours] = useState("2");
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.workDay().then((p) => {
      setStart(p.workDayStart.slice(0, 5));
      setWorkHours(String(p.workDayMinutes / 60));
      setGraceHours(String(p.absentGraceMinutes / 60));
    }).catch(() => {}).finally(() => setLoaded(true));
  }, []);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null); setSaved(false);
    try {
      await api.setWorkDay({
        workDayStart: start,
        workDayMinutes: Math.round(Number(workHours) * 60),
        absentGraceMinutes: Math.round(Number(graceHours) * 60),
      });
      setSaved(true);
    } catch (err) { setError(err instanceof ApiError ? err.message : "Couldn't save the working day"); }
    finally { setBusy(false); }
  }

  return (
    <Card className="mt-6">
      <CardTitle>Working day</CardTitle>
      <p className="mt-1 text-sm text-fg/60">
        For everyone not on a shift. No check-in by the start plus the grace means absent; fewer hours
        than required means a half day.
      </p>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      {saved && <Alert tone="success" className="mt-3">Saved.</Alert>}
      <form onSubmit={save} className="mt-4 grid gap-3 sm:grid-cols-4">
        <Field label="Day starts" htmlFor="wd-start">
          <Input id="wd-start" type="time" value={start} onChange={(e) => { setStart(e.target.value); setSaved(false); }} />
        </Field>
        <Field label="Required hours" htmlFor="wd-hours">
          <Input id="wd-hours" type="number" min="0.5" max="24" step="0.5" value={workHours}
            onChange={(e) => { setWorkHours(e.target.value); setSaved(false); }} />
        </Field>
        <Field label="Absent after (hours)" htmlFor="wd-grace">
          <Input id="wd-grace" type="number" min="0" max="12" step="0.25" value={graceHours}
            onChange={(e) => { setGraceHours(e.target.value); setSaved(false); }} />
        </Field>
        <div className="flex items-end">
          <Button type="submit" disabled={busy || !loaded}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Save</Button>
        </div>
      </form>
    </Card>
  );
}
