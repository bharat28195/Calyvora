"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { Loader2, LogOut } from "lucide-react";
import { useRequireAuth } from "@/hooks/useSession";
import { cn } from "@/lib/utils";
import type { Role } from "@/lib/types";
import { CommandBar } from "@/components/layout/command-bar";
import { AssistantPanel } from "@/components/layout/assistant-panel";

interface NavItem {
  href: string;
  label: string;
  roles?: Role[]; // undefined = everyone
}

const NAV: NavItem[] = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/people", label: "People" },
  { href: "/work", label: "Work" },
  { href: "/knowledge", label: "Knowledge" },
  { href: "/members", label: "Members", roles: ["OWNER", "ADMIN"] },
  { href: "/settings", label: "Settings", roles: ["OWNER", "ADMIN"] },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const session = useRequireAuth();
  const pathname = usePathname();
  const [loggingOut, setLoggingOut] = useState(false);

  if (session.status !== "authenticated" || !session.me) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-violet" />
      </div>
    );
  }

  const { user, company } = session.me;
  const nav = NAV.filter((n) => !n.roles || n.roles.includes(user.role));

  return (
    <div className="min-h-screen">
      <header className="border-b border-white/10 bg-ink/60 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
          <div className="flex items-center gap-8">
            <Link href="/dashboard" className="font-semibold tracking-tight">
              Calyvora
            </Link>
            <nav className="flex items-center gap-1">
              {nav.map((item) => {
                const active = pathname === item.href;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={cn(
                      "rounded-md px-3 py-1.5 text-sm transition-colors",
                      active ? "bg-white/10 text-white" : "text-white/60 hover:text-white",
                    )}
                  >
                    {item.label}
                  </Link>
                );
              })}
            </nav>
          </div>

          <div className="flex items-center gap-4">
            <CommandBar />
            <div className="hidden text-right sm:block">
              <p className="text-sm text-white">
                {user.firstName} {user.lastName}
              </p>
              <p className="text-xs text-white/40">
                {company.name} · {user.role}
              </p>
            </div>
            <button
              onClick={async () => {
                setLoggingOut(true);
                await session.logout();
                window.location.assign("/login");
              }}
              disabled={loggingOut}
              className="inline-flex h-9 items-center gap-1.5 rounded-md px-3 text-sm text-white/60 hover:bg-white/5 hover:text-white disabled:opacity-50"
              aria-label="Log out"
            >
              {loggingOut ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogOut className="h-4 w-4" />}
              <span className="hidden sm:inline">Log out</span>
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-6 py-10">{children}</main>
      <AssistantPanel />
    </div>
  );
}
