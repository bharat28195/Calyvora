"use client";

import { useState } from "react";
import Link from "next/link";
import { CheckCircle2, Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { registerSchema, passwordStrength, type RegisterInput } from "@/lib/validators";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

const STRENGTH_LABELS = ["Too weak", "Weak", "Fair", "Good", "Strong"];

export default function RegisterPage() {
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
      await api.register(parsed.data);
      setDone(true);
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
    return (
      <Card className="text-center">
        <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-400" />
        <CardTitle className="mt-4">Check your email</CardTitle>
        <CardDescription>
          We sent a verification link to <span className="text-white">{values.email}</span>. Click it
          to activate your account, then log in.
        </CardDescription>
        <div className="mt-6 flex flex-col gap-2">
          <Alert tone="info">
            Local dev uses a mock mailbox — open{" "}
            <Link href="/dev/mailbox" className="underline">
              /dev/mailbox
            </Link>{" "}
            to click your verification link.
          </Alert>
          <Link href="/login" className="text-sm text-white/60 hover:text-white">
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
      <CardDescription>You&apos;ll be the Owner. Takes about a minute.</CardDescription>

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
                  className={`flex-1 rounded-full ${i < strength ? "bg-violet" : "bg-white/10"}`} />
              ))}
            </div>
            <span className="w-16 text-right text-xs text-white/50">{STRENGTH_LABELS[strength]}</span>
          </div>
        )}

        <Button type="submit" size="lg" disabled={submitting} className="mt-2">
          {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
          {submitting ? "Creating…" : "Create workspace"}
        </Button>
      </form>

      <p className="mt-5 text-center text-sm text-white/50">
        Already have an account?{" "}
        <Link href="/login" className="text-white hover:underline">
          Log in
        </Link>
      </p>
    </Card>
  );
}
