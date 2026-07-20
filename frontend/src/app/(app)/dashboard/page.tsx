"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Users, MailPlus, Building2, ArrowRight } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { DashboardSummary } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

export default function DashboardPage() {
  const { me } = useSession();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .dashboardSummary()
      .then(setSummary)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load dashboard"))
      .finally(() => setLoading(false));
  }, []);

  const isAdmin = me?.user.role === "OWNER" || me?.user.role === "ADMIN";

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">
        Welcome{me ? `, ${me.user.firstName}` : ""}.
      </h1>
      <p className="mt-1 text-white/50">Here&apos;s your company at a glance.</p>

      {error && (
        <Alert tone="error" className="mt-6">
          {error}
        </Alert>
      )}

      <div className="mt-8 grid gap-5 sm:grid-cols-3">
        <StatCard
          icon={<Building2 className="h-5 w-5 text-aqua" />}
          label="Company"
          value={loading ? null : summary?.companyName ?? "—"}
        />
        <StatCard
          icon={<Users className="h-5 w-5 text-violet" />}
          label="Active members"
          value={loading ? null : String(summary?.memberCount ?? 0)}
        />
        <StatCard
          icon={<MailPlus className="h-5 w-5 text-emerald-400" />}
          label="Pending invites"
          value={loading ? null : String(summary?.pendingInviteCount ?? 0)}
        />
      </div>

      {isAdmin && (
        <Card className="mt-8 flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <CardTitle>Grow your team</CardTitle>
            <p className="mt-1 text-sm text-white/60">Invite employees and manage their roles.</p>
          </div>
          <Link
            href="/members"
            className="inline-flex items-center gap-1.5 rounded-lg bg-violet px-4 py-2 text-sm font-medium text-white hover:bg-violet/90"
          >
            Manage members <ArrowRight className="h-4 w-4" />
          </Link>
        </Card>
      )}
    </div>
  );
}

function StatCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: string | null }) {
  return (
    <Card>
      <div className="flex items-center gap-2 text-sm text-white/50">
        {icon}
        {label}
      </div>
      {value === null ? (
        <div className="mt-3 h-7 w-24 animate-pulse rounded bg-white/10" />
      ) : (
        <p className="mt-2 truncate text-2xl font-semibold">{value}</p>
      )}
    </Card>
  );
}
