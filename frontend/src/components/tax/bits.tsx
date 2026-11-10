"use client";

import { useRef, useState } from "react";
import { Check, Clock, FileText, Loader2, Paperclip, X, AlertCircle, CircleDashed } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { ProofStatus, TaxProofFile } from "@/lib/types";
import { money } from "@/lib/format";
import { cn } from "@/lib/utils";

/** A rupee amount: a plain number box with the symbol in front, no spinners, nothing clever. */
export function MoneyInput({ value, onChange, disabled, placeholder = "0", id, className }: {
  value: string; onChange: (v: string) => void; disabled?: boolean; placeholder?: string; id?: string; className?: string;
}) {
  return (
    <div className={cn("relative", className)}>
      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm text-fg/40">₹</span>
      <input id={id} inputMode="numeric" disabled={disabled} placeholder={placeholder}
        value={value} onChange={(e) => onChange(e.target.value.replace(/[^\d.]/g, ""))}
        className="h-10 w-full rounded-lg border border-fg/15 bg-fg/5 pl-7 pr-3 text-right text-sm tabular-nums text-fg placeholder:text-fg/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet disabled:opacity-50" />
    </div>
  );
}

export function num(v: string | number | null | undefined): number {
  if (v == null) return 0;
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

const STATUS: Record<ProofStatus, { label: string; tone: string; icon: React.ReactNode }> = {
  NONE: { label: "No proof yet", tone: "bg-fg/5 text-fg/50", icon: <CircleDashed className="h-3 w-3" /> },
  SUBMITTED: { label: "With HR", tone: "bg-amber-500/15 text-amber-700 dark:text-amber-300", icon: <Clock className="h-3 w-3" /> },
  ACCEPTED: { label: "Accepted", tone: "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400", icon: <Check className="h-3 w-3" /> },
  PARTIAL: { label: "Part accepted", tone: "bg-sky-500/15 text-sky-600 dark:text-sky-400", icon: <Check className="h-3 w-3" /> },
  REJECTED: { label: "Rejected", tone: "bg-red-500/15 text-red-600 dark:text-red-400", icon: <AlertCircle className="h-3 w-3" /> },
};

export function ProofBadge({ status, accepted }: { status: ProofStatus; accepted?: number | null }) {
  const s = STATUS[status];
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium", s.tone)}>
      {s.icon}{s.label}{status === "PARTIAL" && accepted != null ? ` · ${money(accepted)}` : ""}
    </span>
  );
}

/** Open a proof in a new tab. A plain link would not carry the sign-in, so it goes through a blob. */
export async function openProof(id: string) {
  const blob = await api.taxProofBlob(id);
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener");
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/**
 * The proof row under a declared line: its status, the files, and — while proofs are open — a way to
 * add or remove one. Collapses to nothing when there is nothing to say.
 */
export function Proofs({ status, accepted, note, proofs, canUpload, onUpload, onDelete }: {
  status: ProofStatus;
  accepted?: number | null;
  note?: string | null;
  proofs: TaxProofFile[];
  canUpload: boolean;
  onUpload: (file: File) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!canUpload && proofs.length === 0 && status === "NONE") return null;

  async function pick(file: File | undefined) {
    if (!file) return;
    setBusy(true); setError(null);
    try { await onUpload(file); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Upload failed"); }
    finally { setBusy(false); if (input.current) input.current.value = ""; }
  }

  const locked = status === "ACCEPTED" || status === "PARTIAL";
  return (
    <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
      <ProofBadge status={status} accepted={accepted} />
      {proofs.map((p) => (
        <span key={p.id} className="inline-flex items-center gap-1 rounded-md border border-fg/10 bg-fg/[0.03] py-0.5 pl-1.5 pr-1">
          <button type="button" onClick={() => void openProof(p.id)} className="inline-flex items-center gap-1 text-fg/70 hover:text-violet">
            <FileText className="h-3 w-3" /><span className="max-w-[10rem] truncate">{p.fileName}</span>
          </button>
          {canUpload && !locked && (
            <button type="button" aria-label={`Remove ${p.fileName}`} onClick={() => void onDelete(p.id)}
              className="rounded p-0.5 text-fg/30 hover:bg-fg/10 hover:text-fg"><X className="h-3 w-3" /></button>
          )}
        </span>
      ))}
      {canUpload && !locked && (
        <>
          <input ref={input} type="file" accept="application/pdf,image/png,image/jpeg" className="hidden"
            onChange={(e) => void pick(e.target.files?.[0])} />
          <button type="button" disabled={busy} onClick={() => input.current?.click()}
            className="inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 font-medium text-violet hover:bg-violet/10 disabled:opacity-50">
            {busy ? <Loader2 className="h-3 w-3 animate-spin" /> : <Paperclip className="h-3 w-3" />} Add proof
          </button>
        </>
      )}
      {note && <span className="w-full text-fg/50">HR: {note}</span>}
      {error && <span className="w-full text-red-500">{error}</span>}
    </div>
  );
}

/** A thin bar: how full a ceiling is. */
export function Meter({ used, cap, label }: { used: number; cap: number; label?: string }) {
  const pct = cap > 0 ? Math.min(100, Math.round((used / cap) * 100)) : 0;
  return (
    <div>
      {label && (
        <div className="mb-1 flex items-baseline justify-between text-xs">
          <span className="text-fg/60">{label}</span>
          <span className="tabular-nums text-fg/50">{money(used)} of {money(cap)}</span>
        </div>
      )}
      <div className="h-2 w-full overflow-hidden rounded-full bg-fg/10">
        <div className={cn("h-full rounded-full transition-all", pct >= 100 ? "bg-emerald-500" : "bg-violet")} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
