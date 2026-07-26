"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Loader2, ArrowLeft, Plus, Star, Trash2, FileText, Mail } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { JobOpening, Candidate, CandidateStage } from "@/lib/types";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Alert } from "@/components/ui/alert";
import { cn } from "@/lib/utils";

const STAGES: CandidateStage[] = ["APPLIED", "SCREENING", "INTERVIEW", "OFFER", "HIRED", "REJECTED"];
const STAGE_LABEL: Record<CandidateStage, string> = {
  APPLIED: "Applied", SCREENING: "Screening", INTERVIEW: "Interview", OFFER: "Offer", HIRED: "Hired", REJECTED: "Rejected",
};
const STAGE_ACCENT: Record<CandidateStage, string> = {
  APPLIED: "text-fg/60", SCREENING: "text-aqua", INTERVIEW: "text-violet", OFFER: "text-amber-400",
  HIRED: "text-emerald-400", REJECTED: "text-rose-400",
};

/** The hiring pipeline board for one opening — candidates in columns by stage. */
export default function PipelinePage() {
  const { jobId } = useParams<{ jobId: string }>();
  const [job, setJob] = useState<JobOpening | null>(null);
  const [candidates, setCandidates] = useState<Candidate[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    api.job(jobId).then(setJob).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load"));
    api.candidates(jobId).then(setCandidates).catch(() => setCandidates([]));
  }, [jobId]);

  function replace(c: Candidate) { setCandidates((cur) => cur?.map((x) => (x.id === c.id ? c : x)) ?? cur); }

  async function move(c: Candidate, stage: CandidateStage) {
    const prev = c.stage;
    replace({ ...c, stage }); // optimistic
    try { replace(await api.moveCandidate(c.id, stage)); }
    catch { replace({ ...c, stage: prev }); }
  }
  async function remove(c: Candidate) {
    setCandidates((cur) => cur?.filter((x) => x.id !== c.id) ?? cur);
    try { await api.deleteCandidate(c.id); } catch { /* best-effort in demo */ }
  }

  return (
    <div>
      <Link href="/recruitment" className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg"><ArrowLeft className="h-4 w-4" /> Recruitment</Link>

      <div className="mt-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{job?.title ?? "…"}</h1>
          <p className="mt-1 text-fg/50">
            {job ? [job.department, job.location, `${job.positions} position${job.positions > 1 ? "s" : ""}`].filter(Boolean).join(" · ") : ""}
          </p>
        </div>
        <Button onClick={() => setAdding((v) => !v)}><Plus className="h-4 w-4" /> Add candidate</Button>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {adding && <AddCandidate jobId={jobId} onAdded={(c) => { setCandidates((cur) => [...(cur ?? []), c]); setAdding(false); }} onCancel={() => setAdding(false)} />}

      {candidates === null ? (
        <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <div className="mt-6 overflow-x-auto pb-2">
          <div className="flex gap-3" style={{ minWidth: "min-content" }}>
            {STAGES.map((stage) => {
              const list = candidates.filter((c) => c.stage === stage);
              return (
                <div key={stage} className="w-64 shrink-0">
                  <div className="mb-2 flex items-center justify-between px-1">
                    <span className={cn("text-sm font-medium", STAGE_ACCENT[stage])}>{STAGE_LABEL[stage]}</span>
                    <span className="text-xs text-fg/40">{list.length}</span>
                  </div>
                  <div className="flex min-h-[4rem] flex-col gap-2 rounded-xl bg-fg/[0.03] p-2">
                    {list.map((c) => (
                      <CandidateCard key={c.id} c={c} onMove={(s) => move(c, s)} onRemove={() => remove(c)} />
                    ))}
                    {list.length === 0 && <p className="px-1 py-3 text-center text-xs text-fg/30">—</p>}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

function CandidateCard({ c, onMove, onRemove }: { c: Candidate; onMove: (s: CandidateStage) => void; onRemove: () => void }) {
  return (
    <div className="rounded-lg border border-fg/10 bg-surface p-2.5 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <p className="min-w-0 flex-1 truncate text-sm font-medium">{c.name}</p>
        <button onClick={onRemove} className="text-fg/25 hover:text-rose-400" aria-label="Remove"><Trash2 className="h-3.5 w-3.5" /></button>
      </div>
      {c.email && <p className="mt-0.5 flex items-center gap-1 truncate text-xs text-fg/40"><Mail className="h-3 w-3" />{c.email}</p>}
      <div className="mt-1.5 flex items-center gap-2">
        {c.rating != null && (
          <span className="inline-flex items-center gap-0.5 text-xs text-amber-400">
            {c.rating}<Star className="h-3 w-3 fill-amber-400" />
          </span>
        )}
        {c.source && <span className="rounded-full bg-fg/10 px-1.5 py-0.5 text-[10px] text-fg/50">{c.source}</span>}
        {c.resumeUrl && <a href={c.resumeUrl} target="_blank" rel="noreferrer" className="text-fg/40 hover:text-violet" aria-label="Resume"><FileText className="h-3.5 w-3.5" /></a>}
      </div>
      <select value={c.stage} onChange={(e) => onMove(e.target.value as CandidateStage)}
        className="mt-2 w-full rounded-md border border-fg/10 bg-fg/5 px-1.5 py-1 text-xs text-fg/70">
        {STAGES.map((s) => <option key={s} value={s} className="bg-surface">Move to {STAGE_LABEL[s]}</option>)}
      </select>
    </div>
  );
}

function AddCandidate({ jobId, onAdded, onCancel }: { jobId: string; onAdded: (c: Candidate) => void; onCancel: () => void }) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [source, setSource] = useState("");
  const [resumeUrl, setResumeUrl] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    setBusy(true); setError(null);
    try { onAdded(await api.addCandidate(jobId, { name: name.trim(), email: email || undefined, source: source || undefined, resumeUrl: resumeUrl || undefined })); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Couldn't add candidate"); setBusy(false); }
  }

  return (
    <Card className="mt-6">
      {error && <Alert tone="error" className="mb-3">{error}</Alert>}
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Candidate name" autoFocus />
        <Input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Email" type="email" />
        <Input value={source} onChange={(e) => setSource(e.target.value)} placeholder="Source (e.g. LinkedIn, Referral)" />
        <Input value={resumeUrl} onChange={(e) => setResumeUrl(e.target.value)} placeholder="Resume link (optional)" />
        <div className="flex gap-2 sm:col-span-2">
          <Button type="submit" disabled={busy || !name.trim()}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Add</Button>
          <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
        </div>
      </form>
    </Card>
  );
}
