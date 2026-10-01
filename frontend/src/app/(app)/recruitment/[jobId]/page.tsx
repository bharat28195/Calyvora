"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  Loader2, ArrowLeft, Plus, Star, Trash2, FileText, Mail, FileSignature, UserCheck, Copy, X, Search,
} from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { JobOpening, Candidate, CandidateStage, HireResult, OfferResult } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
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
/**
 * The bar under each column heading. Colour is how a board of six near-identical columns becomes
 * scannable — the eye finds "Offer" by its amber rule long before it reads the word.
 */
const STAGE_BAR: Record<CandidateStage, string> = {
  APPLIED: "bg-fg/25", SCREENING: "bg-aqua", INTERVIEW: "bg-violet", OFFER: "bg-amber-400",
  HIRED: "bg-emerald-400", REJECTED: "bg-rose-400",
};
/** The ring shown on a column while a card is dragged over it, in that column's own colour. */
const STAGE_DROP: Record<CandidateStage, string> = {
  APPLIED: "ring-fg/30 bg-fg/[0.06]", SCREENING: "ring-aqua/50 bg-aqua/[0.06]",
  INTERVIEW: "ring-violet/50 bg-violet/[0.06]", OFFER: "ring-amber-400/50 bg-amber-400/[0.06]",
  HIRED: "ring-emerald-400/50 bg-emerald-400/[0.06]", REJECTED: "ring-rose-400/50 bg-rose-400/[0.06]",
};

/** Six fixed hues for candidate avatars, picked from the name so the same person keeps the same one. */
const AVATAR_TONES = [
  "bg-violet/20 text-violet", "bg-aqua/20 text-aqua", "bg-amber-400/20 text-amber-300",
  "bg-emerald-400/20 text-emerald-400", "bg-rose-400/20 text-rose-400", "bg-sky-400/20 text-sky-400",
];

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  return ((parts[0][0] ?? "") + (parts.length > 1 ? parts[parts.length - 1][0] ?? "" : "")).toUpperCase();
}

function toneOf(name: string): string {
  let sum = 0;
  for (let i = 0; i < name.length; i++) sum += name.charCodeAt(i);
  return AVATAR_TONES[sum % AVATAR_TONES.length];
}

