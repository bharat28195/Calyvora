"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { useFeatures } from "@/hooks/useFeatures";

// Finance is a section, not a screen — but the sidebar's own label is a link, and it pointed at a
// route with no page behind it. Every other top-level entry (/me, /team, /performance, /people) has
// one; this was the single exception, so clicking "Finance" answered 404 while its two children
// worked perfectly.
//
// Pay rather than My finances, because payday is the reason an employee opens this product most
// months. Landing on a summary page would be one more click to the thing they came for. A company
// without payroll in Orbit has no pay screen, so it lands on My finances instead.
export default function FinanceIndexPage() {
  const router = useRouter();
  const features = useFeatures();
  useEffect(() => {
    if (features !== null) router.replace(features.PAYROLL === false ? "/finance/mine" : "/finance/pay");
  }, [features, router]);
  return (
    <div className="flex justify-center py-16">
      <Loader2 className="h-6 w-6 animate-spin text-violet" />
    </div>
  );
}
