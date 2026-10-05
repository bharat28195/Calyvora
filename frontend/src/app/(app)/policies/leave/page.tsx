"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { LeavePolicy } from "@/lib/types";
import { useSession } from "@/hooks/useSession";
import { canPublishDocuments } from "@/lib/document-access";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

const LABELS: Record<string, string> = {
  VACATION: "Vacation",
  SICK: "Sick leave",
  PERSONAL: "Personal leave",
  UNPAID: "Unpaid leave",
  COMP_OFF: "Comp-off",
};

/**
 * The company's leave policy, for everyone to read. Rendered from the same settings the leave screens
 * apply (People › Leave policy), so what an employee reads here is exactly what they get — there is no
 * second copy of the policy to fall out of date.
 */
export default function LeavePolicyReadPage() {
  const { me } = useSession();
  const [policies, setPolicies] = useState<LeavePolicy[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.leavePolicies()
      .then(setPolicies)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load the leave policy"));
  }, []);

  return (
    <div className="max-w-3xl">
      <Link href="/knowledge" className="inline-flex items-center gap-1.5 text-sm text-fg/50 hover:text-fg">
        <ArrowLeft className="h-4 w-4" /> Company documents
      </Link>
      <h1 className="mt-3 text-2xl font-semibold tracking-tight">Leave policy</h1>
      <p className="mt-1 text-fg/50">
        What you are entitled to at {me?.company.name ?? "your company"}, kept up to date by HR.
      </p>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {policies === null && !error ? (
        <Card className="mt-6"><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>
      ) : policies && (
        <div className="mt-6 grid gap-3">
          {policies.map((p) => (
            <Card key={p.type}>
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h2 className="font-medium">{LABELS[p.type] ?? p.type}</h2>
                <span className="text-xs text-fg/50">{p.paid ? "Paid" : "Unpaid"}</span>
              </div>
              <ul className="mt-2 grid gap-1 text-sm text-fg/70">
                {p.type === "COMP_OFF" ? (
                  <li>Earned by working a weekend or holiday; use it within {p.compOffExpiryDays} days.</li>
                ) : p.type === "UNPAID" ? (
                  <li>Not paid. Taken with your manager&apos;s approval.</li>
                ) : (
                  <>
                    <li>
                      <b className="text-fg">{p.daysPerYear} days</b> a year,{" "}
                      {p.accrual === "MONTHLY" ? "building up each month" : "credited at the start of the year"}.
                    </li>
                    <li>
                      {p.carryForwardCap > 0
                        ? `Up to ${p.carryForwardCap} unused days carry into next year.`
                        : "Unused days do not carry into next year."}
                    </li>
                  </>
                )}
              </ul>
            </Card>
          ))}
          <p className="text-sm text-fg/50">
            Apply in <Link href="/me/leave" className="text-violet hover:underline">Me › Time off</Link>. Your manager gets the request to approve.
            {canPublishDocuments(me?.user.role) && (
              <> HR can change these in <Link href="/people/leave-policy" className="text-violet hover:underline">People › Leave policy</Link>.</>
            )}
          </p>
        </div>
      )}
    </div>
  );
}
