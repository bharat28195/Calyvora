"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { BadgeCheck, Loader2, Pencil } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { EmployeeFinance, Payslip } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { money } from "@/lib/format";

/**
 * My Finances — everything about an employee's pay that isn't the payslip itself: how they're paid,
 * which statutory schemes they're enrolled in, and the identity used on those filings.
 *
 * <p>An employee maintains their own bank details and identity here. PF/ESI/professional-tax
 * enrolment is shown read-only, because those are employer filings that HR owns — the server rejects
 * a self-edit of them, and a form that let you try would just be a lie.
 */
export default function MyFinancesPage() {
  const [finance, setFinance] = useState<EmployeeFinance | null>(null);
  const [slip, setSlip] = useState<Payslip | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);

  useEffect(() => {
    api.myFinance()
      .then(setFinance)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load your finance details"))
      .finally(() => setLoading(false));
    api.myPayslip().then(setSlip).catch(() => setSlip(null));
  }, []);

  if (loading) {
    return <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;
  }

  return (
    <div>
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">My finances</h1>
          <p className="mt-1 text-fg/50">How you&apos;re paid, and the details behind your payslip.</p>
        </div>
        {finance && !editing && (
          <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
            <Pencil className="h-4 w-4" /> Edit my details
          </Button>
        )}
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {/* Payroll summary — the same at-a-glance strip the payslip screen opens with. */}
      {slip && (
        <Card className="mt-6">
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Last processed cycle" value={slip.month} />
            <Stat label="Working days" value={String(slip.workingDays)} />
            <Stat label="Loss of pay" value={String(slip.lopDays)} />
            <div>
              <p className="text-xs uppercase tracking-wide text-fg/40">Payslip</p>
              <Link href="/me/payslip" className="mt-1 block text-sm text-violet hover:underline">
                View payslip
              </Link>
            </div>
          </div>
        </Card>
      )}

      {finance && (editing ? (
        <EditForm
          finance={finance}
          onCancel={() => setEditing(false)}
          onSaved={(f) => { setFinance(f); setEditing(false); }}
        />
      ) : (
        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <div className="flex flex-col gap-4">
            <Card>
              <CardTitle>Payment information</CardTitle>
              <div className="mt-4 flex flex-col gap-5">
                <Detail label="Salary payment mode" value={humanise(finance.paymentMode)} />
                <div>
                  <p className="text-sm font-semibold">Bank information</p>
                  <div className="mt-3 grid gap-5 sm:grid-cols-2">
                    <Detail label="Bank name" value={finance.bankName} />
                    <Detail label="Account number" value={finance.bankAccountMasked} />
                    <Detail label="IFSC code" value={finance.bankIfsc} />
                    <Detail label="Name on the account" value={finance.bankAccountName} />
                    <Detail label="Branch" value={finance.bankBranch} />
                  </div>
                </div>
              </div>
            </Card>

            <Card>
              <CardTitle>Statutory information</CardTitle>
              <p className="mt-1 text-xs text-fg/40">Maintained by HR.</p>

              <p className="mt-5 text-sm font-semibold">PF account information</p>
              <div className="mt-3 grid gap-5 sm:grid-cols-2">
                <Detail label="PF status" value={finance.pfStatus === "ENABLED" ? "Enabled" : "Not eligible"} />
                <Detail label="PF number" value={finance.pfNumber} />
                <Detail label="Universal account number" value={finance.uan} />
                <Detail label="PF join date" value={finance.pfJoinDate} />
                <Detail label="Name of the account" value={finance.pfAccountName} />
              </div>

              <p className="mt-6 text-sm font-semibold">ESI account information</p>
              <div className="mt-3 grid gap-5 sm:grid-cols-2">
                <Detail label="ESI status" value={finance.esiStatus === "ELIGIBLE" ? "Eligible" : "Not eligible"} />
                <Detail label="ESI number" value={finance.esiNumber} />
              </div>

              <p className="mt-6 text-sm font-semibold">Professional tax</p>
              <div className="mt-3 grid gap-5 sm:grid-cols-2">
                <Detail label="State" value={finance.ptState} />
                <Detail label="Registered location" value={finance.ptLocation} />
              </div>
            </Card>
          </div>

          <Card className="h-fit">
            <CardTitle>Identity information</CardTitle>
            <div className="mt-4 flex items-center gap-2">
              <span className="text-sm font-medium">PAN card</span>
              {finance.panVerified ? (
                <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs font-medium text-emerald-400">
                  <BadgeCheck className="h-3 w-3" /> Verified
                </span>
              ) : (
                <span className="rounded-full bg-fg/10 px-2 py-0.5 text-xs text-fg/50">Not verified</span>
              )}
            </div>
            <div className="mt-4 grid gap-5 sm:grid-cols-2">
              <Detail label="Permanent account number" value={finance.panMasked} />
              <Detail label="Name" value={finance.employeeName.toUpperCase()} />
              <Detail label="Date of birth" value={finance.dateOfBirth} />
              <Detail label="Parent's name" value={finance.parentName} />
            </div>
          </Card>
        </div>
      ))}
    </div>
  );
}

