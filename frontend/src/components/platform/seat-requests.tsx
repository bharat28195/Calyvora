"use client";

import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { SeatRequest } from "@/lib/types";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";

/**
 * Companies that have outgrown the seats they pay for and are asking for more.
 *
 * <p>Self-contained: it loads and reloads its own queue, because it is now one section of a page
 * rather than a block inside the console's single scroll.
 */
export function SeatRequestsSection({ onChanged }: { onChanged?: () => void }) {
  const [requests, setRequests] = useState<SeatRequest[] | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => void api.platformSeatRequests().then(setRequests).catch(() => setRequests([]));
  useEffect(() => { load(); }, []);

  async function act(id: string, fn: () => Promise<unknown>) {
    setBusyId(id);
    setError(null);
    try {
      await fn();
      load();
      onChanged?.();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Action failed");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <Card>
      <CardTitle>Seat requests</CardTitle>
      <CardDescription>Companies that have outgrown the seats they pay for.</CardDescription>

      {error && <Alert tone="error" className="mt-3">{error}</Alert>}

      {requests === null ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-fg/50">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </div>
      ) : requests.length === 0 ? (
        <p className="mt-3 text-sm text-fg/50">Nothing waiting.</p>
      ) : (
        <div className="mt-3 flex flex-col divide-y divide-fg/5">
          {requests.map((r) => (
            <div key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
              <div className="min-w-0">
                <p className="text-sm font-medium">{r.companyName} · {r.currentSeats} → {r.requestedSeats} seats</p>
                {r.note && <p className="truncate text-xs text-fg/40">{r.note}</p>}
              </div>
              <div className="flex gap-2">
                <Button size="sm" disabled={busyId === r.id} onClick={() => act(r.id, () => api.approveSeatRequest(r.id))}>
                  Approve
                </Button>
                <Button size="sm" variant="ghost" disabled={busyId === r.id} onClick={() => act(r.id, () => api.declineSeatRequest(r.id))}>
                  Decline
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}
