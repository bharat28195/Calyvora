"use client";

import { useEffect, useState } from "react";
import { Loader2, Check, X, Clock } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Regularization } from "@/lib/types";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";

/** Manager/HR approval queue for attendance regularizations. */
export default function RegularizationsPage() {
  const [items, setItems] = useState<Regularization[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  function load() {
    api.pendingRegularizations().then(setItems).catch((e) => { setItems([]); setError(e instanceof ApiError ? e.message : "Failed to load"); });
  }
  useEffect(() => { load(); }, []);

  async function decide(id: string, approve: boolean) {
    setBusyId(id); setError(null);
    try { await (approve ? api.approveRegularization(id) : api.rejectRegularization(id)); load(); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Action failed"); }
    finally { setBusyId(null); }
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">Regularizations</h1>
      <p className="mt-1 text-fg/50">Approve your team&apos;s attendance fix-up requests. Approving marks the day present.</p>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {items === null ? (
        <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : items.length === 0 ? (
        <Card className="mt-6"><p className="inline-flex items-center gap-2 text-sm text-fg/50"><Clock className="h-4 w-4" /> No pending requests.</p></Card>
      ) : (
        <div className="mt-6 flex flex-col gap-2">
          {items.map((r) => (
            <Card key={r.id} className="py-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-medium">{r.employeeName}</p>
                  <p className="text-xs text-fg/50">
                    {r.date}{r.checkIn ? ` · in ${r.checkIn.slice(0, 5)}` : ""}{r.checkOut ? ` · out ${r.checkOut.slice(0, 5)}` : ""}
                    {r.reason ? ` — ${r.reason}` : ""}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button size="sm" disabled={busyId === r.id} onClick={() => decide(r.id, true)}><Check className="h-4 w-4" /> Approve</Button>
                  <Button size="sm" variant="ghost" disabled={busyId === r.id} onClick={() => decide(r.id, false)}><X className="h-4 w-4" /> Reject</Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
