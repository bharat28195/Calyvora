"use client";

import { MyDay, MyMonth } from "@/components/attendance/self";
import { MyRegularizations } from "@/components/attendance/regularization";

/** My attendance — the same self-service pieces People shows, without the team sheet. */
export default function MyAttendancePage() {
  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">My attendance</h1>
        <p className="mt-1 text-fg/50">Clock in and out, and see how your month is tracking.</p>
      </div>
      <MyDay />
      <MyRegularizations />
      <MyMonth />
    </div>
  );
}
