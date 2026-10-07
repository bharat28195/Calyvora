"use client";

import { useState } from "react";
import { CheckCircle2, Copy, MailWarning } from "lucide-react";
import type { CompanySummary } from "@/lib/types";

/**
 * How a newly created company's admin gets their sign-in details.
 *
 * <p>Normally Orbit has emailed them their email and a temporary password, which they replace at
 * first sign-in, and the console never sees the password. If the email could not be sent — or the
 * workspace waits for activation — the server hands the password back once, and it is shown here so
 * it can be passed on by hand.
 */
/** @param handOver the workspace was created by an agency: no email is sent by design, the agency passes it on */
export function WelcomeOutcome({ company, handOver = false }: { company: CompanySummary; handOver?: boolean }) {
  const [copied, setCopied] = useState(false);

  if (company.welcomeEmailSent) {
    return (
      <div className="flex items-start gap-2 rounded-lg border border-emerald-500/30 bg-emerald-500/5 p-3 text-sm">
        <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-500" />
        <p>
          <strong>{company.name}</strong> is ready. A welcome email with the sign-in details went to{" "}
          <strong>{company.adminEmail}</strong> — they&apos;ll choose their own password the first time they sign in.
        </p>
      </div>
    );
  }

  if (!company.temporaryPassword) return null;

  // The address this console is running on — demo.calyvora.in on demo, orbit.calyvora.in on prod.
  const signIn = typeof window === "undefined" ? "/login" : `${window.location.origin}/login`;
  const details = `Sign in: ${signIn}\nEmail: ${company.adminEmail}\nTemporary password: ${company.temporaryPassword}`;
  return (
    <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-sm">
      <p className="flex items-center gap-2 font-medium">
        <MailWarning className="h-4 w-4 text-amber-500" /> Share these sign-in details yourself
      </p>
      <p className="mt-1 text-fg/60">
        {handOver
          ? "Pass these to the company's admin once Calyvora activates the workspace. "
          : "The welcome email could not be sent. "}
        This is the only time the password is shown — send it privately. They will be asked to change it
        when they first sign in.
      </p>
      <pre className="mt-2 whitespace-pre-wrap rounded-md bg-fg/5 p-2 font-mono text-xs">{details}</pre>
      <button
        type="button"
        className="mt-2 inline-flex items-center gap-1 text-xs text-violet hover:underline"
        onClick={() => { void navigator.clipboard?.writeText(details); setCopied(true); }}
      >
        <Copy className="h-3.5 w-3.5" /> {copied ? "Copied" : "Copy details"}
      </button>
    </div>
  );
}
