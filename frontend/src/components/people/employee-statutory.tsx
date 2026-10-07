"use client";

import { useEffect, useState } from "react";
import { Landmark, Loader2, Pencil } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { EmployeeFinance } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";

/**
 * States as the professional-tax engine knows them. Free text is still accepted by the server (and
 * old records hold whatever was typed), but a list stops "Karnatka" silently deducting nothing.
 */
const STATES = [
  "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chandigarh", "Chhattisgarh", "Delhi", "Goa",
  "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka", "Kerala",
  "Ladakh", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha",
  "Puducherry", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh",
  "Uttarakhand", "West Bengal",
];

const select = "h-10 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg";

/**
 * PF / ESI / professional-tax enrolment for one employee — HR only. These are employer filings, so
 * the employee cannot change them from My finances; until now there was no screen where HR could
 * either, and the only way to enrol somebody in PF was the API.
 */
export function EmployeeStatutory({ employeeId }: { employeeId: string }) {
  const [f, setF] = useState<EmployeeFinance | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);

  const [pfStatus, setPfStatus] = useState("NOT_ELIGIBLE");
  const [uan, setUan] = useState("");
  const [pfNumber, setPfNumber] = useState("");
  const [pfJoinDate, setPfJoinDate] = useState("");
  const [esiStatus, setEsiStatus] = useState("NOT_ELIGIBLE");
  const [esiNumber, setEsiNumber] = useState("");
  const [ptState, setPtState] = useState("");
  const [gender, setGender] = useState("");
  const [dob, setDob] = useState("");

  function apply(v: EmployeeFinance) {
    setF(v);
    setPfStatus(v.pfStatus);
    setUan(v.uan ?? "");
    setPfNumber(v.pfNumber ?? "");
    setPfJoinDate(v.pfJoinDate ?? "");
    setEsiStatus(v.esiStatus);
    setEsiNumber(v.esiNumber ?? "");
    setPtState(v.ptState ?? "");
    setGender(v.gender ?? "");
    setDob(v.dateOfBirth ?? "");
  }

  useEffect(() => {
    api.employeeFinance(employeeId).then(apply)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load statutory details"));
  }, [employeeId]);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const patch: Record<string, unknown> = {
        pfStatus, uan, pfNumber, esiStatus, esiNumber, ptState, gender,
      };
      if (pfJoinDate) patch.pfJoinDate = pfJoinDate;
      if (dob) patch.dateOfBirth = dob;
      apply(await api.updateEmployeeFinance(employeeId, patch));
      setEditing(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mt-6">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="flex items-center gap-1.5 text-sm font-medium text-fg/80">
          <Landmark className="h-4 w-4 text-violet" /> Statutory (PF, ESI, professional tax)
        </h3>
        {f && !editing && (
          <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
            <Pencil className="h-3.5 w-3.5" /> Edit
          </Button>
        )}
      </div>
      {error && <Alert tone="error" className="mb-3">{error}</Alert>}

      {f === null ? (
        !error && <Loader2 className="h-5 w-5 animate-spin text-violet" />
      ) : !editing ? (
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <Item label="PF" value={f.pfStatus === "ENABLED" ? `Enrolled${f.uan ? ` · UAN ${f.uan}` : ""}` : "Not enrolled"} />
          <Item label="ESI" value={f.esiStatus === "ELIGIBLE" ? `Eligible${f.esiNumber ? ` · ${f.esiNumber}` : ""}` : "Not eligible"} />
          <Item label="Professional tax state" value={f.ptState ?? "Not set"} />
          <Item label="Gender" value={f.gender ? f.gender.charAt(0) + f.gender.slice(1).toLowerCase() : "Not set"} />
        </dl>
      ) : (
        <form onSubmit={save} className="grid gap-4 sm:grid-cols-2">
          <Field label="PF" htmlFor="pfStatus">
            <select id="pfStatus" className={select} value={pfStatus} onChange={(e) => setPfStatus(e.target.value)}>
              <option value="ENABLED">Enrolled</option>
              <option value="NOT_ELIGIBLE">Not enrolled</option>
            </select>
          </Field>
          <Field label="UAN" htmlFor="uan">
            <Input id="uan" value={uan} placeholder="12 digits" onChange={(e) => setUan(e.target.value)} />
          </Field>
          <Field label="PF member number" htmlFor="pfNumber">
            <Input id="pfNumber" value={pfNumber} onChange={(e) => setPfNumber(e.target.value)} />
          </Field>
          <Field label="PF join date" htmlFor="pfJoinDate">
            <Input id="pfJoinDate" type="date" value={pfJoinDate} onChange={(e) => setPfJoinDate(e.target.value)} />
          </Field>
          <Field label="ESI" htmlFor="esiStatus">
            <select id="esiStatus" className={select} value={esiStatus} onChange={(e) => setEsiStatus(e.target.value)}>
              <option value="ELIGIBLE">Eligible</option>
              <option value="NOT_ELIGIBLE">Not eligible</option>
            </select>
          </Field>
          <Field label="ESI IP number" htmlFor="esiNumber">
            <Input id="esiNumber" value={esiNumber} onChange={(e) => setEsiNumber(e.target.value)} />
          </Field>
          <Field label="Works in (professional tax state)" htmlFor="ptState">
            <select id="ptState" className={select} value={ptState} onChange={(e) => setPtState(e.target.value)}>
              <option value="">Not set</option>
              {ptState && !STATES.includes(ptState) && <option value={ptState}>{ptState}</option>}
              {STATES.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </Field>
          <Field label="Gender" htmlFor="gender">
            <select id="gender" className={select} value={gender} onChange={(e) => setGender(e.target.value)}>
              <option value="">Not set</option>
              <option value="FEMALE">Female</option>
              <option value="MALE">Male</option>
              <option value="OTHER">Other</option>
            </select>
          </Field>
          <Field label="Date of birth" htmlFor="dob">
            <Input id="dob" type="date" value={dob} onChange={(e) => setDob(e.target.value)} />
          </Field>
          <div className="flex items-end gap-2 sm:col-span-2">
            <Button type="submit" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Save</Button>
            <Button type="button" variant="secondary" onClick={() => { if (f) apply(f); setEditing(false); }}>Cancel</Button>
          </div>
        </form>
      )}
    </div>
  );
}

function Item({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-fg/40">{label}</dt>
      <dd className="text-fg/80">{value}</dd>
    </div>
  );
}
