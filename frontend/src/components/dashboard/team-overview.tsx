"use client";

import { useEffect, useMemo, useState } from "react";
import { Users, UserCheck, CalendarOff, Palmtree } from "lucide-react";
import { api } from "@/lib/api";
import type { TeamOverview } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";

/**
 * Owner/Admin team overview (founder feedback B1–B5): headcount, present vs on-leave today, who's out
 * and why, and a month leave calendar. Attendance is derived from approved leave for now.
 */
export function TeamOverviewSection() {
  const [data, setData] = useState<TeamOverview | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.teamOverview().then(setData).catch(() => setData(null)).finally(() => setLoading(false));
  }, []);

  return (
    <section className="mt-8">
      <h2 className="mb-4 text-lg font-semibold">Team overview</h2>

      <div className="grid gap-4 sm:grid-cols-3">
        <Tile icon={<Users className="h-5 w-5 text-violet" />} label="Total employees" value={data?.headcount} loading={loading} />
        <Tile icon={<UserCheck className="h-5 w-5 text-emerald-400" />} label="Present today" value={data?.presentToday} loading={loading} />
        <Tile icon={<CalendarOff className="h-5 w-5 text-amber-400" />} label="On leave today" value={data?.onLeaveToday} loading={loading} />
      </div>

      <div className="mt-4 grid gap-6 lg:grid-cols-2">
        <Card>
          <CardTitle>Out today</CardTitle>
          <div className="mt-3 flex flex-col divide-y divide-fg/5">
            {loading ? (
              <div className="h-16 animate-pulse rounded bg-fg/5" />
            ) : data && data.outToday.length > 0 ? (
              data.outToday.map((l, i) => (
                <div key={i} className="flex items-start gap-3 py-2.5">
                  <Palmtree className="mt-0.5 h-4 w-4 shrink-0 text-amber-400" />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{l.employeeName}</p>
                    <p className="truncate text-xs text-fg/50">
                      {label(l.type)}{l.reason ? ` — ${l.reason}` : ""}
                    </p>
                  </div>
                  <span className="ml-auto shrink-0 text-xs text-fg/40">{l.startDate.slice(5)} → {l.endDate.slice(5)}</span>
                </div>
              ))
            ) : (
              <p className="py-6 text-sm text-fg/40">Everyone's present today. 🎉</p>
            )}
          </div>
        </Card>

        <Card>
          <CardTitle>Leave calendar</CardTitle>
          <LeaveCalendar leaves={data?.monthLeaves ?? []} />
        </Card>
      </div>
    </section>
  );
}

function Tile({ icon, label, value, loading }: { icon: React.ReactNode; label: string; value?: number; loading: boolean }) {
  return (
    <Card>
      <div className="flex items-center gap-2 text-sm text-fg/50">{icon}{label}</div>
      {loading ? <div className="mt-3 h-7 w-14 animate-pulse rounded bg-fg/10" />
        : <p className="mt-2 text-3xl font-semibold tabular-nums">{value ?? 0}</p>}
    </Card>
  );
}

const WEEKDAYS = ["S", "M", "T", "W", "T", "F", "S"];

function LeaveCalendar({ leaves }: { leaves: TeamOverview["monthLeaves"] }) {
  const { cells, monthLabel, todayKey } = useMemo(() => {
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth(); // 0-based
    const firstDow = new Date(year, month, 1).getDay();
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const key = (d: number) => `${year}-${String(month + 1).padStart(2, "0")}-${String(d).padStart(2, "0")}`;

    // count how many people are on leave each day
    const counts: Record<number, number> = {};
    for (const l of leaves) {
      for (let d = 1; d <= daysInMonth; d++) {
        const k = key(d);
        if (l.startDate <= k && l.endDate >= k) counts[d] = (counts[d] ?? 0) + 1;
      }
    }
    const arr: ({ day: number; count: number; k: string } | null)[] = [];
    for (let i = 0; i < firstDow; i++) arr.push(null);
    for (let d = 1; d <= daysInMonth; d++) arr.push({ day: d, count: counts[d] ?? 0, k: key(d) });
    return {
      cells: arr,
      monthLabel: now.toLocaleString(undefined, { month: "long", year: "numeric" }),
      todayKey: key(now.getDate()),
    };
  }, [leaves]);

  return (
    <div className="mt-3">
      <p className="mb-2 text-sm font-medium text-fg/70">{monthLabel}</p>
      <div className="grid grid-cols-7 gap-1 text-center">
        {WEEKDAYS.map((w, i) => (
          <div key={i} className="pb-1 text-[10px] font-medium uppercase text-fg/30">{w}</div>
        ))}
        {cells.map((c, i) =>
          c === null ? (
            <div key={i} />
          ) : (
            <div
              key={i}
              title={c.count > 0 ? `${c.count} on leave` : undefined}
              className={
                "relative flex h-9 flex-col items-center justify-center rounded-md text-xs " +
                (c.count > 0 ? "bg-amber-400/10 text-fg" : "text-fg/50") +
                (c.k === todayKey ? " ring-1 ring-violet" : "")
              }
            >
              {c.day}
              {c.count > 0 && <span className="mt-0.5 h-1 w-1 rounded-full bg-amber-400" />}
            </div>
          ),
        )}
      </div>
      <p className="mt-2 text-xs text-fg/40">Amber = someone on leave · ring = today</p>
    </div>
  );
}

function label(type: string) {
  return type.charAt(0) + type.slice(1).toLowerCase();
}
