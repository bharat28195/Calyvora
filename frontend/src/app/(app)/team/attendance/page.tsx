"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { TeamSummary } from "@/lib/types";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { NoTeam, ScopeToggle, TeamHeader, useTeamStanding } from "@/components/team/team-bits";

/** The last twelve months, newest first — far enough back to settle an argument about a payslip. */
function recentMonths(count = 12): string[] {
  const out: string[] = [];
  const d = new Date();
  for (let i = 0; i < count; i++) {
    out.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`);
    d.setMonth(d.getMonth() - 1);
  }
  return out;
}

/**
 * The team's attendance for a month.
 *
 * Counts rather than a day-by-day grid: a lead is asking "is anybody drifting", and thirty columns
 * per person answers that worse than four numbers does. The per-person detail lives on the profile.
 */
export default function TeamAttendancePage() {
  const standing = useTeamStanding();
  const months = recentMonths();
  const [direct, setDirect] = useState(false);
  const [month, setMonth] = useState(months[0]);
  const [summary, setSummary] = useState<TeamSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setSummary(null);
    try {
      setSummary(await api.teamSummary({ direct, month }));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load attendance");
    }
  }, [direct, month]);

  useEffect(() => { void load(); }, [load]);

  if (standing && !standing.leadsTeam) {
    return <div><TeamHeader title="Team attendance" blurb="How the people you lead are tracking." /><NoTeam /></div>;
  }

  return (
    <div>
      <TeamHeader title="Team attendance" blurb="Present, absent and on leave, per person, per month.">
        <div className="flex flex-wrap items-center gap-2">
          <select
            value={month}
            onChange={(e) => setMonth(e.target.value)}
            className="rounded-lg border border-fg/10 bg-surface px-3 py-1.5 text-sm"
          >
            {months.map((m) => <option key={m} value={m}>{m}</option>)}
          </select>
          <ScopeToggle direct={direct} onChange={setDirect} standing={standing} />
        </div>
      </TeamHeader>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      <div className="mt-8 overflow-x-auto">
        {summary === null ? (
          <div className="flex justify-center py-10"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
        ) : summary.members.length === 0 ? (
          <Card className="text-sm text-fg/50">Nobody on your team has attendance recorded for {month}.</Card>
        ) : (
          <table className="w-full min-w-[640px] text-sm">
            <thead className="text-left text-xs uppercase tracking-wide text-fg/40">
              <tr>
                <th className="px-3 py-2 font-medium">Name</th>
                <th className="px-3 py-2 font-medium">Today</th>
                <th className="px-3 py-2 font-medium text-right">Present</th>
                <th className="px-3 py-2 font-medium text-right">Absent</th>
                <th className="px-3 py-2 font-medium text-right">On leave</th>
              </tr>
            </thead>
            <tbody>
              {summary.members.map((m) => (
                <tr key={m.employeeId} className="border-t border-fg/5">
                  <td className="px-3 py-2.5">
                    <div className="font-medium">{m.name}</div>
                    <div className="text-xs text-fg/40">
                      {m.direct ? "direct report" : m.managerName ? `reports to ${m.managerName}` : "—"}
                    </div>
                  </td>
                  <td className="px-3 py-2.5">
                    {m.todayStatus ? <Badge value={m.todayStatus} /> : <span className="text-fg/30">—</span>}
                  </td>
                  <td className="px-3 py-2.5 text-right tabular-nums">{m.presentDays}</td>
                  {/* Absences are the reason anybody opens this page, so they carry colour and the
                      other columns do not. A zero stays grey — nothing to look at is the good case. */}
                  <td className={"px-3 py-2.5 text-right tabular-nums " + (m.absentDays > 0 ? "text-amber-300" : "text-fg/30")}>
                    {m.absentDays}
                  </td>
                  <td className="px-3 py-2.5 text-right tabular-nums">{m.leaveDays || <span className="text-fg/30">0</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <p className="mt-3 text-xs text-fg/40">
        &ldquo;Today&rdquo; is live regardless of the month selected — it is what is happening now, not part of the total.
      </p>
    </div>
  );
}
