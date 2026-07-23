"use client";

import { useEffect, useState } from "react";
import { Loader2, Users, FolderKanban, Wallet, Gauge } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { AnalyticsOverview } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Donut, BarList, MiniBars, TrendLine, PALETTE } from "@/components/charts/charts";

/**
 * Insights — a company-wide analytics dashboard reaching across People, Work and Finance. Every chart
 * is computed from data we hold (headcount from start dates, velocity from completed sprints), so the
 * numbers are real, not illustrative. Owner/Admin only.
 */
export default function AnalyticsPage() {
  const [data, setData] = useState<AnalyticsOverview | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.analyticsOverview().then(setData)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load analytics"));
  }, []);

  if (error) return <Alert tone="error" className="mt-6">{error}</Alert>;
  if (!data) return <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;

  const { people, work, finance } = data;
  const money = (n: number) => {
    try { return new Intl.NumberFormat(undefined, { style: "currency", currency: finance.currency, maximumFractionDigits: 0 }).format(n); }
    catch { return `${finance.currency} ${Math.round(n).toLocaleString()}`; }
  };

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Insights</h1>
        <p className="mt-1 text-fg/50">Company analytics across People, Work and Finance — all from live data.</p>
      </div>

      {/* KPI strip */}
      <div className="mt-6 grid grid-cols-2 gap-3 md:grid-cols-4">
        <Kpi label="Headcount" value={people.headcount} hint={`${people.newJoinersThisYear} joined this year`} />
        <Kpi label="Avg tenure" value={`${people.avgTenureMonths} mo`} hint={`${people.onLeaveToday} on leave today`} />
        <Kpi label="Open work" value={openTasks(work)} hint={`${work.projects} projects`} />
        <Kpi label="Awaiting reimbursement" value={money(finance.awaitingReimbursement)} hint={`${money(finance.pending)} pending approval`} />
      </div>

      {/* People */}
      <SectionTitle icon={<Users className="h-5 w-5 text-violet" />}>People</SectionTitle>
      <div className="grid gap-4 lg:grid-cols-2">
        <Panel title="Headcount growth" subtitle="Cumulative, last 12 months">
          <TrendLine data={people.headcountGrowth} unit="people" />
        </Panel>
        <Panel title="Headcount by department">
          <BarList data={people.byDepartment} color={PALETTE[6]} />
        </Panel>
        <Panel title="Leave taken this year" subtitle="Approved days, by type">
          {sum(people.leaveByType) === 0
            ? <Empty>No approved leave yet.</Empty>
            : <Donut data={people.leaveByType} unit="days" />}
        </Panel>
        <Panel title="Performance & goals" subtitle={`Avg goal progress ${people.avgGoalProgress}%`}>
          <div className="grid grid-cols-3 gap-2 text-center">
            <Stat label="Open" value={people.goalsOpen} tone="text-aqua" />
            <Stat label="Achieved" value={people.goalsAchieved} tone="text-emerald-400" />
            <Stat label="Missed" value={people.goalsMissed} tone="text-rose-400" />
          </div>
          <div className="mt-4">
            <p className="mb-2 text-xs text-fg/40">Rating distribution</p>
            <BarList data={people.ratingDistribution} color={PALETTE[3]} unit="ppl" />
          </div>
        </Panel>
      </div>

      {/* Work */}
      <SectionTitle icon={<FolderKanban className="h-5 w-5 text-aqua" />}>Work</SectionTitle>
      <div className="grid gap-4 lg:grid-cols-2">
        <Panel title="Tasks by status">
          {sum(work.tasksByStatus) === 0 ? <Empty>No tasks yet.</Empty> : <Donut data={work.tasksByStatus} unit="tasks" />}
        </Panel>
        <Panel title="Tasks by priority">
          <BarList data={work.tasksByPriority} color={PALETTE[4]} />
        </Panel>
        <Panel title="Sprint velocity" subtitle="Completed story points per finished sprint">
          <MiniBars data={work.velocity} color={PALETTE[0]} unit="pts" />
        </Panel>
        <Panel title="Active sprint" subtitle={work.activeSprint?.name ?? "No sprint running"}>
          {work.activeSprint ? (
            <div>
              <div className="grid grid-cols-3 gap-2 text-center">
                <Stat label="Committed" value={work.activeSprint.committed} tone="text-fg/80" />
                <Stat label="Done" value={work.activeSprint.done} tone="text-emerald-400" />
                <Stat label="Remaining" value={work.activeSprint.remaining} tone="text-amber-400" />
              </div>
              <div className="mt-3 h-3 overflow-hidden rounded-full bg-fg/10">
                <div className="h-full rounded-full bg-gradient-to-r from-violet to-aqua"
                  style={{ width: `${pct(work.activeSprint.done, work.activeSprint.committed)}%` }} />
              </div>
              <p className="mt-2 text-xs text-fg/40">
                {pct(work.activeSprint.done, work.activeSprint.committed)}% of committed points done
                {work.activeSprint.unestimated > 0 && ` · ${work.activeSprint.unestimated} tasks unestimated`}
              </p>
            </div>
          ) : <Empty>Start a sprint to see burn-up here.</Empty>}
        </Panel>
        <Panel title="Support tickets by status" className="lg:col-span-2">
          <BarList data={work.ticketsByStatus} color={PALETTE[1]} />
        </Panel>
      </div>

      {/* Finance */}
      <SectionTitle icon={<Wallet className="h-5 w-5 text-emerald-400" />}>Finance</SectionTitle>
      <div className="grid gap-4 lg:grid-cols-2">
        <Panel title="Expenses by category" subtitle="This year, excluding rejected">
          {sum(finance.byCategory) === 0 ? <Empty>No claims yet.</Empty> : <Donut data={finance.byCategory} unit={finance.currency} />}
        </Panel>
        <Panel title="Reimbursement pipeline">
          <div className="grid grid-cols-3 gap-2 text-center">
            <Stat label="Pending" value={money(finance.pending)} tone="text-amber-400" small />
            <Stat label="Approved" value={money(finance.awaitingReimbursement)} tone="text-violet" small />
            <Stat label="Paid (yr)" value={money(finance.reimbursedThisYear)} tone="text-emerald-400" small />
          </div>
          <p className="mt-4 flex items-center gap-1.5 text-xs text-fg/40">
            <Gauge className="h-4 w-4" /> Approved-but-unpaid is what people chase — keep it moving.
          </p>
        </Panel>
      </div>
    </div>
  );
}

