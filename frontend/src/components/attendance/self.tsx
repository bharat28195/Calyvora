"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, LogIn, LogOut, RotateCcw } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { AttendanceEntry, AttendanceMonth, AttendanceStatus } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { cn } from "@/lib/utils";

/*
 * The self-service attendance pieces, shared by People -> Attendance (where they sit alongside the
 * team day sheet) and the Me hub. One implementation, two placements.
 */

/** Colour + label per status. */
export const STATUS: Record<AttendanceStatus, { label: string; chip: string; dot: string }> = {
  PRESENT: { label: "Present", chip: "bg-emerald-500/15 text-emerald-400", dot: "bg-emerald-500" },
  WORK_FROM_HOME: { label: "WFH", chip: "bg-sky-500/15 text-sky-400", dot: "bg-sky-500" },
  HALF_DAY: { label: "Half day", chip: "bg-amber-500/15 text-amber-400", dot: "bg-amber-500" },
  ABSENT: { label: "Absent", chip: "bg-red-500/15 text-red-400", dot: "bg-red-500" },
  ON_LEAVE: { label: "On leave", chip: "bg-violet/15 text-violet", dot: "bg-violet" },
  HOLIDAY: { label: "Holiday", chip: "bg-fg/10 text-fg/50", dot: "bg-fg/30" },
  WEEK_OFF: { label: "Week off", chip: "bg-fg/10 text-fg/40", dot: "bg-fg/20" },
};

/** The statuses an admin marks by hand; leave comes from the leave flow, week-offs resolve themselves. */
export const MARKABLE: AttendanceStatus[] = ["PRESENT", "WORK_FROM_HOME", "HALF_DAY", "ABSENT", "ON_LEAVE", "HOLIDAY"];

export function hhmm(t: string): string {
  return t.slice(0, 5);
}

