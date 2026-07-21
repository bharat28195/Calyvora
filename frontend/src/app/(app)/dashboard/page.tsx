"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  Users, MailPlus, Building2, ArrowRight, FolderKanban, CircleDot,
  LifeBuoy, BookOpen, FileText, CheckCircle2, Network,
} from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { DashboardSummary, Task, PageSummary } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { TeamOverviewSection } from "@/components/dashboard/team-overview";

export default function DashboardPage() {
  const { me } = useSession();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [myTasks, setMyTasks] = useState<Task[] | null>(null);
  const [recentPages, setRecentPages] = useState<PageSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      api.dashboardSummary().then(setSummary),
      api.myTasks().then(setMyTasks).catch(() => setMyTasks([])),
      api.myPages().then(setRecentPages).catch(() => setRecentPages([])),
    ])
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load dashboard"))
      .finally(() => setLoading(false));
  }, []);

  const isAdmin = me?.user.role === "OWNER" || me?.user.role === "ADMIN";
  const sprint = summary?.activeSprint ?? null;
  const sprintPct = sprint && sprint.total > 0 ? Math.round((sprint.done / sprint.total) * 100) : 0;

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">
        Welcome{me ? `, ${me.user.firstName}` : ""}.
      </h1>
      <p className="mt-1 text-fg/50">
        {summary?.companyName ? `${summary.companyName} — your whole company, one login.` : "Your company at a glance."}
      </p>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {/* Company-wide KPIs + team overview are Owner/Admin only (founder feedback B1). */}
      {isAdmin && (
        <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Stat icon={<Users className="h-5 w-5 text-violet" />} label="People" value={summary?.memberCount}
            sub={`${summary?.departmentCount ?? 0} departments`} href="/people" loading={loading} />
          <Stat icon={<CircleDot className="h-5 w-5 text-aqua" />} label="Open tasks" value={summary?.openTaskCount}
            sub={`${summary?.doneTaskCount ?? 0} done`} href="/work" loading={loading} />
          <Stat icon={<LifeBuoy className="h-5 w-5 text-amber-400" />} label="Open tickets" value={summary?.openTicketCount}
            sub={`${summary?.projectCount ?? 0} projects`} href="/work" loading={loading} />
          <Stat icon={<BookOpen className="h-5 w-5 text-emerald-400" />} label="Knowledge pages" value={summary?.pageCount}
            sub={`${summary?.spaceCount ?? 0} spaces`} href="/knowledge" loading={loading} />
        </div>
      )}

      {isAdmin && <TeamOverviewSection />}

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        {/* My open work */}
        <Card className="lg:col-span-2">
          <div className="flex items-center justify-between">
            <CardTitle>My open work</CardTitle>
            <Link href="/work/mine" className="text-sm text-fg/50 hover:text-fg">View all</Link>
          </div>
          <div className="mt-4 flex flex-col divide-y divide-fg/5">
            {loading ? (
              <SkeletonRows />
            ) : myTasks && myTasks.length > 0 ? (
              myTasks.slice(0, 6).map((t) => (
                <Link key={t.id} href={`/work/${t.projectId}`}
                  className="flex items-center gap-3 py-2.5 -mx-2 px-2 rounded-lg hover:bg-fg/5">
                  <PriorityDot priority={t.priority} />
                  <span className="font-mono text-xs text-fg/40 w-16 shrink-0">{t.ref}</span>
                  <span className="flex-1 truncate text-sm">{t.title}</span>
                  <StatusChip status={t.status} />
                </Link>
              ))
            ) : (
              <Empty icon={<CheckCircle2 className="h-5 w-5" />} text="You're all caught up — no open tasks." />
            )}
          </div>
        </Card>

        {/* Active sprint + cross-app callout */}
        <div className="flex flex-col gap-6">
          <Card>
            <CardTitle>Active sprint</CardTitle>
            {loading ? (
              <div className="mt-4 h-16 animate-pulse rounded bg-fg/5" />
            ) : sprint ? (
              <div className="mt-4">
                <p className="text-sm font-medium">{sprint.name}</p>
                <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-fg/10">
                  <div className="h-full rounded-full bg-gradient-to-r from-violet to-aqua transition-all"
                    style={{ width: `${sprintPct}%` }} />
                </div>
                <p className="mt-2 text-xs text-fg/50">
                  {sprint.done} of {sprint.total} tasks done · <span className="text-fg/80">{sprintPct}%</span>
                </p>
              </div>
            ) : (
              <Empty icon={<FolderKanban className="h-5 w-5" />} text="No sprint running." />
            )}
          </Card>

          <Card className="bg-gradient-to-br from-violet/10 to-transparent">
            <div className="flex items-center gap-2 text-sm text-fg/60">
              <Network className="h-4 w-4 text-violet" /> One connected graph
            </div>
            <p className="mt-2 text-sm text-fg/80">
              People, Work &amp; Knowledge share one org graph — a doc&apos;s author is an employee,
              and it links the very task it documents.
            </p>
          </Card>
        </div>
      </div>

      {/* Recent knowledge */}
      <Card className="mt-6">
        <div className="flex items-center justify-between">
          <CardTitle>Recent knowledge</CardTitle>
          <Link href="/knowledge" className="text-sm text-fg/50 hover:text-fg">Browse spaces</Link>
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          {loading ? (
            <SkeletonRows />
          ) : recentPages && recentPages.length > 0 ? (
            recentPages.slice(0, 4).map((p) => (
              <Link key={p.id} href={`/knowledge/${p.spaceId}`}
                className="flex items-start gap-3 rounded-lg border border-fg/5 bg-fg/[0.02] p-3 hover:bg-fg/5">
                <FileText className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{p.title}</p>
                  <p className="mt-0.5 truncate text-xs text-fg/40">
                    {p.spaceName ?? "Space"}{p.linkedTaskRef ? ` · linked to ${p.linkedTaskRef}` : ""}
                  </p>
                </div>
              </Link>
            ))
          ) : (
            <Empty icon={<BookOpen className="h-5 w-5" />} text="No pages yet." />
          )}
        </div>
      </Card>

      {isAdmin && (
        <Card className="mt-6 flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <CardTitle>Grow your team</CardTitle>
            <p className="mt-1 text-sm text-fg/60">
              Invite employees and manage their roles.
              {(summary?.pendingInviteCount ?? 0) > 0 && ` ${summary?.pendingInviteCount} invite(s) pending.`}
            </p>
          </div>
          <Link href="/members"
            className="inline-flex items-center gap-1.5 rounded-lg bg-violet px-4 py-2 text-sm font-medium text-white hover:bg-violet/90">
            <MailPlus className="h-4 w-4" /> Manage members <ArrowRight className="h-4 w-4" />
          </Link>
        </Card>
      )}
    </div>
  );
}

