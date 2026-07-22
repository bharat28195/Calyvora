"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, ChevronLeft, ChevronRight, Check } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { AttendanceDay, AttendanceEntry, AttendanceStatus } from "@/lib/types";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { MARKABLE, STATUS, StatusChip, MyDay, MyMonth, hhmm } from "@/components/attendance/self";

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

/* ---------------- bits ---------------- */

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

function shiftDay(iso: string, by: number): string {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + by);
  return d.toISOString().slice(0, 10);
}
