"use client";

import { SeatRequestsSection } from "@/components/platform/seat-requests";
import { TrialRequestsSection } from "@/components/platform/trial-requests";

/** Everything waiting on a decision from the vendor: trial enquiries and seat increases. */
export default function PlatformRequestsPage() {
  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">Requests</h1>
      <p className="mt-1 text-fg/50">Waiting on a decision from you. Nothing here happens on its own.</p>

      <div className="mt-6 flex flex-col gap-4">
        <TrialRequestsSection onChanged={() => {}} />
        <SeatRequestsSection />
      </div>
    </div>
  );
}