/** The employee-owned half of the record. Statutory fields are absent on purpose — HR owns those. */
function EditForm({ finance, onCancel, onSaved }: {
  finance: EmployeeFinance;
  onCancel: () => void;
  onSaved: (f: EmployeeFinance) => void;
}) {
  const [values, setValues] = useState({
    paymentMode: finance.paymentMode,
    bankName: finance.bankName ?? "",
    bankAccountNo: "",
    bankIfsc: finance.bankIfsc ?? "",
    bankAccountName: finance.bankAccountName ?? "",
    bankBranch: finance.bankBranch ?? "",
    panNumber: "",
    dateOfBirth: finance.dateOfBirth ?? "",
    parentName: finance.parentName ?? "",
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = (k: keyof typeof values) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setValues((v) => ({ ...v, [k]: e.target.value }));

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      // Only send what changed — an untouched account number must not be overwritten with the mask.
      const patch: Record<string, unknown> = { ...values };
      if (!values.bankAccountNo) delete patch.bankAccountNo;
      if (!values.panNumber) delete patch.panNumber;
      onSaved(await api.updateMyFinance(patch));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't save your details");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card className="mt-4">
      <CardTitle>Edit my details</CardTitle>
      <p className="mt-1 text-xs text-fg/40">
        PF, ESI and professional-tax details are maintained by HR and can&apos;t be changed here.
      </p>
      {error && <Alert tone="error" className="mt-4">{error}</Alert>}
      <form onSubmit={save} className="mt-5 flex flex-col gap-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Bank name" htmlFor="bankName">
            <Input id="bankName" value={values.bankName} onChange={set("bankName")} />
          </Field>
          <Field
            label="Account number"
            htmlFor="bankAccountNo"
            hint={finance.bankAccountMasked ? `Currently ${finance.bankAccountMasked} — leave blank to keep it` : undefined}
          >
            <Input id="bankAccountNo" value={values.bankAccountNo} onChange={set("bankAccountNo")} />
          </Field>
          <Field label="IFSC code" htmlFor="bankIfsc" hint="e.g. HDFC0003939">
            <Input id="bankIfsc" value={values.bankIfsc} onChange={set("bankIfsc")} />
          </Field>
          <Field label="Name on the account" htmlFor="bankAccountName">
            <Input id="bankAccountName" value={values.bankAccountName} onChange={set("bankAccountName")} />
          </Field>
          <Field label="Branch" htmlFor="bankBranch">
            <Input id="bankBranch" value={values.bankBranch} onChange={set("bankBranch")} />
          </Field>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            label="PAN"
            htmlFor="panNumber"
            hint={finance.panMasked ? `Currently ${finance.panMasked} — changing it needs re-verification` : "e.g. ABCDE1234F"}
          >
            <Input id="panNumber" value={values.panNumber} onChange={set("panNumber")} />
          </Field>
          <Field label="Date of birth" htmlFor="dateOfBirth">
            <Input id="dateOfBirth" type="date" value={values.dateOfBirth} onChange={set("dateOfBirth")} />
          </Field>
          <Field label="Parent's name" htmlFor="parentName">
            <Input id="parentName" value={values.parentName} onChange={set("parentName")} />
          </Field>
        </div>

        <div className="flex gap-2">
          <Button type="submit" disabled={saving}>
            {saving && <Loader2 className="h-4 w-4 animate-spin" />}
            {saving ? "Saving…" : "Save"}
          </Button>
          <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
        </div>
      </form>
    </Card>
  );
}

function Detail({ label, value }: { label: string; value?: string | null }) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wide text-fg/40">{label}</p>
      <p className="mt-1 text-sm text-fg/90">{value?.trim() ? value : <span className="text-fg/30">—</span>}</p>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wide text-fg/40">{label}</p>
      <p className="mt-1 text-sm font-medium tabular-nums">{value}</p>
    </div>
  );
}

/** BANK_TRANSFER -> "Bank transfer". */
function humanise(s: string): string {
  const lower = s.toLowerCase().replace(/_/g, " ");
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}
