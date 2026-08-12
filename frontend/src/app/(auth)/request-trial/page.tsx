"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { CheckCircle2, Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { trialRequestSchema, type TrialRequestFormInput } from "@/lib/validators";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

const TEAM_SIZES = ["1–10", "11–50", "51–200", "201–500", "500+"];

/**
 * Matches <Input> exactly — same height, border, fill and focus ring — because the select sits in a
 * two-column row beside the phone input and any difference reads as a mistake. The app's other
 * selects use this same string; it is repeated rather than shared only because this page is in the
 * public (auth) tree and imports nothing from the app shell.
 */
const SELECT_CLS =
  "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg " +
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

/**
 * "Start free trial" (PD-21). This page asks; it does not admit. Nothing submitted here creates a
 * company, a user or a password — the vendor is emailed, decides, and provisions the workspace, and
 * only then does a login exist. The old self-serve /register redirects here.
 */
function RequestTrialInner() {
  const params = useSearchParams();
  // Which page sent them, so the vendor can tell an Orbit enquiry from an HR-services one. Read from
  // the query string rather than guessed, and capped, because it lands verbatim in an email.
  const source = (params.get("from") || "app").slice(0, 80);

  const [values, setValues] = useState<TrialRequestFormInput>({
    companyName: "",
    contactName: "",
    email: "",
    phone: "",
    teamSize: "",
    note: "",
  });
  const [errors, setErrors] = useState<Partial<Record<keyof TrialRequestFormInput, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [emailSent, setEmailSent] = useState(false);

  const set = (key: keyof TrialRequestFormInput) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
      setValues((v) => ({ ...v, [key]: e.target.value }));

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    const parsed = trialRequestSchema.safeParse(values);
    if (!parsed.success) {
      const fieldErrors: Partial<Record<keyof TrialRequestFormInput, string>> = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof TrialRequestFormInput;
        fieldErrors[key] ??= issue.message;
      }
      setErrors(fieldErrors);
      return;
    }
    setErrors({});
    setSubmitting(true);
    try {
      const result = await api.requestTrial({ ...parsed.data, source });
      setEmailSent(result.emailSent);
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors as Partial<Record<keyof TrialRequestFormInput, string>>);
        if (Object.keys(err.fieldErrors).length === 0) setFormError(err.message);
      } else {
        setFormError("Something went wrong. Please try again, or email connect@calyvora.in.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <Card className="text-center">
        <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-400" />
        <CardTitle className="mt-4">Thanks — we&apos;ve got it</CardTitle>
        <CardDescription>
          Your request for <span className="text-fg">{values.companyName}</span> has reached us.
          Someone will come back to you at <span className="text-fg">{values.email}</span> to set
          your workspace up.
          {/* Only claim a confirmation email when one actually left the server. */}
          {emailSent
            ? " We've sent you a confirmation in the meantime."
            : " There's nothing else you need to do."}
        </CardDescription>
        <div className="mt-6">
          <Link href="/login" className="text-sm text-fg/60 hover:text-fg">
            Already have an account? Log in
          </Link>
        </div>
      </Card>
    );
  }

  return (
    <Card>
      <CardTitle>Request a free trial</CardTitle>
      <CardDescription>
        Tell us who you are and we&apos;ll set your workspace up. We do this by hand, so someone will
        come back to you — usually the same working day.
      </CardDescription>

      <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-4" noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}

        <Field label="Company name" htmlFor="companyName" error={errors.companyName}>
          <Input id="companyName" value={values.companyName} onChange={set("companyName")}
            aria-invalid={!!errors.companyName} autoComplete="organization" placeholder="Acme Inc." />
        </Field>

        <Field label="Your name" htmlFor="contactName" error={errors.contactName}>
          <Input id="contactName" value={values.contactName} onChange={set("contactName")}
            aria-invalid={!!errors.contactName} autoComplete="name" placeholder="Meera Nair" />
        </Field>

        <Field label="Work email" htmlFor="email" error={errors.email}>
          <Input id="email" type="email" value={values.email} onChange={set("email")}
            aria-invalid={!!errors.email} autoComplete="email" placeholder="you@acme.com" />
        </Field>

        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Phone" htmlFor="phone" hint="Optional" error={errors.phone}>
            <Input id="phone" type="tel" value={values.phone ?? ""} onChange={set("phone")}
              autoComplete="tel" placeholder="+91 98000 00000" />
          </Field>
          <Field label="Team size" htmlFor="teamSize" hint="Optional">
            <select id="teamSize" value={values.teamSize ?? ""} onChange={set("teamSize")}
              className={SELECT_CLS}>
              {/* Every option carries bg-surface. The open dropdown is drawn by the OS, not by the
                  page, so it does not inherit our dark background — only the text colour. Left
                  alone, that is light text on the platform's white popup: invisible until the
                  highlight passes over a row. This is the convention the rest of the app uses. */}
              <option value="" className="bg-surface">Choose…</option>
              {TEAM_SIZES.map((s) => (
                <option key={s} value={s} className="bg-surface">{s} people</option>
              ))}
            </select>
          </Field>
        </div>

        <Field label="Anything we should know?" htmlFor="note" hint="Optional" error={errors.note}>
          <textarea id="note" value={values.note ?? ""} onChange={set("note")} rows={3}
            placeholder="What you're hoping to replace, when you'd like to start…"
            className="w-full rounded-lg border border-fg/15 bg-fg/5 px-3 py-2 text-sm text-fg
              placeholder:text-fg/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet" />
        </Field>

        <Button type="submit" size="lg" disabled={submitting} className="mt-2">
          {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
          {submitting ? "Sending…" : "Request a trial"}
        </Button>

        <p className="text-center text-xs text-fg/50">
          Accounts are set up by us, so there&apos;s no password to choose yet — we&apos;ll send your
          sign-in details once your trial is ready.
        </p>
      </form>

      <p className="mt-6 text-center text-sm text-fg/60">
        Already have an account?{" "}
        <Link href="/login" className="text-violet hover:underline">
          Log in
        </Link>
      </p>
    </Card>
  );
}

export default function RequestTrialPage() {
  return (
    <Suspense fallback={<Card><CardTitle>Request a free trial</CardTitle></Card>}>
      <RequestTrialInner />
    </Suspense>
  );
}
