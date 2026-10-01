"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, ArrowRight } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { LeaveRequest } from "@/lib/types";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { NoTeam, ScopeToggle, TeamHeader, useTeamStanding } from "@/components/team/team-bits";

/**
 * The team's time off — every request, in every state.
 *
 * Read-only on purpose. Deciding happens on Leave approvals, which is a queue you work through;
 * mixing the two would put an approve button next to leave that was decided three months ago, and
 * the history is what this page is for.
 */
export default function TeamLeavePage() {
  const standing = useTeamStanding();
  const [direct, setDirect] = useState(false);
  const [requests, setRequests] = useState<LeaveRequest[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setRequests(await api.teamLeave(direct));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load time off");
    }
  }, [direct]);

  useEffect(() => { void load(); }, [load]);

  if (standing && !standing.leadsTeam) {
    return <div><TeamHeader title="Team time off" blurb="Leave across the people you lead." /><NoTeam /></div>;
  }

  const pending = requests?.filter((r) => r.status === "PENDING").length ?? 0;

  return (
    <div>
      <TeamHeader title="Team time off" blurb="Every leave request from the people you lead, decided or not.">
        <ScopeToggle direct={direct} onChange={setDirect} standing={standing} />
      </TeamHeader>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {pending > 0 && (
        <Card className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm">
            <span className="font-medium">{pending}</span> request{pending === 1 ? "" : "s"} waiting on a decision.
          </p>
          <Link href="/people/time-off" className="inline-flex items-center gap-1.5 text-sm font-medium text-violet hover:underline">
            Go to approvals <ArrowRight className="h-4 w-4" />
          </Link>
        </Card>
      )}

      <div className="mt-6 flex flex-col gap-2">
        {requests === null ? (
          <div className="flex justify-center py-10"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
        ) : requests.length === 0 ? (
          <Card className="text-sm text-fg/50">Nobody on your team has requested time off.</Card>
        ) : (
          requests.map((r) => (
            <Card key={r.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <div className="font-medium">{r.employeeName}</div>
                <div className="text-xs text-fg/50">
                  {r.type.replace(/_/g, " ").toLowerCase()} · {r.startDate} → {r.endDate} · {r.days} day{r.days === 1 ? "" : "s"}
                </div>
                {r.reason && <p className="mt-1 text-sm text-fg/60">{r.reason}</p>}
              </div>
              <Badge value={r.status} />
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
