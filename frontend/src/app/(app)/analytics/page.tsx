"use client";

import { useEffect, useState } from "react";
import { Loader2, Users, Wallet, Gauge } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { AnalyticsOverview } from "@/lib/types";
import { money as fmtMoney } from "@/lib/format";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Donut, BarList, TrendLine, PALETTE } from "@/components/charts/charts";

/**
 * Insights — HR analytics for the People and Finance sides of the company. Every chart is computed
 * from data we hold (headcount from start dates, approved leave, ratings, expenses), so the numbers
 * are real, not illustrative. Owner/Admin only. (The HR-suite branch omits the Work/sprint charts.)
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

  const { people, finance } = data;
  const money = (n: number) => fmtMoney(n);

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
        <Kpi label="Goals achieved" value={people.goalsAchieved} hint={`avg progress ${people.avgGoalProgress}%`} />
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

function sum(s: { value: number }[]): number { return s.reduce((a, b) => a + b.value, 0); }

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
