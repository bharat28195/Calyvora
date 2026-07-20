"use client";

import { SessionProvider } from "@/hooks/useSession";
import { AppShell } from "@/components/layout/app-shell";

export default function AppGroupLayout({ children }: { children: React.ReactNode }) {
  return (
    <SessionProvider>
      <AppShell>{children}</AppShell>
    </SessionProvider>
  );
}
