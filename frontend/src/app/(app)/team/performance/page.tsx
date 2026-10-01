"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, ArrowRight } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { PerformanceReview } from "@/lib/types";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { NoTeam, TeamHeader, useTeamStanding } from "@/components/team/team-bits";

/**
 * Reviews for everyone below you, not only your direct reports.
 *
 * The API used to key this on the review row's own manager column, one level deep, so a head of
 * department with four leads under them saw four reviews and none of the thirty their leads write.
 * It walks the whole tree now.
 *
 * No salary figures. A hike is decided as a percentage, which is what the judgement needs; the
 * absolute number is HR's and the server strips it for everyone else (PerformanceReviewResponse
 * .withoutPay), so it is not merely hidden here.
 */
export default function TeamPerformancePage() {
  const standing = useTeamStanding();
  const [reviews, setReviews] = useState<PerformanceReview[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.teamPerformance().then(setReviews).catch((e) => {
      setReviews([]);
      setError(e instanceof ApiError ? e.message : "Failed to load reviews");
    });
  }, []);

  if (standing && !standing.leadsTeam) {
    return <div><TeamHeader title="Team performance" blurb="Reviews for the people you lead." /><NoTeam /></div>;
  }

  const waitingOnMe = reviews?.filter((r) => r.status === "PENDING_MANAGER") ?? [];

  return (
    <div>
      <TeamHeader title="Team performance" blurb="Where each review has got to, for everyone below you." />

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {waitingOnMe.length > 0 && (
        <Card className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm">
            <span className="font-medium">{waitingOnMe.length}</span> review{waitingOnMe.length === 1 ? " is" : "s are"} waiting
            to be written.
          </p>
          {/* Writing happens on My review, which holds the editable cards. This page is the overview
              across the whole org below you; the one you can actually edit is a subset of it. */}
          <Link href="/performance/review" className="inline-flex items-center gap-1.5 text-sm font-medium text-violet hover:underline">
            Write them <ArrowRight className="h-4 w-4" />
          </Link>
        </Card>
      )}

      <div className="mt-6 flex flex-col gap-2">
        {reviews === null ? (
          <div className="flex justify-center py-10"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
        ) : reviews.length === 0 ? (
          <Card className="text-sm text-fg/50">
            No reviews yet. They appear once HR opens a review cycle.
          </Card>
        ) : (
          reviews.map((r) => (
            <Card key={r.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
              <div>
                <div className="font-medium">{r.employeeName}</div>
                <div className="text-xs text-fg/50">
                  {[r.jobTitle, r.cycleName].filter(Boolean).join(" · ")}
                  {r.managerName ? ` · reviewed by ${r.managerName}` : ""}
                </div>
              </div>
              <div className="flex items-center gap-3">
                {r.rating != null && <span className="text-sm tabular-nums text-fg/70">{r.rating}/5</span>}
                {r.hikePercent != null && <span className="text-sm tabular-nums text-fg/70">+{r.hikePercent}%</span>}
                <Badge value={r.status} />
              </div>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
