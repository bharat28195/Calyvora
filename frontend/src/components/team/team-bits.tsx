"use client";

import { useEffect, useState } from "react";
import { Users } from "lucide-react";
import { cn } from "@/lib/utils";
import { api } from "@/lib/api";
import type { TeamStanding } from "@/lib/types";

/**
 * Direct reports, or the whole org beneath you.
 *
 * The default is everyone, because a lead whose reports are themselves leads would otherwise open
 * "my team" and see three names out of thirty. The toggle exists because the two groups mean
 * genuinely different things: the direct reports are the people whose leave you decide, the rest are
 * the people you are accountable for.
 */
export function ScopeToggle({ direct, onChange, standing }: {
  direct: boolean;
  onChange: (direct: boolean) => void;
  standing: TeamStanding | null;
}) {
  // Nothing to choose between when the two counts are the same — a flat team of five has no skip
  // level, and a toggle that cannot change the answer is furniture.
  if (standing && standing.directCount === standing.totalCount) return null;
  return (
    <div className="inline-flex rounded-lg border border-fg/10 p-0.5 text-sm">
      <button
        type="button"
        onClick={() => onChange(false)}
        className={cn("rounded-md px-3 py-1.5 transition-colors",
          !direct ? "bg-violet/10 font-medium text-violet" : "text-fg/60 hover:text-fg")}
      >
        Everyone{standing ? ` (${standing.totalCount})` : ""}
      </button>
      <button
        type="button"
        onClick={() => onChange(true)}
        className={cn("rounded-md px-3 py-1.5 transition-colors",
          direct ? "bg-violet/10 font-medium text-violet" : "text-fg/60 hover:text-fg")}
      >
        Direct reports{standing ? ` (${standing.directCount})` : ""}
      </button>
    </div>
  );
}

/** Whether the signed-in person leads anybody, and how many. Null while it is still being fetched. */
export function useTeamStanding(): TeamStanding | null {
  const [standing, setStanding] = useState<TeamStanding | null>(null);
  useEffect(() => {
    api.myTeamStanding().then(setStanding).catch(() => setStanding(null));
  }, []);
  return standing;
}

/**
 * What a team screen shows to somebody who has nobody reporting to them.
 *
 * They should not have been able to reach it — the nav hides the section — but a bookmark, a shared
 * link or a reporting line that changed yesterday all land here, and an empty table with no
 * explanation reads as the product being broken rather than as the correct answer.
 */
export function NoTeam() {
  return (
    <div className="mt-10 flex flex-col items-center gap-3 rounded-xl border border-dashed border-fg/15 p-10 text-center">
      <Users className="h-6 w-6 text-fg/30" />
      <p className="text-sm font-medium">Nobody reports to you yet.</p>
      <p className="max-w-sm text-sm text-fg/50">
        This section appears as soon as somebody is assigned to you as their manager. HR sets that on
        the person&rsquo;s profile.
      </p>
    </div>
  );
}

/** The section header every team screen shares, so they read as one place rather than five. */
export function TeamHeader({ title, blurb, children }: {
  title: string;
  blurb: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        <p className="mt-1 text-fg/50">{blurb}</p>
      </div>
      {children}
    </div>
  );
}
