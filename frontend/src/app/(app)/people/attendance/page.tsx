"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, LogIn, LogOut, ChevronLeft, ChevronRight, Check } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { AttendanceDay, AttendanceEntry, AttendanceMonth, AttendanceStatus } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/** Colour + label per status. Derived rows are dimmed by the row itself, not here. */
const STATUS: Record<AttendanceStatus, { label: string; chip: string; dot: string }> = {
  PRESENT: { label: "Present", chip: "bg-emerald-500/15 text-emerald-400", dot: "bg-emerald-500" },
  WORK_FROM_HOME: { label: "WFH", chip: "bg-sky-500/15 text-sky-400", dot: "bg-sky-500" },
  HALF_DAY: { label: "Half day", chip: "bg-amber-500/15 text-amber-400", dot: "bg-amber-500" },
  ABSENT: { label: "Absent", chip: "bg-red-500/15 text-red-400", dot: "bg-red-500" },
  ON_LEAVE: { label: "On leave", chip: "bg-violet/15 text-violet", dot: "bg-violet" },
  HOLIDAY: { label: "Holiday", chip: "bg-fg/10 text-fg/50", dot: "bg-fg/30" },
  WEEK_OFF: { label: "Week off", chip: "bg-fg/10 text-fg/40", dot: "bg-fg/20" },
};
/** The statuses an admin marks by hand; leave comes from the leave flow, week-offs resolve themselves. */
const MARKABLE: AttendanceStatus[] = ["PRESENT", "WORK_FROM_HOME", "HALF_DAY", "ABSENT", "ON_LEAVE", "HOLIDAY"];

export default function AttendancePage() {
  const { me } = useSession();
  const isAdmin = me?.user.role === "OWNER" || me?.user.role === "ADMIN";

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Attendance</h1>
        <p className="mt-1 text-fg/50">
          Clock in for the day, and {isAdmin ? "mark the team's" : "review your"} record.
        </p>
      </div>

      <MyDay />
      {isAdmin && <TeamDaySheet />}
      <MyMonth />
    </div>
  );
}

/* ---------------- my day: check in / out ---------------- */

function MyDay() {
  const [entry, setEntry] = useState<AttendanceEntry | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api.attendanceToday()
      .then(setEntry)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load today"));
  }, []);
  useEffect(() => load(), [load]);

  async function act(which: "in" | "out") {
    setBusy(true);
    setError(null);
    try {
      setEntry(which === "in" ? await api.checkIn() : await api.checkOut());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to record that");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="mt-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <CardTitle>Today</CardTitle>
          <p className="mt-1 text-sm text-fg/50">
            {entry?.checkIn ? (
              <>
                In at <span className="text-fg">{hhmm(entry.checkIn)}</span>
                {entry.checkOut && <> · out at <span className="text-fg">{hhmm(entry.checkOut)}</span></>}
              </>
            ) : (
              "You haven't clocked in yet."
            )}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {entry?.status && <StatusChip status={entry.status} />}
          <Button onClick={() => act("in")} disabled={busy || !!entry?.checkIn}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogIn className="h-4 w-4" />} Check in
          </Button>
          <Button variant="secondary" onClick={() => act("out")} disabled={busy || !entry?.checkIn}>
            <LogOut className="h-4 w-4" /> Check out
          </Button>
        </div>
      </div>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
    </Card>
  );
}

/* ---------------- team day sheet (admin) ---------------- */

/** The tile you can click to drill into. */
type Filter = "IN" | "ON_LEAVE" | "ABSENT" | "UNMARKED";
const FILTER_LABEL: Record<Filter, string> = {
  IN: "In today", ON_LEAVE: "On leave", ABSENT: "Absent", UNMARKED: "Not marked",
};