function Stat({ icon, label, value, sub, href, loading }: {
  icon: React.ReactNode; label: string; value?: number; sub?: string; href: string; loading: boolean;
}) {
  return (
    <Link href={href}>
      <Card className="transition-colors hover:border-fg/20">
        <div className="flex items-center gap-2 text-sm text-fg/50">{icon}{label}</div>
        {loading ? (
          <div className="mt-3 h-7 w-16 animate-pulse rounded bg-fg/10" />
        ) : (
          <p className="mt-2 text-3xl font-semibold tabular-nums">{value ?? 0}</p>
        )}
        {sub && !loading && <p className="mt-1 text-xs text-fg/40">{sub}</p>}
      </Card>
    </Link>
  );
}

function PriorityDot({ priority }: { priority: string }) {
  const color =
    priority === "URGENT" ? "bg-red-500" : priority === "HIGH" ? "bg-amber-400"
    : priority === "MEDIUM" ? "bg-aqua" : "bg-fg/30";
  return <span className={`h-2 w-2 shrink-0 rounded-full ${color}`} title={priority} />;
}

function StatusChip({ status }: { status: string }) {
  const map: Record<string, string> = {
    TODO: "bg-fg/10 text-fg/60",
    IN_PROGRESS: "bg-aqua/15 text-aqua",
    DONE: "bg-emerald-500/15 text-emerald-400",
  };
  const label = status === "IN_PROGRESS" ? "In progress" : status.charAt(0) + status.slice(1).toLowerCase();
  return <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs ${map[status] ?? "bg-fg/10"}`}>{label}</span>;
}

function Empty({ icon, text }: { icon: React.ReactNode; text: string }) {
  return (
    <div className="flex items-center gap-2 py-6 text-sm text-fg/40">
      {icon}{text}
    </div>
  );
}

function SkeletonRows() {
  return (
    <>
      {[0, 1, 2].map((i) => (
        <div key={i} className="h-9 animate-pulse rounded bg-fg/5" style={{ marginTop: i ? 8 : 0 }} />
      ))}
    </>
  );
}
