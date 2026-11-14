"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useSession } from "@/hooks/useSession";

/**
 * Which modules this company has, plus "ATTENDANCE": whether it tracks attendance in Orbit at all.
 *
 * Cosmetic, like the nav's own check: the server refuses a module the company has not bought
 * whatever the screen shows (FeatureGuardFilter). This keeps a smaller plan from offering tiles and
 * links that answer "not in your plan" when clicked.
 *
 * Loaded once per company and shared, so the shell, the dashboard and a page do not each ask.
 */
export type Features = Record<string, boolean>;

const cache = new Map<string, Promise<Features>>();

function load(companyId: string): Promise<Features> {
  let p = cache.get(companyId);
  if (!p) {
    p = Promise.all([api.companyFeatures(), api.getSettings().catch(() => null)]).then(([list, settings]) => ({
      ...Object.fromEntries(list.map((f) => [f.feature, f.enabled])),
      // Unknown settings leave it on: never hide check-in because a read failed.
      ATTENDANCE: settings?.trackAttendance !== false,
    }));
    p.catch(() => cache.delete(companyId));
    cache.set(companyId, p);
  }
  return p;
}

/** Null until known; an empty map (everything on) when it could not be read. */
export function useFeatures(): Features | null {
  const { me, status } = useSession();
  const companyId = status === "authenticated" ? me?.company?.id ?? null : null;
  const [features, setFeatures] = useState<Features | null>(null);
  useEffect(() => {
    if (!companyId) return;
    let live = true;
    load(companyId).then((f) => { if (live) setFeatures(f); }).catch(() => { if (live) setFeatures({}); });
    return () => { live = false; };
  }, [companyId]);
  return features;
}

/**
 * Whether a module is on. Unknown counts as on: hiding something the customer has paid for because
 * a request was slow is worse than briefly showing something they have not.
 */
export function isOn(features: Features | null, feature: string): boolean {
  return features === null || features[feature] !== false;
}

/** Forget what was loaded, after a change to the company's own settings. */
export function forgetFeatures(): void {
  cache.clear();
}