/** "HH:mm" for a live Date (used to show elapsed working time before check-out). */
function nowHHmm(d: Date): string {
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/** Minutes between two "HH:mm[:ss]" times, or null if either is missing. */
export function minutesBetween(inT: string | null, outT: string | null): number | null {
  if (!inT || !outT) return null;
  const [ih, im] = inT.split(":").map(Number);
  const [oh, om] = outT.split(":").map(Number);
  const mins = oh * 60 + om - (ih * 60 + im);
  return mins > 0 ? mins : null;
}

/** "8h 05m" (or "—" when we can't compute it). */
export function fmtDuration(mins: number | null): string {
  if (mins == null) return "—";
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return `${h}h ${String(m).padStart(2, "0")}m`;
}

export function StatusChip({ status, derived }: { status: AttendanceStatus; derived?: boolean }) {
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs ${STATUS[status].chip} ${derived ? "opacity-70" : ""}`}
      title={derived ? "Inferred — nobody marked this day" : undefined}
    >
      {STATUS[status].label}{derived && " (auto)"}
    </span>
  );
}

export function MyDay() {
  const [entry, setEntry] = useState<AttendanceEntry | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(() => new Date());

  const load = useCallback(() => {
    api.attendanceToday()
      .then(setEntry)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load today"));
  }, []);
  useEffect(() => load(), [load]);

  // A ticking clock, like Keka's "Time Today".
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(t);
  }, []);

  async function act(which: "in" | "out" | "reset") {
    setBusy(true);
    setError(null);
    try {
      const next = which === "in" ? await api.checkIn()
        : which === "out" ? await api.checkOut()
        : await api.resetToday();
      setEntry(next);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to record that");
    } finally {
      setBusy(false);
    }
  }

  const clockedIn = !!entry?.checkIn;
  const clockedOut = !!entry?.checkOut;
  const worked = fmtDuration(minutesBetween(entry?.checkIn ?? null, entry?.checkOut ?? (clockedIn ? nowHHmm(now) : null)));
  const dateLabel = now.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short", year: "numeric" });
  const timeLabel = now.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" });

  return (
    <Card className="mt-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <CardTitle>Time Today</CardTitle>
            {entry?.status && <StatusChip status={entry.status} derived={entry.derived} />}
          </div>
          <p className="mt-1 text-xs text-fg/40">{dateLabel}</p>
          <p className="mt-2 font-mono text-4xl font-semibold tabular-nums tracking-tight">{timeLabel}</p>
          <p className="mt-2 text-sm text-fg/60">
            {clockedIn ? (
              <>
                In at <span className="font-medium text-fg">{hhmm(entry!.checkIn!)}</span>
                {clockedOut
                  ? <> · out at <span className="font-medium text-fg">{hhmm(entry!.checkOut!)}</span> · <span className="text-fg/80">{worked}</span></>
                  : <> · <span className="text-emerald-400">working now</span> · {worked}</>}
              </>
            ) : "You haven't clocked in yet."}
          </p>
        </div>
        <div className="flex flex-col items-end gap-2">
          {!clockedIn ? (
            <Button onClick={() => act("in")} disabled={busy}>
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogIn className="h-4 w-4" />} Check in
            </Button>
          ) : (
            <Button variant={clockedOut ? "ghost" : "primary"} onClick={() => act("out")} disabled={busy}>
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogOut className="h-4 w-4" />} {clockedOut ? "Update check-out" : "Check out"}
            </Button>
          )}
          {clockedIn && (
            <button onClick={() => act("reset")} disabled={busy}
              className="inline-flex items-center gap-1 text-xs text-fg/40 hover:text-fg/70">
              <RotateCcw className="h-3 w-3" /> Reset today
            </button>
          )}
        </div>
      </div>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
    </Card>
  );
}


export function MyMonth() {
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const [data, setData] = useState<AttendanceMonth | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setData(null);
    api.myAttendance(month)
      .then(setData)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load your month"));
  }, [month]);

  // Pad the grid so day 1 lands under its weekday.
  const leading = useMemo(() => {
    if (!data) return 0;
    const first = new Date(`${data.month}-01T00:00:00`).getDay();
    return (first + 6) % 7;   // Monday-first
  }, [data]);

  return (
    <div className="mt-10">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-medium uppercase tracking-wide text-fg/40">My month</h2>
        <input type="month" value={month} onChange={(e) => setMonth(e.target.value)}
          className="h-9 rounded-lg border border-fg/15 bg-fg/5 px-2 text-sm text-fg" />
      </div>

      {error && <Alert tone="error" className="mt-3">{error}</Alert>}

      {data === null ? (
        <Card className="mt-3"><Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" /></Card>
      ) : (
        <>
        <Card className="mt-3">
          <div className="flex flex-wrap items-center gap-6">
            <div>
              <p className="text-sm text-fg/50">Worked</p>
              <p className="text-2xl font-semibold">
                {data.workedDays}<span className="text-base text-fg/40"> / {data.expectedDays} days</span>
              </p>
            </div>
            {data.attendanceRate !== null && (
              <div>
                <p className="text-sm text-fg/50">Attendance</p>
                <p className="text-2xl font-semibold text-violet">{data.attendanceRate}%</p>
              </div>
            )}
            <div className="flex flex-wrap gap-1.5">
              {Object.entries(data.counts)
                .filter(([, n]) => n > 0)
                .map(([s, n]) => (
                  <span key={s} className={`rounded-full px-2 py-0.5 text-xs ${STATUS[s as AttendanceStatus].chip}`}>
                    {STATUS[s as AttendanceStatus].label} {n}
                  </span>
                ))}
            </div>
          </div>

          <div className="mt-5 grid grid-cols-7 gap-1.5 text-center">
            {["M", "T", "W", "T", "F", "S", "S"].map((d, i) => (
              <span key={i} className="text-xs text-fg/30">{d}</span>
            ))}
            {Array.from({ length: leading }).map((_, i) => <span key={`pad-${i}`} />)}
            {data.days.map((d) => (
              <div
                key={d.date}
                title={`${d.date}${d.status ? ` · ${STATUS[d.status].label}` : " · not marked"}${d.note ? ` · ${d.note}` : ""}`}
                className="flex flex-col items-center gap-1 rounded-md py-1.5 hover:bg-fg/5"
              >
                <span className="text-xs text-fg/50">{Number(d.date.slice(-2))}</span>
                <span className={`h-1.5 w-1.5 rounded-full ${d.status ? STATUS[d.status].dot : "bg-fg/10"}`} />
              </div>
            ))}
          </div>
        </Card>

        <DailyLog days={data.days} />
        </>
      )}
    </div>
  );
}

/**
 * A day-wise log of when you clocked in and out — the table people expect from Keka/Zoho. Newest
 * first, working days only (weekends/holidays are hidden), future days dropped. Hours are computed
 * from the in/out pair and shown with a bar relative to a 9-hour day.
 */
export function DailyLog({ days }: { days: AttendanceEntry[] }) {
  const today = new Date().toISOString().slice(0, 10);
  // Full log: every day up to today (newest first), including week-offs/holidays (muted) and today
  // even if it's still open — a complete timesheet, not just the days that were worked.
  const rows = days.filter((d) => d.date <= today).slice().reverse();

  if (rows.length === 0) {
    return (
      <Card className="mt-4">
        <CardTitle>Daily log</CardTitle>
        <p className="mt-2 text-sm text-fg/50">No days logged yet this month.</p>
      </Card>
    );
  }

  return (
    <Card className="mt-4 overflow-x-auto p-0">
      <div className="px-5 pt-5"><CardTitle>Daily log</CardTitle></div>
      <table className="mt-3 w-full min-w-[560px] border-collapse text-sm">
        <thead>
          <tr className="border-y border-fg/10 text-left text-xs uppercase tracking-wide text-fg/40">
            <th className="px-5 py-2 font-medium">Date</th>
            <th className="px-3 py-2 font-medium">Status</th>
            <th className="px-3 py-2 font-medium">Check in</th>
            <th className="px-3 py-2 font-medium">Check out</th>
            <th className="px-3 py-2 font-medium">Hours</th>
            <th className="w-32 px-5 py-2 font-medium">&nbsp;</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((d) => {
            const mins = minutesBetween(d.checkIn, d.checkOut);
            const pct = mins == null ? 0 : Math.min(100, Math.round((mins / (9 * 60)) * 100));
            const dt = new Date(`${d.date}T00:00:00`);
            const weekday = dt.toLocaleDateString(undefined, { weekday: "short" });
            const nice = dt.toLocaleDateString(undefined, { day: "numeric", month: "short" });
            const offDay = d.status === "WEEK_OFF" || d.status === "HOLIDAY" || !d.status;
            return (
              <tr key={d.date} className={cn("border-b border-fg/5 last:border-0 hover:bg-fg/[0.03]", offDay && "text-fg/40")}>
                <td className="px-5 py-2.5 whitespace-nowrap">
                  <span className="font-medium">{nice}</span>
                  <span className="ml-1.5 text-xs text-fg/40">{weekday}</span>
                </td>
                <td className="px-3 py-2.5">{d.status ? <StatusChip status={d.status} derived={d.derived} /> : <span className="text-xs text-fg/30">not marked</span>}</td>
                <td className="px-3 py-2.5 tabular-nums text-fg/80">{d.checkIn ? hhmm(d.checkIn) : "—"}</td>
                <td className="px-3 py-2.5 tabular-nums text-fg/80">{d.checkOut ? hhmm(d.checkOut) : "—"}</td>
                <td className="px-3 py-2.5 tabular-nums font-medium">{fmtDuration(mins)}</td>
                <td className="px-5 py-2.5">
                  <div className="h-1.5 w-full rounded-full bg-fg/10">
                    <div className="h-1.5 rounded-full bg-violet" style={{ width: `${pct}%` }} />
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </Card>
  );
}

