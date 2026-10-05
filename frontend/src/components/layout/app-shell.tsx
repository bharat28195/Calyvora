"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { SubscriptionGate } from "@/components/layout/subscription-gate";
import {
  Loader2, LogOut, LayoutDashboard, Users, UserCog, Settings, FileText,
  CircleUser, Inbox, Receipt, ClipboardCheck, BarChart3, Wallet, CreditCard, UserPlus,
  CalendarClock, Building2, LifeBuoy, DoorOpen, CalendarCheck, Network, Megaphone,
} from "lucide-react";
import { useRequireAuth } from "@/hooks/useSession";
import { IdleTimeout } from "@/components/layout/idle-timeout";
import { api } from "@/lib/api";
import { cn } from "@/lib/utils";
import type { Role } from "@/lib/types";
import { CommandBar } from "@/components/layout/command-bar";
import { AssistantPanel } from "@/components/layout/assistant-panel";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { NotificationBell } from "@/components/layout/notification-bell";
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
  /**
   * The module this section belongs to. Hidden when the company has not bought it.
   *
   * <p>Cosmetic on its own — the API refuses the same request regardless (FeatureGuardFilter), which
   * is what actually enforces a plan. This exists so a customer on a smaller plan is not shown a
   * section that answers 403 when they click it.
   */
  feature?: string;
  /**
   * Show this section only to somebody who has people reporting to them.
   *
   * Deliberately not a role. A senior engineer with two interns under them leads a team whatever
   * their title says, and a MANAGER with nobody under them leads none — so the reporting tree
   * decides, exactly as it does on the server (OrgScope). Cosmetic either way: the team API returns
   * an empty roster to a caller with no reports regardless of what the nav shows.
   */
  leadsTeam?: boolean;
  children?: NavChild[]; // sub-panes shown in the left pane when the section is active
  /** Sub-panes only some roles may open, merged into `children` for those roles. */
  hrChildren?: NavChild[];
}

