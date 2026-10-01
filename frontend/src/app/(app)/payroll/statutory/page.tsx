"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, ShieldCheck } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { PfSettings } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/**
 * Provident Fund settings.
 *
 * <p>The screen is readable whether or not statutory payroll is switched on for this company, and it
 * says which. Hiding it when off would leave an HR manager who has been told "we're turning PF on
 * next month" with nowhere to check the rates first; showing it without the banner would imply
 * deductions are already happening.
 */
export default function StatutoryPayrollPage() {
  const [settings, setSettings] = useState<PfSettings | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  const [ceiling, setCeiling] = useState("");
  const [restrict, setRestrict] = useState(true);
  const [employeeRate, setEmployeeRate] = useState("");
  const [employerRate, setEmployerRate] = useState("");
  const [epsRate, setEpsRate] = useState("");
  const [adminRate, setAdminRate] = useState("");
  const [edliRate, setEdliRate] = useState("");

  const apply = useCallback((s: PfSettings) => {
    setSettings(s);
    setCeiling(String(s.wageCeiling));
    setRestrict(s.restrictToCeiling);
    setEmployeeRate(String(s.employeeRate));
    setEmployerRate(String(s.employerRate));
    setEpsRate(String(s.epsRate));
    setAdminRate(String(s.adminChargeRate));
    setEdliRate(String(s.edliRate));
  }, []);

  const load = useCallback(async () => {
    try {
      apply(await api.pfSettings());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to load PF settings");
    }
  }, [apply]);

  useEffect(() => {
    void load();
  }, [load]);

  async function save() {
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      apply(
        await api.updatePfSettings({
          wageCeiling: Number(ceiling),
          restrictToCeiling: restrict,
          employeeRate: Number(employeeRate),
          employerRate: Number(employerRate),
          epsRate: Number(epsRate),
          adminChargeRate: Number(adminRate),
          edliRate: Number(edliRate),
        }),
      );
      setSaved(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to save PF settings");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="max-w-3xl">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Statutory payroll</h1>
        <p className="mt-1 text-fg/50">
          Provident Fund rates for this company. These decide what comes out of every enrolled
          employee&apos;s salary.
        </p>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {saved && <Alert tone="success" className="mt-6">Saved. New payslips use these rates.</Alert>}

      {settings === null ? (
        <Card className="mt-8"><Loader2 className="mx-auto h-5 w-5 animate-spin text-violet" /></Card>
      ) : (
        <>
          {settings.enabled ? (
            <Alert tone="success" className="mt-6">
              <ShieldCheck className="mr-1 inline h-4 w-4" />
              Statutory payroll is <strong>on</strong>. Enrolled employees have PF deducted, and
              payslips show the employer contribution.
            </Alert>
          ) : (
            <Alert tone="info" className="mt-6">
              Statutory payroll is <strong>off</strong> for your company, so nothing here affects
              payslips yet. You can set the rates now — ask us to switch it on once you have checked
              them against a real payroll run.
            </Alert>
          )}

          <Card className="mt-6">
            <CardTitle>Provident Fund</CardTitle>
            <div className="mt-5 grid gap-5 sm:grid-cols-2">
              <Field label="Wage ceiling" htmlFor="ceiling">
                <Input id="ceiling" type="number" min={1} value={ceiling}
                  onChange={(e) => setCeiling(e.target.value)} />
              </Field>
              <Field label="Employee contribution (%)" htmlFor="employeeRate">
                <Input id="employeeRate" type="number" step="0.01" min={0} max={100} value={employeeRate}
                  onChange={(e) => setEmployeeRate(e.target.value)} />
              </Field>
              <Field label="Employer contribution (%)" htmlFor="employerRate">
                <Input id="employerRate" type="number" step="0.01" min={0} max={100} value={employerRate}
                  onChange={(e) => setEmployerRate(e.target.value)} />
              </Field>
              <Field label="Of which pension, EPS (%)" htmlFor="epsRate">
                <Input id="epsRate" type="number" step="0.01" min={0} max={100} value={epsRate}
                  onChange={(e) => setEpsRate(e.target.value)} />
              </Field>
              <Field label="Admin charges (%)" htmlFor="adminRate">
                <Input id="adminRate" type="number" step="0.01" min={0} max={100} value={adminRate}
                  onChange={(e) => setAdminRate(e.target.value)} />
              </Field>
              <Field label="EDLI (%)" htmlFor="edliRate">
                <Input id="edliRate" type="number" step="0.01" min={0} max={100} value={edliRate}
                  onChange={(e) => setEdliRate(e.target.value)} />
              </Field>
            </div>

            <label className="mt-5 flex items-start gap-3 text-sm">
              <input type="checkbox" className="mt-1" checked={restrict}
                onChange={(e) => setRestrict(e.target.checked)} />
              <span>
                <span className="font-medium">Cap contributions at the wage ceiling</span>
                <span className="mt-0.5 block text-xs text-fg/50">
                  The common choice. Uncheck to contribute on the full basic pay however high it runs —
                  both are lawful, and the difference is thousands of rupees a month per employee. The
                  pension (EPS) share stays capped either way.
                </span>
              </span>
            </label>

            <Button onClick={save} disabled={busy} className="mt-6">
              {busy && <Loader2 className="h-4 w-4 animate-spin" />}
              Save rates
            </Button>
          </Card>

          <div className="mt-6 rounded-xl border border-fg/10 p-4 text-xs text-fg/50">
            <p className="font-medium text-fg/70">Two things worth knowing</p>
            <p className="mt-2">
              PF is computed on <strong>basic pay</strong> (plus dearness allowance), not on gross. The
              basic is whatever your payslip template marks as the basis component.
            </p>
            <p className="mt-2">
              An employee is only included if their own PF status is set to enabled on their finance
              record. Switching PF on for the company does not enrol anybody by itself.
            </p>
          </div>
        </>
      )}
    </div>
  );
}
