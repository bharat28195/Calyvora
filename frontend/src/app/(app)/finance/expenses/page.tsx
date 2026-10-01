"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, Plus, Receipt, Trash2, ExternalLink } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { ExpenseCategory, ExpenseSummary } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Modal } from "@/components/ui/modal";
import { CATEGORY_LABEL, StatusChip, money } from "@/components/expenses/expense-bits";

const CATEGORIES = Object.keys(CATEGORY_LABEL) as ExpenseCategory[];
const selectCls =
  "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

/** My expense claims — submit one, track what's approved, and see what's still owed to me. */
export default function MyExpensesPage() {
  const [data, setData] = useState<ExpenseSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [claiming, setClaiming] = useState(false);

  const load = useCallback(() => {
    api.myExpenses()
      .then(setData)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load your claims"));
  }, []);

  useEffect(() => load(), [load]);

  async function withdraw(id: string) {
    if (!confirm("Withdraw this claim?")) return;
    await api.withdrawExpense(id).catch(() => {});
    load();
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">My expenses</h1>
          <p className="mt-1 text-fg/50">Claim what you paid for — travel, meals, anything official.</p>
        </div>
        <Button onClick={() => setClaiming(true)}><Plus className="h-4 w-4" /> New claim</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      <div className="mt-8 grid gap-4 sm:grid-cols-3">
        <Tile label="Awaiting approval" value={data ? money(data.pendingAmount, data.currency) : null} />
        <Tile label="Approved, not yet paid" value={data ? money(data.awaitingReimbursement, data.currency) : null}
          tone="text-sky-400" />
        <Tile label="Reimbursed this year" value={data ? money(data.reimbursedThisYear, data.currency) : null}
          tone="text-emerald-400" />
      </div>

      {data === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : data.claims.length === 0 ? (
        <Card className="mt-8 text-center">
          <Receipt className="mx-auto h-8 w-8 text-fg/20" />
          <p className="mt-3 text-sm text-fg/50">No claims yet.</p>
          <Button className="mt-4" onClick={() => setClaiming(true)}><Plus className="h-4 w-4" /> Claim an expense</Button>
        </Card>
      ) : (
        <div className="mt-8 flex flex-col gap-2">
          {data.claims.map((c) => (
            <Card key={c.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{c.title}</p>
                <p className="truncate text-xs text-fg/40">
                  {CATEGORY_LABEL[c.category]} · {c.spentOn}
                  {c.decisionNote && <> · &ldquo;{c.decisionNote}&rdquo;</>}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                <span className="text-sm font-medium tabular-nums">{money(c.amount, c.currency)}</span>
                <StatusChip status={c.status} />
                {c.receiptUrl && (
                  <a href={c.receiptUrl} target="_blank" rel="noreferrer" className="text-fg/40 hover:text-fg"
                    aria-label="Open receipt">
                    <ExternalLink className="h-4 w-4" />
                  </a>
                )}
                {c.status === "SUBMITTED" && (
                  <button onClick={() => withdraw(c.id)} aria-label="Withdraw claim"
                    className="rounded-md p-1 text-red-400/70 hover:bg-fg/5 hover:text-red-300">
                    <Trash2 className="h-4 w-4" />
                  </button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      {claiming && <ClaimDialog onClose={() => setClaiming(false)} onDone={() => { setClaiming(false); load(); }} />}
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

function ClaimDialog({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [form, setForm] = useState({
    title: "", category: "TRAVEL" as ExpenseCategory, amount: "",
    spentOn: new Date().toISOString().slice(0, 10), description: "", receiptUrl: "",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const amount = Number(form.amount);
    if (!form.title.trim()) { setError("What was the expense for?"); return; }
    if (!(amount > 0)) { setError("Enter an amount greater than zero."); return; }
    setBusy(true);
    setError(null);
    try {
      await api.submitExpense({
        title: form.title.trim(), category: form.category, amount,
        spentOn: form.spentOn, description: form.description.trim() || undefined,
        receiptUrl: form.receiptUrl.trim() || undefined,
      });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to submit the claim");
      setBusy(false);
    }
  }

  return (
    <Modal open onClose={onClose} title="New expense claim">
      <form onSubmit={submit} className="flex flex-col gap-3" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="What was it for?" htmlFor="x-title">
          <Input id="x-title" autoFocus value={form.title}
            onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
            placeholder="e.g. Client visit — flights" />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Category" htmlFor="x-cat">
            <select id="x-cat" className={selectCls} value={form.category}
              onChange={(e) => setForm((f) => ({ ...f, category: e.target.value as ExpenseCategory }))}>
              {CATEGORIES.map((c) => <option key={c} value={c} className="bg-surface">{CATEGORY_LABEL[c]}</option>)}
            </select>
          </Field>
          <Field label="Amount" htmlFor="x-amt">
            <Input id="x-amt" type="number" min="0" step="0.01" value={form.amount}
              onChange={(e) => setForm((f) => ({ ...f, amount: e.target.value }))} placeholder="0.00" />
          </Field>
        </div>
        <Field label="Spent on" htmlFor="x-date">
          <Input id="x-date" type="date" max={new Date().toISOString().slice(0, 10)} value={form.spentOn}
            onChange={(e) => setForm((f) => ({ ...f, spentOn: e.target.value }))} />
        </Field>
        <Field label="Receipt link (optional)" htmlFor="x-receipt">
          <Input id="x-receipt" value={form.receiptUrl}
            onChange={(e) => setForm((f) => ({ ...f, receiptUrl: e.target.value }))}
            placeholder="https://… (file upload is coming)" />
        </Field>
        <Field label="Notes (optional)" htmlFor="x-desc">
          <Input id="x-desc" value={form.description}
            onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} />
        </Field>
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />} Submit claim
          </Button>
        </div>
      </form>
    </Modal>
  );
}
