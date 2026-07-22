"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { CalendarCheck, Palmtree, Target, Receipt, FileText, ArrowRight } from "lucide-react";
import { api } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { AttendanceMonth, Goal, LeaveBalance } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { MyDay } from "@/components/attendance/self";
import { WhatsComingUp } from "@/components/dashboard/whats-coming-up";

/**
 * The Me hub — everything about *you* in one place, whatever your role: today's attendance, your
 * leave balance, the goals your manager set you, and your claims. The sub-panes in the left nav go
 * deeper; this page is the summary you land on.
 */
export default function MePage() {
  const { me } = useSession();
  const [balance, setBalance] = useState<LeaveBalance | null>(null);
  const [month, setMonth] = useState<AttendanceMonth | null>(null);
  const [goals, setGoals] = useState<Goal[] | null>(null);

  useEffect(() => {
    api.leaveBalance().then(setBalance).catch(() => {});
    api.myAttendance().then(setMonth).catch(() => {});
    api.myGoals().then(setGoals).catch(() => setGoals([]));
  }, []);

  const openGoals = goals?.filter((g) => g.status === "OPEN") ?? [];

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">
          {me ? `Hello, ${me.user.firstName}` : "Me"}
        </h1>
        <p className="mt-1 text-fg/50">Your day, your time off, your goals and your claims.</p>
      </div>

      <MyDay />

      <div className="mt-6 grid gap-4 sm:grid-cols-3">
        <StatLink
          href="/me/attendance"
          icon={<CalendarCheck className="h-5 w-5 text-emerald-400" />}
          label="Attendance this month"
          value={month ? `${month.attendanceRate ?? 0}%` : null}
          hint={month ? `${month.workedDays} of ${month.expectedDays} days` : undefined}
        />
        <StatLink
          href="/me/leave"
          icon={<Palmtree className="h-5 w-5 text-amber-400" />}
          label="Leave remaining"
          value={balance ? `${balance.remainingDays}d` : null}
          hint={balance ? `${balance.usedDays}d used · ${balance.pendingDays}d pending` : undefined}
        />
        <StatLink
          href="/me/performance"
          icon={<Target className="h-5 w-5 text-violet" />}
          label="Open goals"
          value={goals ? String(openGoals.length) : null}
          hint={openGoals[0]?.title}
        />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <WhatsComingUp />

        <Card>
          <CardTitle>Quick links</CardTitle>
          <div className="mt-3 flex flex-col divide-y divide-fg/5">
            <QuickLink href="/me/expenses" icon={<Receipt className="h-4 w-4 text-sky-400" />}
              label="Claim an expense" hint="Travel, meals, anything you paid for" />
            <QuickLink href="/me/leave" icon={<Palmtree className="h-4 w-4 text-amber-400" />}
              label="Request time off" hint="Your balance and past requests" />
            <QuickLink href="/people/holidays" icon={<FileText className="h-4 w-4 text-violet" />}
              label="Holiday calendar" hint="When the office is closed" />
          </div>
        </Card>
      </div>
    </div>
  );
}

function StatLink({
  href, icon, label, value, hint,
}: { href: string; icon: React.ReactNode; label: string; value: string | null; hint?: string }) {
  return (
    <Link href={href}>
      <Card className="h-full transition-colors hover:border-fg/25">
        <div className="flex items-center gap-2 text-sm text-fg/50">{icon}{label}</div>
        {value === null
          ? <div className="mt-3 h-7 w-16 animate-pulse rounded bg-fg/10" />
          : <p className="mt-2 text-3xl font-semibold tabular-nums">{value}</p>}
        {hint && <p className="mt-1 truncate text-xs text-fg/40">{hint}</p>}
      </Card>
    </Link>
  );
}

function QuickLink({
  href, icon, label, hint,
}: { href: string; icon: React.ReactNode; label: string; hint: string }) {
  return (
    <Link href={href} className="flex items-center gap-3 py-2.5 hover:opacity-80">
      <span className="shrink-0">{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm">{label}</span>
        <span className="block truncate text-xs text-fg/40">{hint}</span>
      </span>
      <ArrowRight className="h-4 w-4 shrink-0 text-fg/30" />
    </Link>
  );
}
