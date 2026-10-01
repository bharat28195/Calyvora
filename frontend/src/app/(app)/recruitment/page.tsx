"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, Plus, MapPin, Building2, Users, ArrowRight } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { JobOpening, Department } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";
import { cn } from "@/lib/utils";

const STATUS_TONE: Record<JobOpening["status"], string> = {
  OPEN: "bg-emerald-500/15 text-emerald-400",
  ON_HOLD: "bg-amber-500/15 text-amber-300",
  CLOSED: "bg-fg/10 text-fg/50",
};

/** Recruitment — open roles and the hiring pipeline (ATS). Owner/Admin. */
export default function RecruitmentPage() {
  const [jobs, setJobs] = useState<JobOpening[] | null>(null);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    api.jobs().then(setJobs).catch((e) => { setJobs([]); setError(e instanceof ApiError ? e.message : "Failed to load"); });
    api.listDepartments().then(setDepartments).catch(() => {});
  }, []);

  const openCount = jobs?.filter((j) => j.status === "OPEN").length ?? 0;
  const pipeline = jobs?.reduce((s, j) => s + j.candidateCount, 0) ?? 0;
  const hired = jobs?.reduce((s, j) => s + j.hiredCount, 0) ?? 0;

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Recruitment</h1>
          <p className="mt-1 text-fg/50">Open roles and your hiring pipeline.</p>
        </div>
        <Button onClick={() => setAdding((v) => !v)}><Plus className="h-4 w-4" /> New opening</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {adding && <NewJobForm departments={departments} onCreated={(j) => { setJobs((cur) => [j, ...(cur ?? [])]); setAdding(false); }} onCancel={() => setAdding(false)} />}

      {jobs === null ? (
        <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <>
          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Kpi label="Open roles" value={openCount} />
            <Kpi label="Total openings" value={jobs.length} />
            <Kpi label="In pipeline" value={pipeline} />
            {/* Hires is the number the row was missing. The other three count work in progress;
                this one counts work finished, which is the figure anyone actually reports on. */}
            <Kpi label="Hired" value={hired} tone="text-emerald-400" />
          </div>

          {jobs.length === 0 ? (
            <Card className="mt-6"><p className="text-sm text-fg/50">No openings yet. Post your first role to start building a pipeline.</p></Card>
          ) : (
            <div className="mt-6 grid gap-4 lg:grid-cols-2">
              {jobs.map((j) => (
                <Link key={j.id} href={`/recruitment/${j.id}`}>
                  <Card className="transition-colors hover:border-fg/20">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="truncate font-medium">{j.title}</p>
                        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-fg/50">
                          {j.department && <span className="inline-flex items-center gap-1"><Building2 className="h-3.5 w-3.5" />{j.department}</span>}
                          {j.location && <span className="inline-flex items-center gap-1"><MapPin className="h-3.5 w-3.5" />{j.location}</span>}
                          <span>{j.positions} position{j.positions > 1 ? "s" : ""}</span>
                        </div>
                      </div>
                      <span className={cn("shrink-0 rounded-full px-2 py-0.5 text-xs font-medium", STATUS_TONE[j.status])}>
                        {j.status.toLowerCase().replace("_", " ")}
                      </span>
                    </div>
                    {/* How full the role is, which is the question the card exists to answer. A bar
                        rather than "2/3" alone: a manager scanning ten openings finds the empty ones
                        by shape, without reading a single fraction. */}
                    <div className="mt-3">
                      <div className="flex items-center justify-between text-xs">
                        <span className="text-fg/50">
                          {j.hiredCount} of {j.positions} filled
                        </span>
                        <span className="tabular-nums text-fg/40">
                          {Math.min(100, Math.round((j.hiredCount / Math.max(1, j.positions)) * 100))}%
                        </span>
                      </div>
                      <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-fg/10">
                        <div
                          className="h-full rounded-full bg-emerald-400/80"
                          style={{ width: `${Math.min(100, (j.hiredCount / Math.max(1, j.positions)) * 100)}%` }}
                        />
                      </div>
                    </div>
                    <div className="mt-3 flex items-center justify-between border-t border-fg/10 pt-3 text-sm">
                      <span className="inline-flex items-center gap-1.5 text-fg/60">
                        <Users className="h-4 w-4 text-violet" /> {j.candidateCount} candidate{j.candidateCount === 1 ? "" : "s"}
                      </span>
                      <span className="inline-flex items-center gap-1 text-violet">Pipeline <ArrowRight className="h-4 w-4" /></span>
                    </div>
                  </Card>
                </Link>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

function NewJobForm({ departments, onCreated, onCancel }: {
  departments: Department[]; onCreated: (j: JobOpening) => void; onCancel: () => void;
}) {
  const [title, setTitle] = useState("");
  const [departmentId, setDepartmentId] = useState("");
  const [location, setLocation] = useState("");
  const [employmentType, setEmploymentType] = useState("FULL_TIME");
  const [positions, setPositions] = useState("1");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const selectCls = "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim()) return;
    setBusy(true); setError(null);
    try {
      onCreated(await api.createJob({
        title: title.trim(), departmentId: departmentId || undefined, location: location || undefined,
        employmentType, positions: Number(positions) || 1, description: description || undefined,
      }));
    } catch (err) { setError(err instanceof ApiError ? err.message : "Couldn't create the opening"); setBusy(false); }
  }

  return (
    <Card className="mt-6">
      <CardTitle>New job opening</CardTitle>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      <form onSubmit={submit} className="mt-3 grid gap-3 sm:grid-cols-2">
        <div className="sm:col-span-2"><Field label="Title" htmlFor="j-title"><Input id="j-title" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Senior Backend Engineer" autoFocus /></Field></div>
        <Field label="Department" htmlFor="j-dept">
          <select id="j-dept" className={selectCls} value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
            <option value="" className="bg-surface">—</option>
            {departments.map((d) => <option key={d.id} value={d.id} className="bg-surface">{d.name}</option>)}
          </select>
        </Field>
        <Field label="Location" htmlFor="j-loc"><Input id="j-loc" value={location} onChange={(e) => setLocation(e.target.value)} placeholder="e.g. Remote / Bangalore" /></Field>
        <Field label="Employment type" htmlFor="j-type">
          <select id="j-type" className={selectCls} value={employmentType} onChange={(e) => setEmploymentType(e.target.value)}>
            {["FULL_TIME", "PART_TIME", "CONTRACT", "INTERN"].map((t) => <option key={t} value={t} className="bg-surface">{t.replace("_", " ").toLowerCase()}</option>)}
          </select>
        </Field>
        <Field label="Positions" htmlFor="j-pos"><Input id="j-pos" type="number" min={1} value={positions} onChange={(e) => setPositions(e.target.value)} /></Field>
        <div className="sm:col-span-2">
          <label className="block text-xs text-fg/50">Description</label>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3}
            className="mt-1 w-full rounded-lg border border-fg/15 bg-fg/5 p-3 text-sm text-fg placeholder:text-fg/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
            placeholder="What the role is about…" />
        </div>
        <div className="flex gap-2 sm:col-span-2">
          <Button type="submit" disabled={busy || !title.trim()}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Post opening</Button>
          <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
        </div>
      </form>
    </Card>
  );
}

function Kpi({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <Card className="py-4">
      <p className="text-xs text-fg/50">{label}</p>
      <p className={cn("mt-1 text-2xl font-semibold tabular-nums", tone)}>{value}</p>
    </Card>
  );
}
