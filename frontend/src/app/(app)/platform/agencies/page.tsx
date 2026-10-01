"use client";

import { AgenciesSection } from "@/components/platform/agencies";

/** Groups that run several companies (PD-18). They provision; the vendor alone activates billing. */
export default function PlatformAgenciesPage() {
  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">Agencies</h1>
      <p className="mt-1 text-fg/50">Customers who run several companies of their own.</p>

      <div className="mt-6">
        <AgenciesSection />
      </div>
    </div>
  );
}