// HR-only product surface (feature/hr-suite). Work, Knowledge, Clients and Feed are intentionally
// omitted here — this branch presents Orbit as a focused HR suite for the demo.
// Per-role visibility (PD-10). MEMBER/MANAGER get self-service; HR gets the people-ops surface;
// ADMIN gets everything company-level; OWNER is the platform vendor — only the Platform console.
const HR_PLUS: Role[] = ["ADMIN", "HR"];
const MANAGES: Role[] = ["ADMIN", "HR", "MANAGER"]; // people who approve their team's requests
const COMPANY: Role[] = ["ADMIN", "HR", "MANAGER", "MEMBER"]; // any company user (not the platform OWNER)
const NAV: NavItem[] = [
  // Four pages rather than four sections of one, so each gets the whole width — the companies table
  // is the widest thing in the app and was sharing its row with a second nav.
  {
    href: "/platform", label: "Platform", icon: Building2, roles: ["OWNER"],
    children: [
      { href: "/platform", label: "Companies" },
      { href: "/platform/requests", label: "Requests" },
      { href: "/platform/agencies", label: "Agencies" },
      { href: "/platform/pricing", label: "Pricing" },
      { href: "/platform/plans", label: "Plans & features" },
    ],
  },
  // An agency sees only this — no company surface at all, because it holds no employees of its own.
  { href: "/agency", label: "My companies", icon: Building2, roles: ["AGENCY_OWNER"] },
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard, roles: COMPANY },
  // The company feed: announcements, celebrations and questions, company-wide or to one team — the
  // place to post something everyone sees. Open to every company user; who can post company-wide
  // vs team-only is enforced on the server. Not feature-gated, so a new company has it from day one.
  { href: "/feed", label: "Announcements", icon: Megaphone, roles: COMPANY },
  { href: "/analytics", label: "Insights", icon: BarChart3, roles: HR_PLUS, feature: "ANALYTICS" },
  // "Me" is attendance and time off. Everything that is really about money went to Finance and
  // everything about other people went to My team — this section is what I did, not what I am owed
  // or who I work with.
  {
    href: "/me", label: "Me", icon: CircleUser, roles: COMPANY,
    children: [
      { href: "/me", label: "Overview" },
      { href: "/me/attendance", label: "Attendance" },
      { href: "/me/leave", label: "Time off" },
    ],
  },
  // Anyone who leads people, whatever their role is called. No payroll here, ever — see TeamService.
  //
  // A whole-company role always passes the leadsTeam gate (see the nav filter), so putting the org
  // chart here keeps it in reach of an admin who happens to lead nobody.
  {
    href: "/team", label: "My team", icon: Users, roles: COMPANY, leadsTeam: true,
    children: [
      { href: "/team", label: "Overview" },
      { href: "/team/attendance", label: "Attendance" },
      { href: "/team/leave", label: "Time off" },
      { href: "/team/expenses", label: "Expenses" },
      { href: "/team/performance", label: "Performance" },
    ],
  },
  // Top level, and it took three attempts to get here. Under People it was HR-only, so a lead could
  // not open it. Under Me it was reachable but nobody looks for the company's shape inside their own
  // profile. Under My team it disappeared for exactly the people who most need it — that section is
  // gated on having reports, and someone with none is precisely who needs to look up where they sit.
  //
  // Everyone has a place in the company, so this is everyone's. It opens on your own line with the
  // rest of the org one click away (see OrgTree), which is what makes it safe to show anybody.
  { href: "/people/org", label: "Org chart", icon: Network, roles: COMPANY },
  // Everyone has finances; only HR has payroll. Keeping the two apart is the point of the split —
  // "Finance" is what the company owes me, "Payroll" is what the company pays everybody.
  //
  // Claiming an expense is asking to be paid back, so it sits with the rest of the money rather than
  // beside my attendance.
  {
    href: "/finance", label: "Finance", icon: Wallet, roles: COMPANY,
    children: [
      { href: "/finance/pay", label: "My pay" },
      { href: "/finance/mine", label: "My finances" },
      { href: "/finance/expenses", label: "Expenses" },
      // Everyone declares their own tax and picks their own regime — which one is cheaper depends
      // on the individual, so this cannot be an HR-only screen.
      { href: "/finance/tax", label: "Tax declaration" },
    ],
    hrChildren: [
      { href: "/finance/tax/manage", label: "Manage tax" },
    ],
  },
  // One Performance section for everybody, with two extra panes for HR rather than a second
  // top-level entry — a member and an HR lead both mean "performance" by the same word.
  {
    href: "/performance", label: "Performance", icon: ClipboardCheck, roles: COMPANY,
    feature: "PERFORMANCE",
    children: [
      { href: "/performance/me", label: "My goals" },
      { href: "/performance/review", label: "My review" },
    ],
    hrChildren: [
      { href: "/performance", label: "Review cycles" },
    ],
  },
  { href: "/inbox", label: "Inbox", icon: Inbox, roles: COMPANY },
  { href: "/helpdesk", label: "Helpdesk", icon: LifeBuoy, roles: COMPANY, feature: "HELPDESK" },
  { href: "/regularizations", label: "Regularizations", icon: CalendarClock, roles: MANAGES },
  // Approvals, which is not the same thing as the team's leave calendar under My team — one is a
  // queue you have to act on, the other is a view. Shown to anyone who leads people rather than to
  // the MANAGER role, because the server now lets the whole chain above someone decide their
  // requests, and a lead who can approve but cannot find the queue is the same bug in a new place.
  // MANAGER/MEMBER rather than everyone, so HR and admins are not shown the same page twice — they
  // already reach it as People > Time off.
  { href: "/people/time-off", label: "Leave approvals", icon: CalendarCheck, leadsTeam: true, roles: ["MANAGER", "MEMBER"] },
  // Top-level rather than under People, which is HR-only: exit clearance is a lead's job, and they
  // would never see it nested under a section their role cannot open. HR and admins reach the same
  // page and get the whole company; a lead now gets only their own org (ExitService.leaving).
  { href: "/people/exits", label: "Exits", icon: DoorOpen, roles: COMPANY, leadsTeam: true },
  {
    href: "/people", label: "People", icon: Users, roles: HR_PLUS,
    children: [
      { href: "/people", label: "Directory" },
      { href: "/people/designations", label: "Designations" },
      { href: "/people/attendance", label: "Attendance" },
      { href: "/people/time-off", label: "Time off" },
      { href: "/people/leave-policy", label: "Leave policy" },
      { href: "/people/holidays", label: "Holidays" },
    ],
  },
  { href: "/recruitment", label: "Recruitment", icon: UserPlus, roles: HR_PLUS, feature: "RECRUITMENT" },
  { href: "/shifts", label: "Shifts", icon: CalendarClock, roles: HR_PLUS, feature: "SHIFTS" },
  // The HR-only Performance entry that used to sit here is gone: there is now one Performance section
  // for everybody (above), and HR gets the Review cycles pane inside it via hrChildren. Two nav
  // entries with the same label pointing at the same route is how a screen gets reported missing.
  {
    href: "/payroll", label: "Payroll", icon: Wallet, roles: HR_PLUS, feature: "PAYROLL",
    children: [
      { href: "/payroll", label: "Salaries" },
      { href: "/payroll/run", label: "Payroll run" },
      { href: "/payroll/template", label: "Payslip template" },
      // Always listed, whether or not the feature is switched on for this company: the screen itself
      // says which, so HR can check the rates before asking us to turn it on. Hiding it would leave
      // somebody told "PF starts next month" with nowhere to look.
      { href: "/payroll/statutory", label: "Statutory (PF)" },
    ],
  },
  // Admins and HR, matching ExpenseController (HR manages expenses since 2026-10-05). Approving their
  // own team's claims stays open to whoever leads them (My team > Expenses).
  { href: "/expenses", label: "Expenses", icon: Receipt, roles: HR_PLUS, feature: "EXPENSES" },
  {
    href: "/documents", label: "Documents", icon: FileText, roles: HR_PLUS,
    children: [
      { href: "/documents", label: "Issued" },
      { href: "/documents/new", label: "Generate" },
      { href: "/documents/templates", label: "Templates" },
      { href: "/documents/letterhead", label: "Letterpad" },
    ],
  },
  { href: "/members", label: "Members", icon: UserCog, roles: ["ADMIN"] },
  { href: "/subscription", label: "Subscription", icon: CreditCard, roles: ["ADMIN"] },
  { href: "/settings", label: "Settings", icon: Settings, roles: ["ADMIN"] },
];

