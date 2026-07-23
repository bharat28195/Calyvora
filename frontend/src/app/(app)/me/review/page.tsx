"use client";

import { useEffect, useState } from "react";
import { Loader2, Users } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import type { PerformanceReview } from "@/lib/types";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { ReviewCard } from "@/components/performance/review-card";

/**
 * My review: my own self-assessment and, if I manage people, my reports' reviews to fill in. One
 * place for the whole review conversation from wherever the logged-in person sits in the org.
 */
export default function MyReviewPage() {
  const { me } = useSession();
  const isAdmin = me?.user.role === "OWNER" || me?.user.role === "ADMIN";
  const [mine, setMine] = useState<PerformanceReview[] | null>(null);
  const [team, setTeam] = useState<PerformanceReview[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([api.myReviews(), api.teamReviews()])
      .then(([m, t]) => { setMine(m); setTeam(t); })
      .catch((e) => { setMine([]); setError(e instanceof ApiError ? e.message : "Failed to load reviews"); });
  }, []);

  function replace(list: PerformanceReview[], updated: PerformanceReview): PerformanceReview[] {
    return list.map((r) => (r.id === updated.id ? updated : r));
  }

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">My review</h1>
        <p className="mt-1 text-fg/50">Your performance review — write your self-assessment and see your manager&apos;s feedback.</p>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {mine === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <>
          <section className="mt-8 space-y-5">
            {mine.length === 0 ? (
              <Card><p className="text-sm text-fg/50">No review cycle is open for you yet.</p></Card>
            ) : (
              mine.map((r) => (
                <ReviewCard key={r.id} review={r} perspective="self" canApprove={false}
                  onChange={(u) => setMine((cur) => (cur ? replace(cur, u) : cur))} />
              ))
            )}
          </section>

          {team.length > 0 && (
            <section className="mt-10">
              <h2 className="flex items-center gap-2 text-lg font-semibold">
                <Users className="h-5 w-5 text-violet" /> My team&apos;s reviews
              </h2>
              <p className="mt-1 text-sm text-fg/50">Write the review and a hike recommendation for each of your reports.</p>
              <div className="mt-4 space-y-5">
                {team.map((r) => (
                  <ReviewCard key={r.id} review={r} perspective="manager" canApprove={!!isAdmin}
                    onChange={(u) => setTeam((cur) => replace(cur, u))} />
                ))}
              </div>
            </section>
          )}
        </>
      )}
    </div>
  );
}