function openTasks(work: AnalyticsOverview["work"]): number {
  return work.tasksByStatus.filter((s) => s.label !== "Done").reduce((a, b) => a + b.value, 0);
}
function sum(s: { value: number }[]): number { return s.reduce((a, b) => a + b.value, 0); }
function pct(a: number, b: number): number { return b === 0 ? 0 : Math.round((a / b) * 100); }

function Kpi({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <Card className="py-4">
      <p className="text-xs text-fg/50">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
      {hint && <p className="mt-0.5 text-xs text-fg/40">{hint}</p>}
    </Card>
  );
}

function SectionTitle({ icon, children }: { icon: React.ReactNode; children: React.ReactNode }) {
  return <h2 className="mb-3 mt-9 flex items-center gap-2 text-lg font-semibold">{icon} {children}</h2>;
}

function Panel({ title, subtitle, children, className }: {
  title: string; subtitle?: string; children: React.ReactNode; className?: string;
}) {
  return (
    <Card className={className}>
      <CardTitle>{title}</CardTitle>
      {subtitle && <p className="mt-0.5 text-sm text-fg/50">{subtitle}</p>}
      <div className="mt-4">{children}</div>
    </Card>
  );
}

function Stat({ label, value, tone, small }: { label: string; value: string | number; tone: string; small?: boolean }) {
  return (
    <div className="rounded-lg border border-fg/10 py-2">
      <p className={`${small ? "text-base" : "text-xl"} font-semibold tabular-nums ${tone}`}>{value}</p>
      <p className="text-xs text-fg/40">{label}</p>
    </div>
  );
}

function Empty({ children }: { children: React.ReactNode }) {
  return <p className="py-6 text-center text-sm text-fg/40">{children}</p>;
}