/**
 * Pages that exist but have no menu entry on this branch, with the rule their API applies — without
 * these the guard has nothing to go on and a typed URL renders the page under a 403 again.
 */
const UNLISTED: { href: string; roles?: Role[]; feature?: string }[] = [
  { href: "/billing", roles: ["ADMIN"] },                       // BillingController: OWNER/ADMIN
  { href: "/knowledge", roles: COMPANY, feature: "KNOWLEDGE" },
];

/** Where each kind of account lands, and is sent back to from a page that is not theirs. */
function homeFor(role: Role): string {
  return role === "OWNER" ? "/platform" : role === "AGENCY_OWNER" ? "/agency" : "/dashboard";
}

/**
 * Who may open `pathname`, read off NAV so the menu and the guard cannot disagree.
 *
 * <p>Typing a URL used to skip the menu entirely: a company admin who opened /platform got the
 * vendor's console — heading, "New company" button, zeroed totals — under a 403 banner. Nothing
 * leaked (the API refuses every call), but it looked broken and invited the question "what is this?".
 *
 * <p>The most specific matching entry wins, and entries sharing that href pool their roles: the
 * leave-approvals queue is listed once for leads and once under People for HR, and both may open it.
 * A route NAV does not mention is allowed — the server is still the one that enforces.
 *
 * @return the roles and module for the route, or null when the route is open to anyone signed in
 */
