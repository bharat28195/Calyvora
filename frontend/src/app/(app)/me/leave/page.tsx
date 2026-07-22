"use client";

import { MyLeave } from "@/components/leave/my-leave";

/** My time off — balance, request form and history, without the team approvals view. */
export default function MyLeavePage() {
  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">My time off</h1>
        <p className="mt-1 text-fg/50">Request leave and track your balance.</p>
      </div>
      <MyLeave />
    </div>
  );
}
