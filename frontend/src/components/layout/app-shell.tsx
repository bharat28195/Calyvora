"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import {
  Loader2, LogOut, LayoutDashboard, Users, FolderKanban, BookOpen, UserCog, Settings, Handshake, FileText,
} from "lucide-react";
import { useRequireAuth } from "@/hooks/useSession";
import { cn } from "@/lib/utils";
import type { Role } from "@/lib/types";
import { CommandBar } from "@/components/layout/command-bar";
import { AssistantPanel } from "@/components/layout/assistant-panel";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { Wordmark } from "@/components/layout/wordmark";

interface NavChild {
  href: string;
  label: string;
}
interface NavItem {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  roles?: Role[]; // undefined = everyone
  children?: NavChild[]; // sub-panes shown in the left pane when the section is active
}

const NAV: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  {
    href: "/people", label: "People", icon: Users,
    children: [
      { href: "/people", label: "Directory" },
      { href: "/people/org", label: "Org chart" },
      { href: "/people/time-off", label: "Time off" },
    ],
  },
  { href: "/work", label: "Work", icon: FolderKanban },
  { href: "/knowledge", label: "Knowledge", icon: BookOpen },
  { href: "/clients", label: "Clients", icon: Handshake },
  {
    href: "/documents", label: "Documents", icon: FileText, roles: ["OWNER", "ADMIN"],
    children: [
      { href: "/documents", label: "Issued" },
      { href: "/documents/new", label: "Generate" },
      { href: "/documents/templates", label: "Templates" },
    ],
  },
  { href: "/members", label: "Members", icon: UserCog, roles: ["OWNER", "ADMIN"] },
  { href: "/settings", label: "Settings", icon: Settings, roles: ["OWNER", "ADMIN"] },
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
  const isActive = (href: string) => pathname === href || pathname.startsWith(href + "/");

  async function logout() {
    setLoggingOut(true);
    await session.logout();
    window.location.assign("/login");
  }

  return (
    <div className="min-h-screen md:flex">
      {/* Left sidebar (md+) */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r border-fg/10 bg-surface/70 backdrop-blur md:flex">
        <div className="flex h-16 items-center px-5">
          <Link href="/dashboard"><Wordmark /></Link>
        </div>
        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-2">
          {nav.map((item) => {
            const Icon = item.icon;
            const section = isActive(item.href);
            const hasChildren = !!item.children?.length;
            return (
              <div key={item.href}>
                <Link
                  href={item.href}
                  className={cn(
                    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors",
                    // With children, the parent is a section header (violet text, no pill) — the active
                    // sub-pane carries the pill. Without children it's a normal highlighted item.
                    section
                      ? hasChildren ? "font-medium text-violet" : "bg-violet/10 font-medium text-violet"
                      : "text-fg/60 hover:bg-fg/5 hover:text-fg",
                  )}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  {item.label}
                </Link>
                {hasChildren && section && (
                  <div className="mt-1 space-y-0.5 border-l border-fg/10 pb-1 pl-3 ml-4">
                    {item.children!.map((c) => {
                      const active = pathname === c.href;
                      return (
                        <Link
                          key={c.href}
                          href={c.href}
                          className={cn(
                            "block rounded-md px-3 py-1.5 text-sm transition-colors",
                            active ? "bg-violet/10 font-medium text-violet" : "text-fg/50 hover:bg-fg/5 hover:text-fg",
                          )}
                        >
                          {c.label}
                        </Link>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </nav>
        <div className="border-t border-fg/10 p-3">
          <div className="mb-2 px-2">
            <p className="truncate text-sm font-medium">{user.firstName} {user.lastName}</p>
            <p className="truncate text-xs text-fg/40">{company.name} · {user.role}</p>
          </div>
          <button
            onClick={logout}
            disabled={loggingOut}
            className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-fg/60 hover:bg-fg/5 hover:text-fg disabled:opacity-50"
          >
            {loggingOut ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogOut className="h-4 w-4" />}
            Log out
          </button>
        </div>
      </aside>

      {/* Main column */}
      <div className="min-w-0 flex-1 md:pl-60">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-fg/10 bg-surface/70 px-5 backdrop-blur">
          <Link href="/dashboard" className="md:hidden"><Wordmark /></Link>
          <div className="flex-1" />
          <CommandBar />
          <ThemeToggle />
          <button
            onClick={logout}
            disabled={loggingOut}
            className="inline-flex h-9 w-9 items-center justify-center rounded-md text-fg/60 hover:bg-fg/5 hover:text-fg disabled:opacity-50 md:hidden"
            aria-label="Log out"
          >
            {loggingOut ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogOut className="h-4 w-4" />}
          </button>
        </header>

        {/* Mobile nav — horizontal scroll; sub-panes appear after the active section */}
        <nav className="flex gap-1 overflow-x-auto border-b border-fg/10 px-4 py-2 md:hidden">
          {nav.map((item) => {
            const Icon = item.icon;
            const section = isActive(item.href);
            return (
              <span key={item.href} className="inline-flex shrink-0 gap-1">
                <Link
                  href={item.href}
                  className={cn(
                    "inline-flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm",
                    section ? "bg-violet/10 text-violet" : "text-fg/60",
                  )}
                >
                  <Icon className="h-4 w-4" /> {item.label}
                </Link>
                {item.children?.length && section &&
                  item.children.filter((c) => c.href !== item.href).map((c) => (
                    <Link key={c.href} href={c.href}
                      className={cn("inline-flex shrink-0 items-center rounded-lg px-3 py-1.5 text-sm",
                        pathname === c.href ? "bg-violet/10 text-violet" : "text-fg/50")}>
                      {c.label}
                    </Link>
                  ))}
              </span>
            );
          })}
        </nav>

        <main className="mx-auto max-w-6xl px-6 py-10">{children}</main>
      </div>

      <AssistantPanel />
    </div>
  );
}
