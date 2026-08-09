"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AlertTriangle, CheckCircle2, Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { registerSchema, passwordStrength, type RegisterInput } from "@/lib/validators";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

const STRENGTH_LABELS = ["Too weak", "Weak", "Fair", "Good", "Strong"];

export default function RegisterPage() {
  const router = useRouter();
  // Whether to offer the mail-capture page is a fact about the backend, not about how the frontend
  // was built — a staging deploy is a production build talking to a non-prod API, so asking is the
  // only way to get it right in both directions. Pointing a real customer at a page that doesn't
  // exist is as bad as hiding it from someone who needs it.
  const [showDevMailbox, setShowDevMailbox] = useState(false);
  useEffect(() => {
    void api.devMailboxAvailable().then(setShowDevMailbox);
  }, []);
  const [values, setValues] = useState<RegisterInput>({
    companyName: "",
    firstName: "",
    lastName: "",
    email: "",
    password: "",
  });
  const [errors, setErrors] = useState<Partial<Record<keyof RegisterInput, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [emailSent, setEmailSent] = useState(true);
  const [resent, setResent] = useState(false);

  const set = (key: keyof RegisterInput) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setValues((v) => ({ ...v, [key]: e.target.value }));

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    const parsed = registerSchema.safeParse(values);
    if (!parsed.success) {
      const fieldErrors: Partial<Record<keyof RegisterInput, string>> = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof RegisterInput;
        fieldErrors[key] ??= issue.message;
      }
      setErrors(fieldErrors);
      return;
    }
    setErrors({});
    setSubmitting(true);
    try {
      const result = await api.register(parsed.data);
      // Creating a workspace signs you straight into it — you've just set up a company and need to
      // start adding people. If the backend still requires verification it refuses the login, and
      // we fall back to the "check your email" screen rather than showing an error.
      try {
        await api.login(parsed.data.email, parsed.data.password);
        router.replace("/dashboard");
        return;
      } catch {
        setEmailSent(result.emailSent);
        setDone(true);
      }
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors as Partial<Record<keyof RegisterInput, string>>);
        if (Object.keys(err.fieldErrors).length === 0) setFormError(err.message);
        else if (err.fieldErrors.email) setFormError(null);
      } else {
        setFormError("Something went wrong. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    // The workspace exists either way. Say which of the two situations the user is actually in —
    // claiming "check your email" when the send failed leaves them waiting for nothing.
    return (
      <Card className="text-center">
        {emailSent ? (
          <>
            <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-400" />
            <CardTitle className="mt-4">Check your email</CardTitle>
            <CardDescription>
              We sent a verification link to <span className="text-fg">{values.email}</span>. Click
              it to activate your account, then log in.
            </CardDescription>
          </>
        ) : (
          <>
            <AlertTriangle className="mx-auto h-10 w-10 text-amber-400" />
            <CardTitle className="mt-4">Workspace created — but we couldn&apos;t send the email</CardTitle>
            <CardDescription>
              Your account for <span className="text-fg">{values.email}</span> exists. The
              verification email didn&apos;t go out, so it can&apos;t be activated yet. Try again, or
              ask your administrator to check the mail settings.
            </CardDescription>
          </>
        )}
        <div className="mt-6 flex flex-col gap-2">
          {!emailSent && (
            <Button
              type="button"
              variant="secondary"
              onClick={async () => {
                try {
                  await api.resendVerification(values.email);
                  setResent(true);
                } catch {
                  setResent(false);
                }
              }}
            >
              {resent ? "Verification email requested" : "Resend verification email"}
            </Button>
          )}
          {showDevMailbox && (
            <Alert tone="info">
              This environment captures mail instead of sending it — open the{" "}
              <Link href="/dev/mailbox" className="underline">
                mailbox
              </Link>{" "}
              to click your verification link.
            </Alert>
          )}
          <Link href="/login" className="text-sm text-fg/60 hover:text-fg">
            Back to log in
          </Link>
        </div>
      </Card>
    );
  }

  const strength = passwordStrength(values.password);

  return (
    <Card>
      <CardTitle>Create your company workspace</CardTitle>
      <CardDescription>
        You&apos;ll be the admin, and you&apos;ll go straight in. Takes about a minute.
      </CardDescription>

      <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-4" noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}

        <Field label="Company name" htmlFor="companyName" error={errors.companyName}>
          <Input id="companyName" value={values.companyName} onChange={set("companyName")}
            aria-invalid={!!errors.companyName} autoComplete="organization" placeholder="Acme Inc." />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="First name" htmlFor="firstName" error={errors.firstName}>
            <Input id="firstName" value={values.firstName} onChange={set("firstName")}
              aria-invalid={!!errors.firstName} autoComplete="given-name" />
          </Field>
          <Field label="Last name" htmlFor="lastName" error={errors.lastName}>
            <Input id="lastName" value={values.lastName} onChange={set("lastName")}
              aria-invalid={!!errors.lastName} autoComplete="family-name" />
          </Field>
        </div>

        <Field label="Work email" htmlFor="email" error={errors.email}>
          <Input id="email" type="email" value={values.email} onChange={set("email")}
            aria-invalid={!!errors.email} autoComplete="email" placeholder="you@acme.com" />
        </Field>

        <Field label="Password" htmlFor="password" error={errors.password}
          hint="At least 10 characters, with a letter and a number.">
          <Input id="password" type="password" value={values.password} onChange={set("password")}
            aria-invalid={!!errors.password} autoComplete="new-password" />
        </Field>

        {values.password.length > 0 && (
          <div className="flex items-center gap-2" aria-hidden>
            <div className="flex h-1.5 flex-1 gap-1">
              {[0, 1, 2, 3].map((i) => (
                <div key={i}
                  className={`flex-1 rounded-full ${i < strength ? "bg-violet" : "bg-fg/10"}`} />
              ))}
            </div>
            <span className="w-16 text-right text-xs text-fg/50">{STRENGTH_LABELS[strength]}</span>
          </div>
        )}

        <Button type="submit" size="lg" disabled={submitting} className="mt-2">
          {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
          {submitting ? "Creating…" : "Create workspace"}
        </Button>
      </form>

      <p className="mt-5 text-center text-sm text-fg/50">
        Already have an account?{" "}
        <Link href="/login" className="text-fg hover:underline">
          Log in
        </Link>
      </p>
    </Card>
  );
}
