"use client";

import { useEffect, useState } from "react";
import { Loader2, Plus, Trash2, GripVertical, Save } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { PayslipComponent, PayComponentKind, PayComponentCalc } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const CALC_LABEL: Record<PayComponentCalc, string> = {
  PERCENT_OF_GROSS: "% of gross",
  PERCENT_OF_BASIC: "% of basic",
  FIXED: "Fixed amount",
  REMAINDER: "Remainder of gross",
};

/**
 * Payslip template editor (Owner/Admin) — defines the earnings and deductions that make up every
 * payslip. The same validation the server enforces (percentages in range, one remainder, one basis,
 * earnings ≤ 100%) is surfaced here on save.
 */
export default function PayslipTemplatePage() {
  const [rows, setRows] = useState<PayslipComponent[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.payslipTemplate().then(setRows).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load template"));
  }, []);

  function update(i: number, patch: Partial<PayslipComponent>) {
    setRows((r) => r!.map((c, idx) => (idx === i ? { ...c, ...patch } : c)));
    setOk(false);
  }
  function setBasis(i: number) {
    setRows((r) => r!.map((c, idx) => ({ ...c, basis: idx === i ? !c.basis : false })));
    setOk(false);
  }
  function remove(i: number) { setRows((r) => r!.filter((_, idx) => idx !== i)); setOk(false); }
  function add(kind: PayComponentKind) {
    setRows((r) => [...r!, { name: "", kind, calc: "PERCENT_OF_GROSS", value: 0, basis: false }]);
    setOk(false);
  }

  async function save() {
    setSaving(true); setError(null); setOk(false);
    try {
      const saved = await api.savePayslipTemplate(rows!.map((c) => ({
        ...c,
        value: c.calc === "REMAINDER" ? null : Number(c.value ?? 0),
      })));
      setRows(saved); setOk(true);
    } catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't save"); }
    finally { setSaving(false); }
  }

  if (!rows) return <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;

  const earnings = rows.map((c, i) => ({ c, i })).filter((x) => x.c.kind === "EARNING");
  const deductions = rows.map((c, i) => ({ c, i })).filter((x) => x.c.kind === "DEDUCTION");

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Payslip template</h1>
        <p className="mt-1 text-fg/50">Define the earnings and deductions on every payslip. Applies to all employees.</p>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {ok && <Alert tone="success" className="mt-6">Template saved.</Alert>}

      <Section title="Earnings" onAdd={() => add("EARNING")}>
        {earnings.length === 0 ? <Empty>Add at least one earning.</Empty> : earnings.map(({ c, i }) => (
          <Row key={i} c={c} onChange={(p) => update(i, p)} onRemove={() => remove(i)} onBasis={() => setBasis(i)} />
        ))}
      </Section>

      <Section title="Deductions" onAdd={() => add("DEDUCTION")}>
        {deductions.length === 0 ? <Empty>No deductions.</Empty> : deductions.map(({ c, i }) => (
          <Row key={i} c={c} onChange={(p) => update(i, p)} onRemove={() => remove(i)} onBasis={() => setBasis(i)} />
        ))}
      </Section>

      <div className="mt-6 flex items-center gap-3">
        <Button onClick={save} disabled={saving}>{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />} Save template</Button>
        <p className="text-xs text-fg/40">One earning must be the <strong>remainder</strong> so pay adds up to gross; mark one earning as the <strong>basis</strong> for percent-of-basic deductions.</p>
      </div>
    </div>
  );
}

function Section({ title, onAdd, children }: { title: string; onAdd: () => void; children: React.ReactNode }) {
  return (
    <Card className="mt-6">
      <div className="mb-3 flex items-center justify-between">
        <CardTitle>{title}</CardTitle>
        <button onClick={onAdd} className="inline-flex items-center gap-1 text-sm text-violet hover:underline"><Plus className="h-4 w-4" /> Add</button>
      </div>
      <div className="space-y-2">{children}</div>
    </Card>
  );
}

function Row({ c, onChange, onRemove, onBasis }: {
  c: PayslipComponent; onChange: (p: Partial<PayslipComponent>) => void; onRemove: () => void; onBasis: () => void;
}) {
  const calcs: PayComponentCalc[] = c.kind === "EARNING"
    ? ["PERCENT_OF_GROSS", "FIXED", "REMAINDER"]
    : ["PERCENT_OF_GROSS", "PERCENT_OF_BASIC", "FIXED"];
  return (
    <div className="flex flex-wrap items-center gap-2 rounded-lg border border-fg/10 bg-fg/[0.02] p-2">
      <GripVertical className="h-4 w-4 shrink-0 text-fg/20" />
      <Input value={c.name} onChange={(e) => onChange({ name: e.target.value })} placeholder="Name" className="min-w-[9rem] flex-1" />
      <select value={c.calc} onChange={(e) => onChange({ calc: e.target.value as PayComponentCalc })}
        className="rounded-md border border-fg/15 bg-fg/5 px-2 py-2 text-sm text-fg">
        {calcs.map((k) => <option key={k} value={k}>{CALC_LABEL[k]}</option>)}
      </select>
      {c.calc !== "REMAINDER" && (
        <div className="flex items-center gap-1">
          <Input type="number" value={c.value ?? ""} onChange={(e) => onChange({ value: e.target.value === "" ? null : Number(e.target.value) })}
            className="w-24" min={0} />
          <span className="text-xs text-fg/40">{c.calc === "FIXED" ? "amt" : "%"}</span>
        </div>
      )}
      {c.kind === "EARNING" && c.calc !== "REMAINDER" && (
        <button onClick={onBasis} title="Use as basis for percent-of-basic deductions"
          className={"rounded-md px-2 py-1.5 text-xs " + (c.basis ? "bg-violet/15 font-medium text-violet" : "text-fg/50 hover:bg-fg/5")}>
          basis
        </button>
      )}
      <button onClick={onRemove} className="text-fg/30 hover:text-red-400" aria-label="Remove"><Trash2 className="h-4 w-4" /></button>
    </div>
  );
}

function Empty({ children }: { children: React.ReactNode }) {
  return <p className="py-2 text-sm text-fg/40">{children}</p>;
}
