"use client";

import { useEffect, useState } from "react";
import { Loader2, Users, Wallet, Gauge, Briefcase } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { AnalyticsOverview, Profitability } from "@/lib/types";
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

      {/* Delivery economics (D2) — the join no other HR product can make. */}
      <DeliveryEconomics />

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

/**
 * What delivery cost against what it was sold for (D2).
 *
 * <p>Loaded separately from the overview so a company with no projects, or a slow first month, never
 * blocks the rest of Insights. The server's `basis` string is rendered verbatim rather than
 * paraphrased — these are allocated costs, and the screen has to say so where the numbers are.
 */
function DeliveryEconomics() {
  const [data, setData] = useState<Profitability | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.profitability()
      .then(setData)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Couldn't load delivery economics"));
  }, []);

  const money = (n: number) => fmtMoney(n);

  return (
    <>
      <SectionTitle icon={<Briefcase className="h-5 w-5 text-aqua" />}>Delivery economics</SectionTitle>
      {error && <Alert tone="error">{error}</Alert>}
      {!error && !data && (
        <div className="flex justify-center py-8"><Loader2 className="h-5 w-5 animate-spin text-violet" /></div>
      )}
      {data && (
        <>
          <div className="mb-3 grid grid-cols-2 gap-3 md:grid-cols-4">
            <Kpi label="Delivery cost" value={money(data.totalCost)}
              hint={`${data.periodStart} → ${data.periodEnd}`} />
            <Kpi label="Contracted" value={money(data.totalContractValue)}
              hint={`${data.projectsCosted} project${data.projectsCosted === 1 ? "" : "s"} costed`} />
            <Kpi label="Margin" value={money(data.totalMargin)}
              hint={data.totalContractValue === 0 ? "no fees agreed yet" : "contracted less cost"} />
            <Kpi label="Unallocated" value={money(data.unallocatedCost)}
              hint="paid, nothing closed" />
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Panel title="By project" subtitle="Most expensive first">
              {data.projects.length === 0 ? (
                <Empty>No work was closed against a project in this period.</Empty>
              ) : (
                <div className="space-y-2">
                  {data.projects.map((p) => (
                    <div key={p.projectId} className="rounded-lg border border-fg/10 p-2.5">
                      <div className="flex items-baseline gap-2">
                        <span className="font-mono text-xs text-violet">{p.projectKey}</span>
                        <span className="min-w-0 flex-1 truncate text-sm text-fg/80">{p.projectName}</span>
                        {p.billable
                          ? <span className="shrink-0 rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs text-emerald-300">Billable</span>
                          : <span className="shrink-0 rounded-full bg-fg/10 px-2 py-0.5 text-xs text-fg/40">Internal</span>}
                      </div>
                      <div className="mt-1.5 flex flex-wrap items-baseline gap-x-4 gap-y-1 text-xs text-fg/50">
                        <span>Cost this period <span className="tabular-nums text-fg/80">{money(p.cost)}</span></span>
                        {p.contractValue != null && (
                          <>
                            <span>Fee <span className="tabular-nums text-fg/80">{money(p.contractValue)}</span></span>
                            <span>Cost to date <span className="tabular-nums text-fg/80">{money(p.costToDate)}</span></span>
                          </>
                        )}
                        {p.margin != null && (
                          <span>
                            Margin{" "}
                            <span className={`tabular-nums ${p.margin >= 0 ? "text-emerald-400" : "text-rose-400"}`}>
                              {money(p.margin)}{p.marginPercent != null && ` (${p.marginPercent}%)`}
                            </span>
                          </span>
                        )}
                        {p.clientName && <span className="text-fg/35">{p.clientName}</span>}
                        <span className="text-fg/35">
                          {p.itemsDelivered} item{p.itemsDelivered === 1 ? "" : "s"} · {p.contributors} contributor{p.contributors === 1 ? "" : "s"}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Panel>

            <Panel title="By person" subtitle="Cost this period, and how much went to sold work">
              {data.people.length === 0 ? (
                <Empty>Nobody has pay on record for this period.</Empty>
              ) : (
                <div className="space-y-2">
                  {data.people.map((p) => (
                    <div key={p.employeeId} className="flex items-center gap-3 text-sm">
                      <span className="min-w-0 flex-1 truncate text-fg/75">{p.employeeName}</span>
                      <div className="h-1.5 w-24 shrink-0 overflow-hidden rounded-full bg-fg/10"
                        title={`${p.billablePercent}% of cost on billable work`}>
                        <div className="h-full rounded-full bg-emerald-400" style={{ width: `${p.billablePercent}%` }} />
                      </div>
                      <span className="w-12 shrink-0 text-right text-xs tabular-nums text-fg/40">{p.billablePercent}%</span>
                      <span className="w-24 shrink-0 text-right text-xs tabular-nums text-fg/70">{money(p.cost)}</span>
                    </div>
                  ))}
                </div>
              )}
            </Panel>
          </div>

          <p className="mt-2 text-xs leading-relaxed text-fg/35">{data.basis}</p>
        </>
      )}
    </>
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
