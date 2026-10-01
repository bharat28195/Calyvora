"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Users, UserCheck, CalendarOff, Palmtree } from "lucide-react";
import { api } from "@/lib/api";
import type { TeamOverview } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { MonthCalendar } from "@/components/ui/month-calendar";

/**
 * Owner/Admin team overview (founder feedback B1–B5): headcount, present vs on-leave today, who's out
 * and why, and a month leave calendar. Attendance is derived from approved leave for now.
 */
/**
 * Headline counts for the people the viewer is responsible for. For a whole-company role that is
 * the company and the tiles open the People pages; for a lead it is their downline and the tiles
 * open My team, because the People pages would refuse them.
 */
export function TeamOverviewSection({ wholeCompany = true }: { wholeCompany?: boolean }) {
  const peopleHref = wholeCompany ? "/people" : "/team";
  const attendanceHref = wholeCompany ? "/people/attendance" : "/team/attendance";
  const [data, setData] = useState<TeamOverview | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.teamOverview().then(setData).catch(() => setData(null)).finally(() => setLoading(false));
  }, []);

  return (
    <section className="mt-8">
      <h2 className="mb-4 text-lg font-semibold">{wholeCompany ? "Team overview" : "Your team today"}</h2>

      {/* Each tile opens the attendance day sheet, where the count can be drilled into by person. */}
      <div className="grid gap-4 sm:grid-cols-3">
        <Tile icon={<Users className="h-5 w-5 text-violet" />} label={wholeCompany ? "Total employees" : "People reporting to you"} value={data?.headcount}
          loading={loading} href={peopleHref} />
        <Tile icon={<UserCheck className="h-5 w-5 text-emerald-400" />} label="Present today" value={data?.presentToday}
          loading={loading} href={attendanceHref}
          hint={data && data.unmarkedToday > 0 ? `${data.unmarkedToday} not marked yet` : undefined} />
        <Tile icon={<CalendarOff className="h-5 w-5 text-amber-400" />} label="On leave today" value={data?.onLeaveToday}
          loading={loading} href={attendanceHref} hint="See who" />
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
              <p className="py-6 text-sm text-fg/40">Everyone is present today. 🎉</p>
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

function Tile({
  icon, label, value, loading, href, hint,
}: { icon: React.ReactNode; label: string; value?: number; loading: boolean; href?: string; hint?: string }) {
  const card = (
    <Card className={href ? "h-full transition-colors hover:border-fg/25" : undefined}>
      <div className="flex items-center gap-2 text-sm text-fg/50">{icon}{label}</div>
      {loading ? <div className="mt-3 h-7 w-14 animate-pulse rounded bg-fg/10" />
        : <p className="mt-2 text-3xl font-semibold tabular-nums">{value ?? 0}</p>}
      {hint && !loading && <p className="mt-1 text-xs text-fg/40">{hint}</p>}
    </Card>
  );
  return href ? <Link href={href}>{card}</Link> : card;
}

/**
 * Who is off this month, on the same grid as every other calendar in Orbit.
 *
 * <p>It used to draw its own: Sunday-first, dots rather than bars, its own idea of what "today"
 * looks like — so the dashboard and the attendance page disagreed about the shape of a month while
 * showing overlapping facts. One violet bar per day somebody is away; a taller bar would be a lie
 * about a card this size, which is why this one is compact and tinted instead.
 */
function LeaveCalendar({ leaves }: { leaves: TeamOverview["monthLeaves"] }) {
  const month = new Date().toLocaleDateString("sv").slice(0, 7);

  const days = useMemo(() => {
    const counts = new Map<string, number>();
    const last = new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0).getDate();
    for (let i = 1; i <= last; i++) {
      const date = `${month}-${String(i).padStart(2, "0")}`;
      const n = leaves.filter((l) => l.startDate <= date && l.endDate >= date).length;
      if (n > 0) counts.set(date, n);
    }
    return [...counts].map(([date, n]) => ({
      date,
      bars: [{ color: "bg-violet", label: "On leave" }],
      tint: "bg-violet/10",
      title: `${date} · ${n} on leave`,
    }));
  }, [leaves, month]);

  return (
    <div className="mt-3">
      <MonthCalendar month={month} days={days} compact />
      <p className="mt-3 text-xs text-fg/40">Tinted = someone on leave · outlined = today</p>
    </div>
  );
}

function label(type: string) {
  return type.charAt(0) + type.slice(1).toLowerCase();
}
