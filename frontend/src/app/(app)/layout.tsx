"use client";

import { SessionProvider } from "@/hooks/useSession";
import { AppShell } from "@/components/layout/app-shell";
import { AppLock } from "@/components/layout/app-lock";

export default function AppGroupLayout({ children }: { children: React.ReactNode }) {
  return (
    <SessionProvider>
      <AppShell>{children}</AppShell>
      {/* Fingerprint / face unlock in the phone app (PD-81); nothing in a browser. */}
      <AppLock />
    </SessionProvider>
  );
}