function TeamDaySheet() {
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [sheet, setSheet] = useState<AttendanceDay | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState<string | null>(null);
  const [filter, setFilter] = useState<Filter | null>(null);

  const filtered = useMemo(() => {
    const entries = sheet?.entries ?? [];
    if (!filter) return entries;
    return entries.filter((e) => {
      if (filter === "UNMARKED") return e.status === null;
      if (filter === "IN") return e.status === "PRESENT" || e.status === "WORK_FROM_HOME" || e.status === "HALF_DAY";
      return e.status === filter;
    });
  }, [sheet, filter]);

  const load = useCallback(() => {
    setSheet(null);
    api.attendanceDay(date)
      .then(setSheet)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load the day"));
  }, [date]);
  useEffect(() => load(), [load]);

  async function mark(employeeId: string, status: AttendanceStatus) {
    setSaving(employeeId);
    try {
      await api.markAttendance({ employeeId, date, status });
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to mark that day");
    } finally {
      setSaving(null);
    }
  }

  const future = date > new Date().toISOString().slice(0, 10);

  return (
    <div className="mt-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-medium uppercase tracking-wide text-fg/40">Team day sheet</h2>
        <div className="flex items-center gap-1">
          <button onClick={() => setDate(shiftDay(date, -1))} aria-label="Previous day"
            className="rounded-md p-1.5 text-fg/50 hover:bg-fg/5 hover:text-fg">
            <ChevronLeft className="h-4 w-4" />
          </button>
          <input type="date" value={date} max={new Date().toISOString().slice(0, 10)}
            onChange={(e) => setDate(e.target.value)}
            className="h-9 rounded-lg border border-fg/15 bg-fg/5 px-2 text-sm text-fg" />
          <button onClick={() => setDate(shiftDay(date, 1))} aria-label="Next day" disabled={future}
            className="rounded-md p-1.5 text-fg/50 hover:bg-fg/5 hover:text-fg disabled:opacity-30">
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>

      {error && <Alert tone="error" className="mt-3">{error}</Alert>}

      {sheet === null ? (
        <Card className="mt-3"><Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" /></Card>
      ) : (
        <>
          {/* Counts are clickable: pick one to see exactly who it is. */}
          <div className="mt-3 grid gap-3 sm:grid-cols-5">
            <Tile label="Headcount" value={sheet.headcount} active={filter === null} onClick={() => setFilter(null)} />
            <Tile label="In" value={sheet.present} tone="text-emerald-400"
              active={filter === "IN"} onClick={() => setFilter("IN")} />
            <Tile label="On leave" value={sheet.onLeave} tone="text-violet"
              active={filter === "ON_LEAVE"} onClick={() => setFilter("ON_LEAVE")} />
            <Tile label="Absent" value={sheet.absent} tone="text-red-400"
              active={filter === "ABSENT"} onClick={() => setFilter("ABSENT")} />
            <Tile label="Not marked" value={sheet.unmarked} tone="text-fg/40"
              active={filter === "UNMARKED"} onClick={() => setFilter("UNMARKED")} />
          </div>

          <ByTeam entries={sheet.entries} />

          {filter && (
            <p className="mt-4 text-sm text-fg/50">
              Showing <span className="text-fg">{filtered.length}</span> {FILTER_LABEL[filter].toLowerCase()} ·{" "}
              <button onClick={() => setFilter(null)} className="text-violet hover:underline">show everyone</button>
            </p>
          )}

          <div className="mt-3 flex flex-col gap-2">
            {filtered.map((e) => (
              <Card key={e.employeeId} className="flex flex-wrap items-center justify-between gap-3 p-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{e.employeeName}</p>
                  <p className="truncate text-xs text-fg/40">
                    {e.jobTitle ?? "—"}
                    {e.checkIn && <> · in {hhmm(e.checkIn)}{e.checkOut && <> → {hhmm(e.checkOut)}</>}</>}
                    {e.note && <> · {e.note}</>}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-1.5">
                  {e.status && e.derived && <StatusChip status={e.status} derived />}
                  {saving === e.employeeId ? (
                    <Loader2 className="h-4 w-4 animate-spin text-violet" />
                  ) : (
                    MARKABLE.map((s) => (
                      <button
                        key={s}
                        onClick={() => mark(e.employeeId, s)}
                        className={`rounded-md px-2 py-1 text-xs transition-colors ${
                          !e.derived && e.status === s
                            ? STATUS[s].chip + " font-medium"
                            : "text-fg/40 hover:bg-fg/5 hover:text-fg"
                        }`}
                      >
                        {!e.derived && e.status === s && <Check className="mr-0.5 inline h-3 w-3" />}
                        {STATUS[s].label}
                      </button>
                    ))
                  )}
                </div>
              </Card>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

/* ---------------- my month ---------------- */

function MyMonth() {
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
      )}
    </div>
  );
}

/* ---------------- bits ---------------- */

function StatusChip({ status, derived }: { status: AttendanceStatus; derived?: boolean }) {
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs ${STATUS[status].chip} ${derived ? "opacity-70" : ""}`}
      title={derived ? "Inferred — nobody marked this day" : undefined}
    >
      {STATUS[status].label}{derived && " (auto)"}
    </span>
  );
}

function Tile({
  label, value, tone = "", active, onClick,
}: { label: string; value: number; tone?: string; active?: boolean; onClick?: () => void }) {
  const body = (
    <>
      <p className="text-sm text-fg/50">{label}</p>
      <p className={`mt-1 text-2xl font-semibold ${tone}`}>{value}</p>
    </>
  );
  if (!onClick) return <Card className="p-4">{body}</Card>;
  return (
    <button onClick={onClick} className="text-left">
      <Card className={`p-4 transition-colors hover:border-fg/25 ${active ? "border-violet/50 bg-violet/5" : ""}`}>
        {body}
      </Card>
    </button>
  );
}

/**
 * Per-team roll-up (founder ask: "how many members in this particular team are absent today").
 * Only rendered when departments are actually in use — a single "no team" row would be noise.
 */
function ByTeam({ entries }: { entries: AttendanceEntry[] }) {
  const teams = useMemo(() => {
    const map = new Map<string, { total: number; in: number; leave: number; absent: number; unmarked: number }>();
    for (const e of entries) {
      const key = e.department ?? "No team";
      const row = map.get(key) ?? { total: 0, in: 0, leave: 0, absent: 0, unmarked: 0 };
      row.total++;
      if (e.status === null) row.unmarked++;
      else if (e.status === "PRESENT" || e.status === "WORK_FROM_HOME" || e.status === "HALF_DAY") row.in++;
      else if (e.status === "ON_LEAVE") row.leave++;
      else if (e.status === "ABSENT") row.absent++;
      map.set(key, row);
    }
    return [...map.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  }, [entries]);

  if (teams.length < 2) return null;

  return (
    <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
      {teams.map(([name, r]) => (
        <Card key={name} className="p-3">
          <p className="truncate text-sm font-medium">{name}</p>
          <p className="mt-1 text-xs text-fg/50">
            <span className="text-emerald-400">{r.in} in</span>
            {r.leave > 0 && <> · <span className="text-violet">{r.leave} on leave</span></>}
            {r.absent > 0 && <> · <span className="text-red-400">{r.absent} absent</span></>}
            {r.unmarked > 0 && <> · {r.unmarked} not marked</>}
            {" · "}{r.total} total
          </p>
        </Card>
      ))}
    </div>
  );
}

function hhmm(t: string): string {
  return t.slice(0, 5);
}

function shiftDay(iso: string, by: number): string {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + by);
  return d.toISOString().slice(0, 10);
}
