"use client";

import { PricingEditor } from "@/components/platform/pricing-editor";

/** The published price list every company on standard terms is billed against. */
export default function PlatformPricingPage() {
  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">Pricing</h1>
      <p className="mt-1 text-fg/50">
        What every company on the standard list pays. Companies on an agreed price are unaffected.
      </p>

      <div className="mt-6">
        <PricingEditor />
      </div>
    </div>
  );
}
