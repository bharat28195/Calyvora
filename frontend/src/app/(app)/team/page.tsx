"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, CalendarCheck, Palmtree, Receipt, Users } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { TeamSummary } from "@/lib/types";
import { money } from "@/lib/format";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { NoTeam, ScopeToggle, TeamHeader, useTeamStanding } from "@/components/team/team-bits";

/**
 * My team — the roster, and how each person's month is going.
 *
 * Belongs to anyone with reports rather than to a role. A senior engineer with two interns leads a
 * team whatever their title says; the server decides from the reporting tree (OrgScope) and answers
 * an empty roster to somebody who leads nobody, so this page is safe to reach either way.
 *
 * There is no pay on it, and there must not be. A lead needs to know somebody was absent nine days;
 * what the company pays them is HR's business. That line is drawn on the server, in TeamService.
 */
export default function TeamPage() {
  const standing = useTeamStanding();
  const [direct, setDirect] = useState(false);
  const [summary, setSummary] = useState<TeamSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setSummary(await api.teamSummary({ direct }));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load your team");
    }
  }, [direct]);

  useEffect(() => { void load(); }, [load]);

  if (standing && !standing.leadsTeam) {
    return (
      <div>
        <TeamHeader title="My team" blurb="The people who report to you." />
        <NoTeam />
      </div>
    );
  }

  return (
    <div>
      <TeamHeader title="My team" blurb="Attendance, time off, expenses and reviews for the people you lead.">
        <ScopeToggle direct={direct} onChange={setDirect} standing={standing} />
      </TeamHeader>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat icon={<Users className="h-5 w-5 text-violet" />} label="People"
          value={summary?.members.length ?? 0} sub={direct ? "direct reports" : "in your org"} />
        <Stat icon={<CalendarCheck className="h-5 w-5 text-emerald-400" />} label="In today"
          value={summary?.presentToday ?? 0} sub={`${summary?.onLeaveToday ?? 0} on leave`} />
        <Stat icon={<Palmtree className="h-5 w-5 text-amber-400" />} label="Leave to decide"
          value={summary?.pendingLeaveRequests ?? 0} sub="waiting on you" href="/people/time-off" />
        <Stat icon={<Receipt className="h-5 w-5 text-aqua" />} label="Open claims"
          value={summary?.openExpenseClaims ?? 0} sub="not yet reimbursed" href="/team/expenses" />
      </div>

      <div className="mt-8 overflow-x-auto">
        {summary === null ? (
          <div className="flex justify-center py-10"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
        ) : summary.members.length === 0 ? (
          <NoTeam />
        ) : (
          <table className="w-full min-w-[820px] text-sm">
            <thead className="text-left text-xs uppercase tracking-wide text-fg/40">
              <tr>
                <th className="px-3 py-2 font-medium">Name</th>
                <th className="px-3 py-2 font-medium">Today</th>
                <th className="px-3 py-2 font-medium text-right">Present</th>
                <th className="px-3 py-2 font-medium text-right">Absent</th>
                <th className="px-3 py-2 font-medium text-right">On leave</th>
                <th className="px-3 py-2 font-medium text-right">Claims</th>
                <th className="px-3 py-2 font-medium">Review</th>
              </tr>
            </thead>
            <tbody>
              {summary.members.map((m) => (
                <tr key={m.employeeId} className="border-t border-fg/5">
                  <td className="px-3 py-2.5">
                    <div className="font-medium">{m.name}</div>
                    <div className="text-xs text-fg/40">
                      {[m.jobTitle, m.department].filter(Boolean).join(" · ") || "—"}
                      {/* Says whose report they are when the list reaches past the first level, so a
                          skip-level lead can tell their own five from the other twenty-five. */}
                      {!m.direct && m.managerName ? ` · reports to ${m.managerName}` : ""}
                    </div>
                  </td>
                  <td className="px-3 py-2.5">{m.todayStatus ? <Badge value={m.todayStatus} /> : <span className="text-fg/30">—</span>}</td>
                  <td className="px-3 py-2.5 text-right tabular-nums">{m.presentDays}</td>
                  <td className="px-3 py-2.5 text-right tabular-nums">{m.absentDays || <span className="text-fg/30">0</span>}</td>
                  <td className="px-3 py-2.5 text-right tabular-nums">{m.leaveDays || <span className="text-fg/30">0</span>}</td>
                  <td className="px-3 py-2.5 text-right tabular-nums">
                    {m.openExpenseClaims > 0
                      ? <span title={`${m.openExpenseClaims} open`}>{money(m.openExpenseAmount)}</span>
                      : <span className="text-fg/30">—</span>}
                  </td>
                  <td className="px-3 py-2.5">
                    {m.reviewStatus ? <Badge value={m.reviewStatus} /> : <span className="text-fg/30">—</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {summary && summary.members.length > 0 && (
        <p className="mt-3 text-xs text-fg/40">
          Attendance figures cover {summary.month}. Salary and payslips are not shown here — those stay with HR.
        </p>
      )}
    </div>
  );
}

function Stat({ icon, label, value, sub, href }: {
  icon: React.ReactNode; label: string; value: number; sub?: string; href?: string;
}) {
  const card = (
    <Card className={href ? "transition-colors hover:border-fg/20" : undefined}>
      <div className="flex items-center gap-2 text-sm text-fg/50">{icon}{label}</div>
      <p className="mt-2 text-3xl font-semibold tabular-nums">{value}</p>
      {sub && <p className="mt-1 text-xs text-fg/40">{sub}</p>}
    </Card>
  );
  return href ? <Link href={href}>{card}</Link> : card;
}
