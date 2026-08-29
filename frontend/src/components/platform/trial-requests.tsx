"use client";

import { useEffect, useState } from "react";
import { Clock, Loader2, Mail, Phone, Users } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { TrialRequest } from "@/lib/types";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";

/**
 * The queue behind the website's "free trial" button (PD-21).
 *
 * <p>This is the gate: a request sits here doing nothing until someone approves it, and approving is
 * the only thing on the platform that turns an enquiry into a workspace with a login. The form asks
 * for the same three terms as "New company" — starting password, seats, months — because that is
 * exactly what it does underneath.
 */
export function TrialRequestsSection({ onChanged, onWaitingCount }: {
  onChanged: () => void;
  /** Lifts the "waiting on you" count so the tab it lives behind can show it without being opened. */
  onWaitingCount?: (n: number) => void;
}) {
  const [requests, setRequests] = useState<TrialRequest[] | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [terms, setTerms] = useState({ password: "", seats: "10", months: "1" });

  function load() {
    api.platformTrialRequests().then(setRequests).catch(() => setRequests([]));
  }
  useEffect(() => { load(); }, []);

  const waiting = requests?.filter((r) => r.status === "NEW") ?? [];
  const decided = requests?.filter((r) => r.status !== "NEW") ?? [];

  useEffect(() => { onWaitingCount?.(waiting.length); }, [waiting.length, onWaitingCount]);

  async function approve(r: TrialRequest) {
    setBusyId(r.id);
    setError(null);
    try {
      await api.approveTrialRequest(r.id, {
        password: terms.password,
        seats: Number(terms.seats) || 10,
        months: Number(terms.months) || 1,
      });
      setOpenId(null);
      setTerms({ password: "", seats: "10", months: "1" });
      load();
      // The new company belongs in the table above, and the counts on this page just changed.
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not approve this request");
    } finally {
      setBusyId(null);
    }
  }

  async function decline(r: TrialRequest) {
    setBusyId(r.id);
    setError(null);
    try {
      await api.declineTrialRequest(r.id);
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not decline this request");
    } finally {
      setBusyId(null);
    }
  }

  if (requests === null) {
    return (
      <Card className="mt-4">
        <CardTitle>Trial requests</CardTitle>
        <div className="mt-3 flex items-center gap-2 text-sm text-fg/50">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </div>
      </Card>
    );
  }

  return (
    <Card className="mt-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <CardTitle>Trial requests</CardTitle>
        {waiting.length > 0 && (
          <span className="rounded-full bg-amber-500/15 px-2.5 py-1 text-xs font-medium text-amber-300">
            {waiting.length} waiting on you
          </span>
        )}
      </div>
      <CardDescription>
        People who asked for a trial on the website. Nobody can sign in until you approve one.
      </CardDescription>

      {error && <Alert tone="error" className="mt-3">{error}</Alert>}

      {waiting.length === 0 && decided.length === 0 && (
        <p className="mt-4 text-sm text-fg/50">No requests yet.</p>
      )}

      <div className="mt-4 flex flex-col gap-3">
        {waiting.map((r) => (
          <div key={r.id} className="rounded-xl border border-fg/10 p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="font-medium">{r.companyName}</p>
                <p className="text-sm text-fg/60">{r.contactName}</p>
                <div className="mt-1.5 flex flex-wrap gap-x-4 gap-y-1 text-xs text-fg/50">
                  <span className="inline-flex items-center gap-1">
                    <Mail className="h-3.5 w-3.5" />
                    <a href={`mailto:${r.email}`} className="hover:text-fg">{r.email}</a>
                  </span>
                  {r.phone && (
                    <span className="inline-flex items-center gap-1">
                      <Phone className="h-3.5 w-3.5" />
                      <a href={`tel:${r.phone}`} className="hover:text-fg">{r.phone}</a>
                    </span>
                  )}
                  {r.teamSize && (
                    <span className="inline-flex items-center gap-1">
                      <Users className="h-3.5 w-3.5" /> {r.teamSize}
                    </span>
                  )}
                  <span className="inline-flex items-center gap-1">
                    <Clock className="h-3.5 w-3.5" /> {waitedFor(r.createdAt)}
                  </span>
                </div>
                {r.note && <p className="mt-2 text-sm text-fg/70">“{r.note}”</p>}
              </div>
              <div className="flex gap-2">
                <Button size="sm" onClick={() => setOpenId(openId === r.id ? null : r.id)}>
                  {openId === r.id ? "Cancel" : "Approve"}
                </Button>
                <Button size="sm" variant="ghost" disabled={busyId === r.id} onClick={() => decline(r)}>
                  Decline
                </Button>
              </div>
            </div>

            {openId === r.id && (
              <div className="mt-4 border-t border-fg/10 pt-4">
                <p className="text-sm text-fg/60">
                  This creates the workspace and makes <span className="text-fg">{r.email}</span> its
                  admin. Pass the password on yourself — we email them that the trial is ready, never
                  the credential.
                </p>
                <div className="mt-3 grid gap-3 sm:grid-cols-3">
                  <Field label="Starting password" htmlFor={`pw-${r.id}`}>
                    <Input id={`pw-${r.id}`} type="text" value={terms.password} autoComplete="off"
                      onChange={(e) => setTerms((t) => ({ ...t, password: e.target.value }))}
                      placeholder="At least 8 characters" />
                  </Field>
                  <Field label="Seats" htmlFor={`seats-${r.id}`}>
                    <Input id={`seats-${r.id}`} type="number" min={1} value={terms.seats}
                      onChange={(e) => setTerms((t) => ({ ...t, seats: e.target.value }))} />
                  </Field>
                  <Field label="Trial length (months)" htmlFor={`months-${r.id}`}>
                    <Input id={`months-${r.id}`} type="number" min={1} value={terms.months}
                      onChange={(e) => setTerms((t) => ({ ...t, months: e.target.value }))} />
                  </Field>
                </div>
                <Button className="mt-3" disabled={busyId === r.id || terms.password.length < 8}
                  onClick={() => approve(r)}>
                  {busyId === r.id && <Loader2 className="h-4 w-4 animate-spin" />}
                  Create workspace and approve
                </Button>
              </div>
            )}
          </div>
        ))}
      </div>

      {decided.length > 0 && (
        <details className="mt-4">
          <summary className="cursor-pointer text-sm text-fg/50 hover:text-fg">
            {decided.length} decided
          </summary>
          <div className="mt-2 flex flex-col divide-y divide-fg/5">
            {decided.map((r) => (
              <div key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                <span className="text-fg/70">{r.companyName} · {r.email}</span>
                <span className={r.status === "APPROVED" ? "text-emerald-400" : "text-fg/40"}>
                  {r.status === "APPROVED" ? "Approved" : "Declined"}
                </span>
              </div>
            ))}
          </div>
        </details>
      )}
    </Card>
  );
}

/**
 * How long someone has been waiting, which is the only thing about the timestamp that matters here —
 * a date tells you when they asked, this tells you whether you're being slow.
 */
function waitedFor(createdAt: string): string {
  const ms = Date.now() - new Date(createdAt).getTime();
  if (!Number.isFinite(ms) || ms < 0) return "just now";
  const hours = Math.floor(ms / 3_600_000);
  if (hours < 1) return "just now";
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return days === 1 ? "yesterday" : `${days} days ago`;
}
