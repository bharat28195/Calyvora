"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, ShieldCheck } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { PfSettings, StatutorySettings } from "@/lib/types";
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
          Provident Fund, ESI and professional tax for this company, and the registration numbers
          your statutory filings carry.
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

          <EsiPtSettings />
        </>
      )}
    </div>
  );
}

/**
 * ESI, professional tax and registration numbers — a second settings row with its own save, so a
 * change here never resends the PF rates above (and the reverse).
 */
function EsiPtSettings() {
  const [s, setS] = useState<StatutorySettings | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  const [esiEnabled, setEsiEnabled] = useState(false);
  const [esiEmployee, setEsiEmployee] = useState("");
  const [esiEmployer, setEsiEmployer] = useState("");
  const [esiCeiling, setEsiCeiling] = useState("");
  const [ptEnabled, setPtEnabled] = useState(false);
  const [pfCode, setPfCode] = useState("");
  const [esiCode, setEsiCode] = useState("");
  const [tan, setTan] = useState("");
  const [pan, setPan] = useState("");
  const [ptReg, setPtReg] = useState("");

  const apply = useCallback((v: StatutorySettings) => {
    setS(v);
    setEsiEnabled(v.esiEnabled);
    setEsiEmployee(String(v.esiEmployeeRate));
    setEsiEmployer(String(v.esiEmployerRate));
    setEsiCeiling(String(v.esiWageCeiling));
    setPtEnabled(v.ptEnabled);
    setPfCode(v.pfEstablishmentCode ?? "");
    setEsiCode(v.esiEmployerCode ?? "");
    setTan(v.tan ?? "");
    setPan(v.companyPan ?? "");
    setPtReg(v.ptRegistrationNo ?? "");
  }, []);

  useEffect(() => {
    api.statutorySettings().then(apply).catch((e) =>
      setError(e instanceof ApiError ? e.message : "Failed to load ESI and professional-tax settings"));
  }, [apply]);

  async function save() {
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      apply(await api.updateStatutorySettings({
        esiEnabled,
        esiEmployeeRate: Number(esiEmployee),
        esiEmployerRate: Number(esiEmployer),
        esiWageCeiling: Number(esiCeiling),
        ptEnabled,
        pfEstablishmentCode: pfCode,
        esiEmployerCode: esiCode,
        tan,
        companyPan: pan,
        ptRegistrationNo: ptReg,
      }));
      setSaved(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Failed to save");
    } finally {
      setBusy(false);
    }
  }

  if (s === null) {
    return error ? <Alert tone="error" className="mt-6">{error}</Alert> : null;
  }

  return (
    <>
      <Card className="mt-6">
        <CardTitle>Employees&apos; State Insurance (ESI)</CardTitle>
        <label className="mt-4 flex items-start gap-3 text-sm">
          <input type="checkbox" className="mt-1" checked={esiEnabled} onChange={(e) => setEsiEnabled(e.target.checked)} />
          <span>
            <span className="font-medium">Deduct ESI</span>
            <span className="mt-0.5 block text-xs text-fg/50">
              For employees marked ESI-eligible whose gross pay was at or under the ceiling when the
              contribution period began (April or October). They stay covered until the period ends,
              even after a raise.
            </span>
          </span>
        </label>
        <div className="mt-5 grid gap-5 sm:grid-cols-3">
          <Field label="Employee (%)" htmlFor="esiEmployee">
            <Input id="esiEmployee" type="number" step="0.01" min={0} max={100} value={esiEmployee}
              onChange={(e) => setEsiEmployee(e.target.value)} />
          </Field>
          <Field label="Employer (%)" htmlFor="esiEmployer">
            <Input id="esiEmployer" type="number" step="0.01" min={0} max={100} value={esiEmployer}
              onChange={(e) => setEsiEmployer(e.target.value)} />
          </Field>
          <Field label="Wage ceiling (monthly)" htmlFor="esiCeiling">
            <Input id="esiCeiling" type="number" min={1} value={esiCeiling}
              onChange={(e) => setEsiCeiling(e.target.value)} />
          </Field>
        </div>
      </Card>

      <Card className="mt-6">
        <CardTitle>Professional tax</CardTitle>
        <label className="mt-4 flex items-start gap-3 text-sm">
          <input type="checkbox" className="mt-1" checked={ptEnabled} onChange={(e) => setPtEnabled(e.target.checked)} />
          <span>
            <span className="font-medium">Deduct professional tax</span>
            <span className="mt-0.5 block text-xs text-fg/50">
              By the state on each employee&apos;s finance record, using that state&apos;s slabs —
              monthly in most states, twice a year in Tamil Nadu and Kerala, and in instalments in
              Madhya Pradesh, Jharkhand and Bihar. States without professional tax deduct nothing.
            </span>
          </span>
        </label>
      </Card>

      <Card className="mt-6">
        <CardTitle>Registration numbers</CardTitle>
        <p className="mt-1 text-xs text-fg/50">Printed on the files you upload to the EPFO, ESIC and income-tax portals.</p>
        <div className="mt-5 grid gap-5 sm:grid-cols-2">
          <Field label="PF establishment code" htmlFor="pfCode">
            <Input id="pfCode" value={pfCode} placeholder="MHBAN0012345000" onChange={(e) => setPfCode(e.target.value)} />
          </Field>
          <Field label="ESI employer code" htmlFor="esiCode">
            <Input id="esiCode" value={esiCode} placeholder="17 digits" onChange={(e) => setEsiCode(e.target.value)} />
          </Field>
          <Field label="TAN" htmlFor="tan">
            <Input id="tan" value={tan} placeholder="DELA12345B" onChange={(e) => setTan(e.target.value)} />
          </Field>
          <Field label="Company PAN" htmlFor="companyPan">
            <Input id="companyPan" value={pan} placeholder="ABCDE1234F" onChange={(e) => setPan(e.target.value)} />
          </Field>
          <Field label="Professional tax registration" htmlFor="ptReg">
            <Input id="ptReg" value={ptReg} onChange={(e) => setPtReg(e.target.value)} />
          </Field>
        </div>
      </Card>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {saved && <Alert tone="success" className="mt-6">Saved. New payslips use these settings.</Alert>}
      <Button onClick={save} disabled={busy} className="mt-6">
        {busy && <Loader2 className="h-4 w-4 animate-spin" />}
        Save ESI, professional tax and registrations
      </Button>
    </>
  );
}
