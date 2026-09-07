"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { ExpenseClaim } from "@/lib/types";
import { money } from "@/lib/format";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { NoTeam, ScopeToggle, TeamHeader, useTeamStanding } from "@/components/team/team-bits";

/**
 * The team's expense claims.
 *
 * An expense is not pay: it is money the person is out of pocket for, and the lead who approved the
 * trip is the one who can say whether the taxi was real. So it belongs here, where salary does not.
 *
 * Read-only for now — approving a claim is still Admin/HR work (ExpenseController), so showing a
 * button that 403s would be worse than showing none. See the follow-up note in FOUNDER.md.
 */
export default function TeamExpensesPage() {
  const standing = useTeamStanding();
  const [direct, setDirect] = useState(false);
  const [claims, setClaims] = useState<ExpenseClaim[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setClaims(await api.teamExpenses(direct));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load expenses");
    }
  }, [direct]);

  useEffect(() => { void load(); }, [load]);

  if (standing && !standing.leadsTeam) {
    return <div><TeamHeader title="Team expenses" blurb="Claims from the people you lead." /><NoTeam /></div>;
  }

  const open = claims?.filter((c) => c.status === "SUBMITTED" || c.status === "APPROVED") ?? [];
  const openTotal = open.reduce((sum, c) => sum + c.amount, 0);

  return (
    <div>
      <TeamHeader title="Team expenses" blurb="What the people you lead have claimed back.">
        <ScopeToggle direct={direct} onChange={setDirect} standing={standing} />
      </TeamHeader>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {open.length > 0 && (
        <Card className="mt-6">
          <p className="text-sm text-fg/50">Not yet reimbursed</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums">{money(openTotal)}</p>
          <p className="mt-1 text-xs text-fg/40">across {open.length} claim{open.length === 1 ? "" : "s"}</p>
        </Card>
      )}

      <div className="mt-6 flex flex-col gap-2">
        {claims === null ? (
          <div className="flex justify-center py-10"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
        ) : claims.length === 0 ? (
          <Card className="text-sm text-fg/50">Nobody on your team has claimed an expense.</Card>
        ) : (
          claims.map((c) => (
            <Card key={c.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <div className="font-medium">{c.title}</div>
                <div className="text-xs text-fg/50">
                  {c.employeeName} · {c.category.replace(/_/g, " ").toLowerCase()} · {c.spentOn}
                </div>
              </div>
              <div className="flex items-center gap-3">
                <span className="tabular-nums">{money(c.amount, c.currency)}</span>
                <Badge value={c.status} />
              </div>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