/** The hiring pipeline board for one opening — candidates in columns by stage. */
export default function PipelinePage() {
  const { jobId } = useParams<{ jobId: string }>();
  const [job, setJob] = useState<JobOpening | null>(null);
  const [candidates, setCandidates] = useState<Candidate[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  /** Which candidate is being offered or hired — the panel above the board (PD-20). */
  const [action, setAction] = useState<{ candidate: Candidate; kind: "OFFER" | "HIRE" } | null>(null);
  /** Drag state. `dragId` is the card in flight; `overStage` is the column it is hovering. */
  const [dragId, setDragId] = useState<string | null>(null);
  const [overStage, setOverStage] = useState<CandidateStage | null>(null);
  const [query, setQuery] = useState("");

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

  /** Finish a drag: move if it actually changed column, and clear the drag state either way. */
  function drop(stage: CandidateStage) {
    const c = candidates?.find((x) => x.id === dragId);
    setDragId(null);
    setOverStage(null);
    if (c && c.stage !== stage) move(c, stage);
  }

  const needle = query.trim().toLowerCase();
  const visible = needle
    ? (candidates ?? []).filter((c) =>
        c.name.toLowerCase().includes(needle) || (c.email ?? "").toLowerCase().includes(needle))
    : candidates ?? [];

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
        <div className="flex items-center gap-2">
          {/* Filters the board rather than a list: every column narrows at once, so you can see
              where a named person actually is without opening six of them. */}
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg/30" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Find a candidate"
              aria-label="Find a candidate"
              className="h-11 w-52 rounded-lg border border-fg/15 bg-fg/5 pl-9 pr-3 text-sm text-fg placeholder:text-fg/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
            />
          </div>
          <Button onClick={() => setAdding((v) => !v)}><Plus className="h-4 w-4" /> Add candidate</Button>
        </div>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}
      {adding && <AddCandidate jobId={jobId} onAdded={(c) => { setCandidates((cur) => [...(cur ?? []), c]); setAdding(false); }} onCancel={() => setAdding(false)} />}

      {action && (
        <OfferOrHirePanel
          key={`${action.candidate.id}-${action.kind}`}
          candidate={action.candidate}
          kind={action.kind}
          job={job}
          onDone={(c) => replace(c)}
          onClose={() => setAction(null)}
        />
      )}

      {candidates === null ? (
        <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <div className="mt-6 overflow-x-auto pb-2">
          <div className="flex gap-3" style={{ minWidth: "min-content" }}>
            {STAGES.map((stage) => {
              const list = visible.filter((c) => c.stage === stage);
              const isTarget = overStage === stage && dragId !== null;
              return (
                <div key={stage} className="w-64 shrink-0">
                  {/* Heading: the stage's own colour as a rule under it, and a count that stays put.
                      Six grey columns all look alike; the rule is what makes them findable. */}
                  <div className="mb-2 px-1">
                    <div className="flex items-center justify-between">
                      <span className={cn("text-sm font-medium", STAGE_ACCENT[stage])}>{STAGE_LABEL[stage]}</span>
                      <span className="rounded-full bg-fg/10 px-1.5 text-xs tabular-nums text-fg/50">{list.length}</span>
                    </div>
                    <div className={cn("mt-1.5 h-0.5 w-full rounded-full opacity-60", STAGE_BAR[stage])} />
                  </div>
                  {/* The column is the drop target. dragOver must preventDefault or the browser
                      refuses the drop outright — that single line is why HTML5 DnD "doesn't work"
                      more often than not. */}
                  <div
                    onDragOver={(e) => { if (dragId) { e.preventDefault(); setOverStage(stage); } }}
                    onDragLeave={() => setOverStage((s) => (s === stage ? null : s))}
                    onDrop={(e) => { e.preventDefault(); drop(stage); }}
                    className={cn(
                      "flex min-h-[6rem] flex-col gap-2 rounded-xl p-2 transition-colors",
                      isTarget ? cn("ring-2", STAGE_DROP[stage]) : "bg-fg/[0.03]",
                    )}
                  >
                    {list.map((c) => (
                      <CandidateCard
                        key={c.id}
                        c={c}
                        dragging={dragId === c.id}
                        onDragStart={() => setDragId(c.id)}
                        onDragEnd={() => { setDragId(null); setOverStage(null); }}
                        onMove={(s) => move(c, s)}
                        onRemove={() => remove(c)}
                        onAction={(kind) => setAction({ candidate: c, kind })}
                      />
                    ))}
                    {list.length === 0 && (
                      <p className="px-2 py-4 text-center text-xs text-fg/30">
                        {isTarget ? "Drop here" : needle ? "No match" : "Nothing here yet"}
                      </p>
                    )}
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

function CandidateCard({ c, dragging, onDragStart, onDragEnd, onMove, onRemove, onAction }: {
  c: Candidate;
  dragging: boolean;
  onDragStart: () => void;
  onDragEnd: () => void;
  onMove: (s: CandidateStage) => void;
  onRemove: () => void;
  onAction: (kind: "OFFER" | "HIRE") => void;
}) {
  return (
    <div
      draggable
      onDragStart={(e) => {
        // A drag begun on the select or a button would swallow the click that was actually meant.
        // Refusing it there keeps every control on the card usable.
        if ((e.target as HTMLElement).closest("select,button,a")) { e.preventDefault(); return; }
        e.dataTransfer.effectAllowed = "move";
        // Firefox will not start a drag unless some data is set.
        e.dataTransfer.setData("text/plain", c.id);
        onDragStart();
      }}
      onDragEnd={onDragEnd}
      className={cn(
        "cursor-grab rounded-lg border border-fg/10 bg-surface p-2.5 shadow-sm transition",
        "hover:border-fg/25 active:cursor-grabbing",
        dragging && "opacity-40",
      )}
    >
      <div className="flex items-start gap-2">
        <span className={cn(
          "grid h-7 w-7 shrink-0 place-items-center rounded-full text-[11px] font-semibold",
          toneOf(c.name),
        )} aria-hidden>
          {initialsOf(c.name)}
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{c.name}</p>
          {c.email && <p className="flex items-center gap-1 truncate text-xs text-fg/40"><Mail className="h-3 w-3 shrink-0" />{c.email}</p>}
        </div>
        <button onClick={onRemove} className="text-fg/25 hover:text-rose-400" aria-label="Remove"><Trash2 className="h-3.5 w-3.5" /></button>
      </div>
      {(c.rating != null || c.source || c.resumeUrl) && (
        <div className="mt-1.5 flex items-center gap-2 pl-9">
          {c.rating != null && (
            <span className="inline-flex items-center gap-0.5 text-xs text-amber-400">
              {c.rating}<Star className="h-3 w-3 fill-amber-400" />
            </span>
          )}
          {c.source && <span className="rounded-full bg-fg/10 px-1.5 py-0.5 text-[10px] text-fg/50">{c.source}</span>}
          {c.resumeUrl && <a href={c.resumeUrl} target="_blank" rel="noreferrer" className="text-fg/40 hover:text-violet" aria-label="Resume"><FileText className="h-3.5 w-3.5" /></a>}
        </div>
      )}
      {/* Kept, not replaced by the drag. Dragging is a mouse gesture: it is unavailable to anyone
          on a keyboard and awkward on a touchscreen, so the select stays as the way that always
          works. The board gains a faster path; it does not lose the reliable one. */}
      <select value={c.stage} onChange={(e) => onMove(e.target.value as CandidateStage)}
        aria-label={`Move ${c.name} to another stage`}
        className="mt-2 w-full rounded-md border border-fg/10 bg-fg/5 px-1.5 py-1 text-xs text-fg/70">
        {STAGES.map((s) => <option key={s} value={s} className="bg-surface">Move to {STAGE_LABEL[s]}</option>)}
      </select>

      {/* The two moments that produce a letter, offered where the decision is made (PD-20). */}
      {c.stage !== "REJECTED" && c.stage !== "HIRED" && (
        <div className="mt-1.5 flex gap-1.5">
          <button
            onClick={() => onAction("OFFER")}
            className="flex flex-1 items-center justify-center gap-1 rounded-md border border-fg/10 py-1 text-[11px] text-fg/60 hover:border-amber-400/40 hover:text-amber-400"
          >
            <FileSignature className="h-3 w-3" /> Offer
          </button>
          <button
            onClick={() => onAction("HIRE")}
            className="flex flex-1 items-center justify-center gap-1 rounded-md border border-fg/10 py-1 text-[11px] text-fg/60 hover:border-emerald-400/40 hover:text-emerald-400"
          >
            <UserCheck className="h-3 w-3" /> Hire
          </button>
        </div>
      )}
    </div>
  );
}

/**
 * Make an offer, or hire. Both raise a letter; hiring also sends the invitation that gives them a
 * login and carries the agreed role onto their employee profile.
 */
function OfferOrHirePanel({ candidate, kind, job, onDone, onClose }: {
  candidate: Candidate;
  kind: "OFFER" | "HIRE";
  job: JobOpening | null;
  onDone: (c: Candidate) => void;
  onClose: () => void;
}) {
  const [jobTitle, setJobTitle] = useState(job?.title ?? "");
  const [startDate, setStartDate] = useState("");
  const [workLocation, setWorkLocation] = useState(job?.location ?? "");
  const [annualSalary, setAnnualSalary] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [role, setRole] = useState<"ADMIN" | "HR" | "MANAGER" | "MEMBER">("MEMBER");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<OfferResult | HireResult | null>(null);

  const hiring = kind === "HIRE";

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      const r = hiring
        ? await api.hireCandidate(candidate.id, {
            role, jobTitle: jobTitle || undefined, startDate: startDate || undefined,
          })
        : await api.makeOffer(candidate.id, {
            jobTitle: jobTitle || undefined,
            startDate: startDate || undefined,
            workLocation: workLocation || undefined,
            annualSalary: annualSalary ? Number(annualSalary) : undefined,
            currency: currency || undefined,
          });
      setResult(r);
      onDone(r.candidate);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "That didn't work");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="mt-6">
      <div className="flex items-start justify-between gap-3">
        <div>
          <CardTitle>{hiring ? `Hire ${candidate.name}` : `Make an offer to ${candidate.name}`}</CardTitle>
          <p className="mt-1 text-sm text-fg/50">
            {hiring
              ? "Sends their joining link, records the agreed role, and raises the joining letter."
              : "Moves them to Offer and raises the offer letter on your letterpad."}
          </p>
        </div>
        <button onClick={onClose} className="text-fg/30 hover:text-fg" aria-label="Close">
          <X className="h-4 w-4" />
        </button>
      </div>

      {error && <Alert tone="error" className="mt-4">{error}</Alert>}

      {result ? (
        <div className="mt-4 flex flex-col gap-3">
          <Alert tone={result.letterNote ? "warning" : "success"}>
            {result.letterNote ?? `${result.documentTitle} was raised.`}
          </Alert>
          {result.documentId && (
            <Link href={`/documents/${result.documentId}`}>
              <Button variant="secondary"><FileText className="h-4 w-4" /> Open the letter</Button>
            </Link>
          )}
          {"joinLink" in result && result.joinLink && <JoinLink link={result.joinLink} />}
        </div>
      ) : (
        <>
          <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="Job title" htmlFor="o-title">
              <Input id="o-title" value={jobTitle} onChange={(e) => setJobTitle(e.target.value)} />
            </Field>
            <Field label="Start date" htmlFor="o-start">
              <Input id="o-start" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
            </Field>
            {hiring ? (
              <Field label="Role in the app" htmlFor="o-role"
                hint="What they can see once they sign in.">
                <select id="o-role" value={role}
                  onChange={(e) => setRole(e.target.value as typeof role)}
                  className="h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet">
                  <option value="MEMBER" className="bg-surface">Employee</option>
                  <option value="MANAGER" className="bg-surface">Manager</option>
                  <option value="HR" className="bg-surface">HR</option>
                  <option value="ADMIN" className="bg-surface">Admin</option>
                </select>
              </Field>
            ) : (
              <>
                <Field label="Location" htmlFor="o-loc">
                  <Input id="o-loc" value={workLocation} onChange={(e) => setWorkLocation(e.target.value)} />
                </Field>
                <Field label="Annual salary" htmlFor="o-sal">
                  <div className="flex gap-2">
                    <Input id="o-cur" value={currency} onChange={(e) => setCurrency(e.target.value)}
                      className="w-20" aria-label="Currency" />
                    <Input id="o-sal" inputMode="numeric" value={annualSalary}
                      onChange={(e) => setAnnualSalary(e.target.value.replace(/[^\d.]/g, ""))} />
                  </div>
                </Field>
              </>
            )}
          </div>

          {hiring && !candidate.email && (
            <Alert tone="warning" className="mt-4">
              This candidate has no email address, so there is nowhere to send their joining link. Add
              one first.
            </Alert>
          )}

          <div className="mt-4 flex gap-2">
            <Button onClick={submit} disabled={busy || (hiring && !candidate.email)}>
              {busy ? <Loader2 className="h-4 w-4 animate-spin" />
                : hiring ? <UserCheck className="h-4 w-4" /> : <FileSignature className="h-4 w-4" />}
              {hiring ? "Hire & invite" : "Make offer"}
            </Button>
            <Button variant="ghost" onClick={onClose}>Cancel</Button>
          </div>
        </>
      )}
    </Card>
  );
}

/** The joining link, shown so it can be passed on however the admin likes — mail is a convenience. */
function JoinLink({ link }: { link: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <div>
      <p className="text-xs font-medium uppercase tracking-wide text-fg/40">Joining link</p>
      <div className="mt-1.5 flex gap-2">
        <Input value={link} readOnly onFocus={(e) => e.currentTarget.select()} />
        <Button
          variant="secondary"
          onClick={() => {
            void navigator.clipboard.writeText(link).then(() => {
              setCopied(true);
              setTimeout(() => setCopied(false), 2000);
            });
          }}
        >
          <Copy className="h-4 w-4" /> {copied ? "Copied" : "Copy"}
        </Button>
      </div>
      <p className="mt-1.5 text-xs text-fg/40">
        Also emailed to them. Their profile — title, start date, department — is filled in the moment
        they accept.
      </p>
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
