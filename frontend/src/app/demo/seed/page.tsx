"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { CheckCircle2, Loader2, AlertTriangle, Copy, Check } from "lucide-react";
import { api, ApiError } from "@/lib/api";

/**
 * Prepare a demo by visiting a URL: {@code app.calyvora.in/demo/seed}.
 *
 * <p>This replaced the "Explore the live demo" button that used to sit on the login screen. That
 * button was a sales affordance on the door real customers sign in through — one click and an
 * anonymous visitor was inside a populated company. Preparing a demo is something the person
 * running the demo does, on purpose, before anyone is watching.
 *
 * <p>Deliberately outside the (auth) and (app) route groups: it needs no session, and it should not
 * inherit the sign-in chrome. It seeds on arrival and then shows every login, because the thing you
 * actually need three seconds before a demo is the credentials, not a confirmation message.
 */

type Login = { label: string; email: string; password: string; note: string };

/** Mirrors DemoSeedService — the demo password is the same for every seeded account. */
const DEMO_PASSWORD = "demopass123";

const LOGINS: Login[] = [
  { label: "Platform owner (you)", email: "bharat28195@calyvora.in", password: "Bharat@28195#",
    note: "Every company on the platform, subscriptions, pricing, trial requests" },
  { label: "Company admin", email: "ava.chen@northwind.demo", password: DEMO_PASSWORD,
    note: "Northwind Robotics — the full HR product" },
  { label: "HR", email: "leo.martins@northwind.demo", password: DEMO_PASSWORD,
    note: "People, hiring, payroll, documents" },
  { label: "Manager", email: "tom.becker@northwind.demo", password: DEMO_PASSWORD,
    note: "Approvals, their team, exits" },
  { label: "Employee", email: "priya.nair@northwind.demo", password: DEMO_PASSWORD,
    note: "What a normal member of staff sees" },
  { label: "Agency owner", email: "owner@vertexgroup.demo", password: DEMO_PASSWORD,
    note: "A group running several companies at once" },
];

export default function DemoSeedPage() {
  const [state, setState] = useState<"seeding" | "done" | "error">("seeding");
  const [message, setMessage] = useState("");
  const [companies, setCompanies] = useState<string[]>([]);

  useEffect(() => {
    // One call does both halves: the Northwind company and the sample companies that fill the
    // owner console. Seeding is idempotent, so reloading this page is always safe.
    api.seedAll()
      .then((data) => {
        setCompanies(data.companies);
        setState("done");
      })
      .catch((e: unknown) => {
        setMessage(e instanceof ApiError ? e.message : "The backend didn't answer.");
        setState("error");
      });
  }, []);

  return (
    <div className="mx-auto flex min-h-screen max-w-3xl flex-col justify-center px-6 py-12">
      {state === "seeding" && (
        <div className="flex items-center gap-3 text-fg/60">
          <Loader2 className="h-5 w-5 animate-spin text-violet" />
          Building the demo — this takes a few seconds…
        </div>
      )}

      {state === "error" && (
        <div className="rounded-xl border border-red-500/30 bg-red-500/5 p-5">
          <div className="flex items-center gap-2 font-medium text-red-400">
            <AlertTriangle className="h-5 w-5" /> Couldn&apos;t build the demo
          </div>
          <p className="mt-2 text-sm text-fg/60">{message}</p>
          {/* The overwhelmingly likely cause, said plainly rather than left to be guessed. */}
          <p className="mt-3 text-sm text-fg/50">
            Seeding is switched off when the backend runs under the <code>prod</code> profile. That is
            deliberate — it must never be reachable on a deployment holding real customer data.
          </p>
        </div>
      )}

      {state === "done" && (
        <>
          <div className="flex items-center gap-2 text-emerald-400">
            <CheckCircle2 className="h-6 w-6" />
            <h1 className="text-2xl font-semibold tracking-tight text-fg">Demo is ready</h1>
          </div>
          <p className="mt-2 text-fg/50">
            Northwind Robotics is populated — people, attendance, leave, payroll, hiring, helpdesk and
            documents{companies.length > 0 && `, plus ${companies.length} sample companies in the owner console`}.
            Sign in as whoever you want to show.
          </p>

          <div className="mt-8 flex flex-col gap-2">
            {LOGINS.map((l) => (
              <LoginRow key={l.email} login={l} />
            ))}
          </div>

          <Link href="/login"
            className="mt-8 inline-flex w-fit items-center gap-2 rounded-lg bg-violet px-4 py-2.5 text-sm font-medium text-white hover:bg-violet/90">
            Go to sign in
          </Link>

          <p className="mt-6 text-xs text-fg/30">
            Safe to reload — seeding only fills gaps and never overwrites anything that already exists.
          </p>
        </>
      )}
    </div>
  );
}

function LoginRow({ login }: { login: Login }) {
  const [copied, setCopied] = useState(false);

  // Copies both halves at once. Reading a password off a screen and typing it while someone waits
  // is exactly where a demo stumbles.
  async function copy() {
    await navigator.clipboard.writeText(`${login.email}\n${login.password}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-fg/10 bg-fg/[0.02] px-4 py-3">
      <div className="min-w-0">
        <p className="text-sm font-medium">{login.label}</p>
        <p className="truncate font-mono text-xs text-fg/60">{login.email}</p>
        <p className="mt-0.5 text-xs text-fg/35">{login.note}</p>
      </div>
      <div className="flex items-center gap-3">
        <code className="rounded-md bg-fg/5 px-2 py-1 text-xs text-fg/70">{login.password}</code>
        <button onClick={copy} aria-label={`Copy ${login.label} login`}
          className="rounded-md border border-fg/10 p-1.5 text-fg/40 hover:border-fg/25 hover:text-fg">
          {copied ? <Check className="h-4 w-4 text-emerald-400" /> : <Copy className="h-4 w-4" />}
        </button>
      </div>
    </div>
  );
}
