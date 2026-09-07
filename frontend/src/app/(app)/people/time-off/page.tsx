"use client";

import { useCallback, useEffect, useState } from "react";
import { Check, X } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { LeaveRequest } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { MyLeave } from "@/components/leave/my-leave";
import { CompOffApprovals, MyCompOff } from "@/components/leave/comp-off";
import { useTeamStanding } from "@/components/team/team-bits";

/**
 * Time off: your own balance and requests (shared with the Me hub) plus, for anyone who approves, the
 * queue waiting on a decision.
 *
 * <p>Who sees the queue is deliberately the same set the API allows, and it was not before. HR could
 * approve leave through the API and never saw the queue on this screen, because the check here was
 * Owner/Admin only — so the one role whose job this is had to be told to use a screen that did not
 * show it.
 *
 * <p>The manager half is no longer a role check either. Anybody with people under them can decide
 * their requests — a senior engineer with two interns leads a team whatever their title says — so the
 * gate here is "do you have reports", the same question the server asks (OrgScope). Keying it on the
 * MANAGER role would have recreated the exact gap described above, for a different role.
 */
export default function TimeOffPage() {
  const { me } = useSession();
  const standing = useTeamStanding();
  const role = me?.user.role;
  const canApprove = role === "OWNER" || role === "ADMIN" || role === "HR" || !!standing?.leadsTeam;

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Time off</h1>
        <p className="mt-1 text-fg/50">Request leave and track your balance.</p>
      </div>

      <MyLeave />
      <MyCompOff />
      {canApprove && <CompOffApprovals />}
      {canApprove && <Approvals />}
    </div>
  );
}

function Approvals() {
  const [inbox, setInbox] = useState<LeaveRequest[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setInbox(await api.allLeave());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load requests");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function decide(id: string, action: "approve" | "reject") {
    try {
      await (action === "approve" ? api.approveLeave(id) : api.rejectLeave(id));
      await load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to update request");
    }
  }

  const pending = inbox.filter((r) => r.status === "PENDING");

  return (
    <div className="mt-10">
      <h2 className="text-sm font-medium uppercase tracking-wide text-fg/40">Approvals</h2>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      <div className="mt-3 flex flex-col gap-2">
        {pending.length === 0 ? (
          <Card className="text-sm text-fg/50">No requests waiting for approval.</Card>
        ) : (
          pending.map((r) => (
            <Card key={r.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <p className="text-sm font-medium">{r.employeeName}</p>
                <p className="text-xs text-fg/50">
                  <span className="capitalize">{r.type.toLowerCase()}</span> · {r.days}d · {r.startDate} → {r.endDate}
                  {r.reason && <> · {r.reason}</>}
                </p>
              </div>
              <div className="flex gap-2">
                <Button size="sm" variant="secondary" onClick={() => decide(r.id, "reject")}>
                  <X className="h-4 w-4" /> Reject
                </Button>
                <Button size="sm" onClick={() => decide(r.id, "approve")}>
                  <Check className="h-4 w-4" /> Approve
                </Button>
              </div>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
