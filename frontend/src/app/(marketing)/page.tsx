import Link from "next/link";
import { LinkButton } from "@/components/ui/button";
import { ArrowRight, Boxes, Brain, Shield, Users, Workflow, BookOpen } from "lucide-react";

const apps = [
  { icon: Users, name: "People OS", desc: "HRIS, org directory, onboarding, and the identity graph every app builds on." },
  { icon: Workflow, name: "Work OS", desc: "Projects, tasks, and delivery — natively linked to people, docs, and customers." },
  { icon: BookOpen, name: "Knowledge OS", desc: "A real system of record for docs and institutional memory, not just another wiki." },
];

const pillars = [
  { icon: Boxes, title: "One platform", body: "Every app shares one identity, one data fabric, and one permission model — integrated by construction, not by brittle connectors." },
  { icon: Brain, title: "AI-native core", body: "A governed AI layer with a complete, permissioned view of your business — woven in from line one, not bolted on." },
  { icon: Shield, title: "Zero-Trust security", body: "OIDC/SSO, RBAC+ABAC, per-tenant encryption, and immutable audit of every human and agent action." },
];

export default function LandingPage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-6xl flex-col px-6">
      {/* Nav */}
      <header className="flex items-center justify-between py-6">
        <Link href="/" className="text-lg font-semibold tracking-tight">
          Calyvora
        </Link>
        <nav className="flex items-center gap-2">
          <LinkButton href="/login" variant="ghost" size="sm">
            Log in
          </LinkButton>
          <LinkButton href="/register" variant="primary" size="sm">
            Get started
          </LinkButton>
        </nav>
      </header>

      {/* Hero */}
      <section className="flex flex-col items-center gap-6 py-20 text-center sm:py-28">
        <span className="rounded-full border border-white/15 bg-white/5 px-4 py-1.5 text-xs font-medium text-white/70">
          The AI-Native Enterprise Operating System
        </span>
        <h1 className="max-w-3xl text-4xl font-bold leading-tight tracking-tight sm:text-6xl">
          Run your entire company on <span className="text-gradient">one platform</span>.
        </h1>
        <p className="max-w-2xl text-lg text-white/70">
          Replace the 20–40 disconnected tools you run for HR, work, and knowledge with a single
          system — one identity, one data fabric, one AI layer. Each app works independently, yet
          integrates natively.
        </p>
        <div className="mt-2 flex flex-wrap items-center justify-center gap-3">
          <LinkButton href="/register" size="lg">
            Get started <ArrowRight className="h-4 w-4" />
          </LinkButton>
          <LinkButton href="/login" variant="secondary" size="lg">
            Log in
          </LinkButton>
        </div>
      </section>

      {/* Pillars */}
      <section className="grid gap-6 py-12 sm:grid-cols-3">
        {pillars.map((p) => (
          <div key={p.title} className="rounded-2xl border border-white/10 bg-white/[0.03] p-6">
            <p.icon className="h-6 w-6 text-aqua" />
            <h3 className="mt-4 text-lg font-semibold">{p.title}</h3>
            <p className="mt-2 text-sm leading-relaxed text-white/60">{p.body}</p>
          </div>
        ))}
      </section>

      {/* Apps */}
      <section className="py-16">
        <h2 className="text-center text-2xl font-semibold tracking-tight sm:text-3xl">
          Phase 1 ships three connected apps
        </h2>
        <p className="mx-auto mt-3 max-w-xl text-center text-white/60">
          Depth-first, not breadth-first. Each is excellent on its own — and multiplies when combined.
        </p>
        <div className="mt-10 grid gap-6 sm:grid-cols-3">
          {apps.map((a) => (
            <div key={a.name} className="rounded-2xl border border-white/10 bg-white/[0.03] p-6">
              <a.icon className="h-6 w-6 text-violet" />
              <h3 className="mt-4 text-lg font-semibold">{a.name}</h3>
              <p className="mt-2 text-sm leading-relaxed text-white/60">{a.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* CTA */}
      <section className="my-12 rounded-3xl border border-white/10 bg-gradient-to-br from-violet/20 to-aqua/10 p-10 text-center sm:p-16">
        <h2 className="text-2xl font-semibold tracking-tight sm:text-3xl">
          Create your company workspace
        </h2>
        <p className="mx-auto mt-3 max-w-lg text-white/70">
          Register in under a minute, verify your email, and invite your team.
        </p>
        <div className="mt-8">
          <LinkButton href="/register" size="lg">
            Get started <ArrowRight className="h-4 w-4" />
          </LinkButton>
        </div>
      </section>

      {/* Footer */}
      <footer className="mt-auto flex flex-col items-center justify-between gap-2 border-t border-white/10 py-8 text-sm text-white/40 sm:flex-row">
        <span>© {new Date().getFullYear()} Calyvora</span>
        <span>One platform · one identity · one AI layer</span>
      </footer>
    </main>
  );
}
