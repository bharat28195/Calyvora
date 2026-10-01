"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, Check, X, Banknote, Receipt, ExternalLink } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { ExpenseStatus, ExpenseSummary } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { CATEGORY_LABEL, STATUS_LABEL, StatusChip, money } from "@/components/expenses/expense-bits";

const FILTERS: (ExpenseStatus | "ALL")[] = ["SUBMITTED", "APPROVED", "REIMBURSED", "REJECTED", "ALL"];

/**
 * The company expense queue (Owner/Admin). Approving and paying are separate actions on purpose —
 * "approved but not yet paid" is the state people chase, so it stays visible until money moves.
 */
export default function ExpensesQueuePage() {
  const [data, setData] = useState<ExpenseSummary | null>(null);
  const [filter, setFilter] = useState<ExpenseStatus | "ALL">("SUBMITTED");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(() => {
    api.allExpenses()
      .then(setData)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load claims"));
  }, []);

  useEffect(() => load(), [load]);

  async function act(id: string, action: "approve" | "reject" | "reimburse") {
    setBusy(id);
    try {
      await api.decideExpense(id, action);
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to update the claim");
    } finally {
      setBusy(null);
    }
  }

  const claims = useMemo(
    () => (data?.claims ?? []).filter((c) => filter === "ALL" || c.status === filter),
    [data, filter],
  );

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Expenses</h1>
        <p className="mt-1 text-fg/50">Claims from the team — approve them, then mark them paid.</p>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      <div className="mt-8 grid gap-4 sm:grid-cols-3">
        <Tile label="Awaiting approval" value={data ? money(data.pendingAmount, data.currency) : null}
          tone="text-amber-400" />
        <Tile label="Approved, not yet paid" value={data ? money(data.awaitingReimbursement, data.currency) : null}
          tone="text-sky-400" />
        <Tile label="Reimbursed this year" value={data ? money(data.reimbursedThisYear, data.currency) : null}
          tone="text-emerald-400" />
      </div>

      <div className="mt-8 flex flex-wrap gap-1.5">
        {FILTERS.map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`rounded-lg px-3 py-1.5 text-sm transition-colors ${
              filter === f ? "bg-violet/10 font-medium text-violet" : "text-fg/50 hover:bg-fg/5 hover:text-fg"
            }`}
          >
            {f === "ALL" ? "All" : STATUS_LABEL[f]}
          </button>
        ))}
      </div>

      {data === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : claims.length === 0 ? (
        <Card className="mt-4 text-center">
          <Receipt className="mx-auto h-8 w-8 text-fg/20" />
          <p className="mt-3 text-sm text-fg/50">Nothing here.</p>
        </Card>
      ) : (
        <div className="mt-4 flex flex-col gap-2">
          {claims.map((c) => (
            <Card key={c.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">
                  {c.title}
                  <span className="ml-2 text-xs font-normal text-fg/40">{c.employeeName}</span>
                </p>
                <p className="truncate text-xs text-fg/40">
                  {CATEGORY_LABEL[c.category]} · spent {c.spentOn}
                  {c.description && <> · {c.description}</>}
                </p>
              </div>
              <div className="flex shrink-0 flex-wrap items-center gap-2">
                <span className="text-sm font-medium tabular-nums">{money(c.amount, c.currency)}</span>
                {c.receiptUrl && (
                  <a href={c.receiptUrl} target="_blank" rel="noreferrer" className="text-fg/40 hover:text-fg"
                    aria-label="Open receipt">
                    <ExternalLink className="h-4 w-4" />
                  </a>
                )}
                {busy === c.id ? (
                  <Loader2 className="h-4 w-4 animate-spin text-violet" />
                ) : c.status === "SUBMITTED" ? (
                  <>
                    <Button size="sm" variant="secondary" onClick={() => act(c.id, "reject")}>
                      <X className="h-4 w-4" /> Reject
                    </Button>
                    <Button size="sm" onClick={() => act(c.id, "approve")}>
                      <Check className="h-4 w-4" /> Approve
                    </Button>
                  </>
                ) : c.status === "APPROVED" ? (
                  <Button size="sm" onClick={() => act(c.id, "reimburse")}>
                    <Banknote className="h-4 w-4" /> Mark paid
                  </Button>
                ) : (
                  <StatusChip status={c.status} />
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

function Tile({ label, value, tone = "" }: { label: string; value: string | null; tone?: string }) {
  return (
    <Card>
      <p className="text-sm text-fg/50">{label}</p>
      {value === null
        ? <div className="mt-2 h-7 w-24 animate-pulse rounded bg-fg/10" />
        : <p className={`mt-1 text-xl font-semibold tabular-nums ${tone}`}>{value}</p>}
    </Card>
  );
}