function routeRule(pathname: string): { roles: Role[] | null; feature?: string } | null {
  const entries: { href: string; roles?: Role[]; feature?: string }[] = [...UNLISTED];
  for (const n of NAV) {
    entries.push({ href: n.href, roles: n.roles, feature: n.feature });
    for (const c of n.children ?? []) entries.push({ href: c.href, roles: n.roles, feature: n.feature });
    // hrChildren are only ever merged in for whole-company roles (see the nav filter below).
    const wholeCompany = (n.roles ?? COMPANY).filter((r) => r === "ADMIN" || r === "HR");
    for (const c of n.hrChildren ?? []) entries.push({ href: c.href, roles: wholeCompany, feature: n.feature });
  }
  const matches = entries.filter((e) => pathname === e.href || pathname.startsWith(e.href + "/"));
  if (matches.length === 0) return null;
  const longest = Math.max(...matches.map((e) => e.href.length));
  const best = matches.filter((e) => e.href.length === longest);
  const roles = best.some((e) => !e.roles) ? null : Array.from(new Set(best.flatMap((e) => e.roles ?? [])));
  return { roles, feature: best.find((e) => e.feature)?.feature };
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const session = useRequireAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [loggingOut, setLoggingOut] = useState(false);
  // Null until known. Nothing is hidden while it loads: showing a section briefly and then removing
  // it is far better than hiding one the customer has paid for because a request was slow.
  const [features, setFeatures] = useState<Record<string, boolean> | null>(null);
  // Whether this person has anybody reporting to them. False until known, the opposite of `features`
  // above: showing a section that then vanishes is the right trade for a paid module, but "My team"
  // flashing up for somebody who has no team reads as a bug, and one extra second before a lead sees
  // it costs nothing.
  const [leadsTeam, setLeadsTeam] = useState(false);

  // Wait for the session before asking anything of the server. This ran on mount for a long time,
  // and React runs child effects before parent ones, so it always fired before SessionProvider had
  // a token — not a race but an ordering, and both calls 401'd on every single page load. Both
  // failures were swallowed, which is why nothing ever looked broken. It cost more than it looked:
  // `features` stayed null, so the nav offered every gated module to every company, and `leadsTeam`
  // stayed false, so My team, Leave approvals and Exits were invisible to exactly the managers and
  // members PD-32 built them for. The 401s also woke the transport's refresh retry, which is how a
  // page load came to present the rotating refresh cookie twice and get itself signed out.
  const authenticated = session.status === "authenticated";
  useEffect(() => {
    if (!authenticated) return;
    api.companyFeatures()
      .then((list) => setFeatures(Object.fromEntries(list.map((f) => [f.feature, f.enabled]))))
      .catch(() => setFeatures(null));
    api.myTeamStanding()
      .then((s) => setLeadsTeam(s.leadsTeam))
      .catch(() => setLeadsTeam(false));
  }, [authenticated]);

  // The platform OWNER (vendor) has no company app — send them to the Platform console. Beyond that,
  // a page whose NAV entry is not for this role — or belongs to a module this company has not bought
  // — sends its visitor home rather than rendering a console that can only answer 403.
  const role = session.me?.user.role;
  const rule = routeRule(pathname);
  const blocked = !!role && (
    (role === "OWNER" && !pathname.startsWith("/platform")) ||
    (rule?.roles != null && !rule.roles.includes(role)) ||
    // Only once features are known, and only an explicit "off": never hide a paid module on a slow load.
    (!!rule?.feature && features !== null && features[rule.feature] === false));
  useEffect(() => {
    if (blocked && role) router.replace(homeFor(role));
  }, [blocked, role, router]);

  if (session.status !== "authenticated" || !session.me || blocked) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-violet" />
      </div>
    );
  }

  const { user, company } = session.me;
  const seesWholeCompany = user.role === "ADMIN" || user.role === "HR" || user.role === "OWNER";
  const nav = NAV
    .filter((n) => !n.roles || n.roles.includes(user.role))
    .filter((n) => !n.feature || features === null || features[n.feature] !== false)
    // A whole-company role is never gated by the team check: HR who happens to have nobody reporting
    // to them still runs exits for the business.
    .filter((n) => !n.leadsTeam || leadsTeam || seesWholeCompany)
    // hrChildren are appended, not substituted: HR still has their own goals and their own review,
    // and a section that swapped one set for the other would take those away from them.
    .map((n) => (n.hrChildren && seesWholeCompany
      ? { ...n, children: [...(n.children ?? []), ...n.hrChildren] }
      : n));
  const isActive = (href: string) => pathname === href || pathname.startsWith(href + "/");

  async function logout() {
    setLoggingOut(true);
    await session.logout();
    window.location.assign("/login");
  }

  return (
    <>
    <IdleTimeout />
    {user.role !== "OWNER" && <SubscriptionGate />}
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
          <NotificationBell />
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
    </>
  );
}
