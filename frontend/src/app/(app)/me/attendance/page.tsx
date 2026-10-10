"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { MyDay, MyMonth } from "@/components/attendance/self";
import { MyRegularizations } from "@/components/attendance/regularization";

/** My attendance — the same self-service pieces People shows, without the team sheet. */
export default function MyAttendancePage() {
  return <Suspense fallback={null}><MyAttendance /></Suspense>;
}

function MyAttendance() {
  // ?fix=2026-10-09 — from the "you didn't check out" email: open the correction for that day.
  const fix = useSearchParams().get("fix");
  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">My attendance</h1>
        <p className="mt-1 text-fg/50">Clock in and out, and see how your month is tracking.</p>
      </div>
      <MyDay />
      <MyRegularizations fixDate={fix && /^\d{4}-\d{2}-\d{2}$/.test(fix) ? fix : null} />
      <MyMonth />
    </div>
  );
}
