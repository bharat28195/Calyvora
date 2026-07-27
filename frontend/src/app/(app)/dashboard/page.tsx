"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  Users, MailPlus, ArrowRight, Building2, Palmtree,
  ClipboardCheck, Wallet, Receipt, Target, Clock, Inbox,
} from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { DashboardSummary, LeaveBalance } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { TeamOverviewSection } from "@/components/dashboard/team-overview";
import { WhatsComingUp } from "@/components/dashboard/whats-coming-up";
import { MyDay } from "@/components/attendance/self";

/** HR-suite dashboard: the People side of the company up front — attendance, time off, and your own day. */
export default function DashboardPage() {
  const { me } = useSession();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [balance, setBalance] = useState<LeaveBalance | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.dashboardSummary().then(setSummary).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load dashboard"));
    api.leaveBalance().then(setBalance).catch(() => {});
  }, []);

  const isAdmin = me?.user.role === "OWNER" || me?.user.role === "ADMIN";
  const leaveLeft = balance ? balance.remainingDays : null;

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">Welcome{me ? `, ${me.user.firstName}` : ""}.</h1>
      <p className="mt-1 text-fg/50">
        {summary?.companyName ? `${summary.companyName} — your people, one place.` : "Your team at a glance."}
      </p>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {isAdmin && (
        <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Stat icon={<Users className="h-5 w-5 text-violet" />} label="People" value={summary?.memberCount ?? 0}
            sub={`${summary?.departmentCount ?? 0} departments`} href="/people" />
          <Stat icon={<Building2 className="h-5 w-5 text-aqua" />} label="Departments" value={summary?.departmentCount ?? 0}
            sub="org structure" href="/people/org" />
          <Stat icon={<ClipboardCheck className="h-5 w-5 text-amber-400" />} label="Performance" value={undefined}
            sub="review cycles" href="/performance" />
          <Stat icon={<Wallet className="h-5 w-5 text-emerald-400" />} label="Payroll" value={undefined}
            sub="salaries & payslips" href="/payroll" />
        </div>
      )}

      {isAdmin && <TeamOverviewSection />}

      <div className="mt-2 grid gap-6 lg:grid-cols-3">
        {/* Time Today — the live clock + check-in/out */}
        <div className="lg:col-span-2"><MyDay /></div>
        <div className="flex flex-col gap-6"><WhatsComingUp /></div>
      </div>

      {/* Quick Access — jump straight to what you do most (Keka-style) */}
      <div className="mt-6">
        <h2 className="text-sm font-medium uppercase tracking-wide text-fg/40">Quick access</h2>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <QuickTile href="/me/attendance" icon={<Clock className="h-5 w-5 text-violet" />} label="Attendance" />
          <QuickTile href="/me/leave" icon={<Palmtree className="h-5 w-5 text-amber-400" />}
            label="Time off" sub={leaveLeft != null ? `${leaveLeft} days left` : undefined} />
          <QuickTile href="/me/payslip" icon={<Wallet className="h-5 w-5 text-emerald-400" />} label="My pay" />
          <QuickTile href="/me/review" icon={<Target className="h-5 w-5 text-aqua" />} label="My review" />
          <QuickTile href="/me/expenses" icon={<Receipt className="h-5 w-5 text-violet" />} label="Expenses" />
          <QuickTile href="/inbox" icon={<Inbox className="h-5 w-5 text-amber-400" />} label="Inbox" />
        </div>
      </div>

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

function Stat({ icon, label, value, sub, href }: {
  icon: React.ReactNode; label: string; value?: number; sub?: string; href: string;
}) {
  return (
    <Link href={href}>
      <Card className="transition-colors hover:border-fg/20">
        <div className="flex items-center gap-2 text-sm text-fg/50">{icon}{label}</div>
        {value != null
          ? <p className="mt-2 text-3xl font-semibold tabular-nums">{value}</p>
          : <p className="mt-2 flex h-9 items-center text-sm font-medium text-fg/70">Open <ArrowRight className="ml-1 h-4 w-4" /></p>}
        {sub && <p className="mt-1 text-xs text-fg/40">{sub}</p>}
      </Card>
    </Link>
  );
}

function QuickTile({ href, icon, label, sub }: { href: string; icon: React.ReactNode; label: string; sub?: string }) {
  return (
    <Link href={href}
      className="flex flex-col gap-2 rounded-xl border border-fg/10 bg-fg/[0.02] p-4 transition-colors hover:border-fg/20 hover:bg-fg/5">
      {icon}
      <span className="text-sm font-medium">{label}</span>
      {sub && <span className="-mt-1 text-xs text-fg/40">{sub}</span>}
    </Link>
  );
}
