"use client";

import { useEffect, useState } from "react";
import { Loader2, Star } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Employee } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { EmployeeGoals } from "@/components/people/employee-goals";

/**
 * My performance: the rating my manager set, and my goals — which I can update myself
 * (the goals API already allows the owner of a goal to edit it).
 */
export default function MyPerformancePage() {
  const [me, setMe] = useState<Employee | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.myEmployee()
      .then(setMe)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load your profile"));
  }, []);

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">My performance</h1>
        <p className="mt-1 text-fg/50">Your goals and how you&apos;re rated. Update progress as you go.</p>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {me === null ? (
        <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <>
          <Card className="mt-8">
            <CardTitle>Rating</CardTitle>
            {me.rating ? (
              <div className="mt-2 flex items-center gap-1">
                {[1, 2, 3, 4, 5].map((n) => (
                  <Star key={n}
                    className={`h-5 w-5 ${n <= me.rating! ? "fill-amber-400 text-amber-400" : "text-fg/20"}`} />
                ))}
                <span className="ml-2 text-sm text-fg/50">{me.rating} of 5</span>
              </div>
            ) : (
              <p className="mt-2 text-sm text-fg/40">No rating yet — your manager sets this during a review.</p>
            )}
            {me.skills.length > 0 && (
              <div className="mt-4">
                <p className="text-sm text-fg/50">Skills</p>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {me.skills.map((s) => (
                    <span key={s} className="rounded-full bg-fg/10 px-2 py-0.5 text-xs text-fg/70">{s}</span>
                  ))}
                </div>
              </div>
            )}
          </Card>

          <EmployeeGoals employeeId={me.id} canEdit />
        </>
      )}
    </div>
  );
}
