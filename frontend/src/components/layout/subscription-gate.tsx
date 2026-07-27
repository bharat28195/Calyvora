"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, Lock } from "lucide-react";
import { api } from "@/lib/api";
import type { SubscriptionView } from "@/lib/types";

/**
 * Guards the company app against subscription state (PD-10 pts 6/8). If the owner has ended the
 * subscription (or it lapsed), the whole app is covered by a lock popup. If it's within two weeks of
 * expiry, a dismissible banner nudges the admin — the "notification as expiry nears".
 */
export function SubscriptionGate() {
  const [sub, setSub] = useState<SubscriptionView | null>(null);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    api.mySubscription().then(setSub).catch(() => setSub(null));
  }, []);

  if (!sub) return null;

  if (sub.locked) {
    return (
      <div className="fixed inset-0 z-[100] flex items-center justify-center bg-app/90 p-6 backdrop-blur">
        <div className="max-w-md rounded-2xl border border-fg/10 bg-surface p-8 text-center shadow-xl">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-red-500/15">
            <Lock className="h-7 w-7 text-red-400" />
          </div>
          <h2 className="mt-4 text-xl font-semibold">Your subscription has ended</h2>
          <p className="mt-2 text-sm text-fg/60">
            Access to your workspace is paused. Please contact your Priority HR account manager to
            renew and restore access for your team.
          </p>
          <p className="mt-4 text-xs text-fg/40">Priority HR · support@priorityhr.app</p>
        </div>
      </div>
    );
  }

  const expiring = sub.daysLeft != null && sub.daysLeft <= 14;
  if (expiring && !dismissed) {
    return (
      <div className="sticky top-0 z-40 flex items-center justify-center gap-2 bg-amber-500/15 px-4 py-2 text-center text-sm text-amber-300">
        <AlertTriangle className="h-4 w-4 shrink-0" />
        <span>
          Your subscription {sub.daysLeft! <= 0 ? "expires today" : `ends in ${sub.daysLeft} day${sub.daysLeft === 1 ? "" : "s"}`}.
          Renew soon to avoid interruption.
        </span>
        <button onClick={() => setDismissed(true)} className="ml-2 rounded px-1.5 text-amber-300/70 hover:text-amber-200">Dismiss</button>
      </div>
    );
  }

  return null;
}
