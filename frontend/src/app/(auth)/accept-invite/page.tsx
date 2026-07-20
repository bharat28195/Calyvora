"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { acceptInviteSchema, passwordStrength, type AcceptInviteInput } from "@/lib/validators";
import { Button, LinkButton } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import type { Role } from "@/lib/types";

interface Preview {
  email: string;
  companyName: string;
  role: Role;
}

function AcceptInviteInner() {
  const params = useSearchParams();
  const token = params.get("token");

  const [preview, setPreview] = useState<Preview | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [done, setDone] = useState(false);

  const [values, setValues] = useState<AcceptInviteInput>({ firstName: "", lastName: "", password: "" });
  const [errors, setErrors] = useState<Partial<Record<keyof AcceptInviteInput, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!token) {
      setLoadError("This invitation link is missing its token.");
      setLoading(false);
      return;
    }
    api
      .invitationPreview(token)
      .then(setPreview)
      .catch((e) => setLoadError(e instanceof ApiError ? e.message : "This invitation is invalid."))
      .finally(() => setLoading(false));
  }, [token]);

  const set = (key: keyof AcceptInviteInput) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setValues((v) => ({ ...v, [key]: e.target.value }));

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    const parsed = acceptInviteSchema.safeParse(values);
    if (!parsed.success) {
      const fe: Partial<Record<keyof AcceptInviteInput, string>> = {};
      for (const issue of parsed.error.issues) fe[issue.path[0] as keyof AcceptInviteInput] ??= issue.message;
      setErrors(fe);
      return;
    }
    setErrors({});
    setSubmitting(true);
    try {
      await api.acceptInvitation({ token: token!, ...parsed.data });
      setDone(true);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <Card className="text-center">
        <Loader2 className="mx-auto h-10 w-10 animate-spin text-violet" />
      </Card>
    );
  }

  if (loadError) {
    return (
      <Card className="text-center">
        <XCircle className="mx-auto h-10 w-10 text-red-400" />
        <CardTitle className="mt-4">Invitation problem</CardTitle>
        <CardDescription>{loadError}</CardDescription>
        <Link href="/login" className="mt-6 inline-block text-sm text-white/60 hover:text-white">
          Back to log in
        </Link>
      </Card>
    );
  }

  if (done) {
    return (
      <Card className="text-center">
        <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-400" />
        <CardTitle className="mt-4">You&apos;re in</CardTitle>
        <CardDescription>Your account is active. Log in to join {preview?.companyName}.</CardDescription>
        <div className="mt-6">
          <LinkButton href="/login" size="lg">Continue to log in</LinkButton>
        </div>
      </Card>
    );
  }

  const strength = passwordStrength(values.password);

  return (
    <Card>
      <CardTitle>Join {preview?.companyName}</CardTitle>
      <CardDescription>
        You were invited as <span className="text-white">{preview?.role.toLowerCase()}</span>. Set your
        name and a password to accept.
      </CardDescription>

      <div className="mt-4 rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-white/60">
        {preview?.email}
      </div>

      <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-4" noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}

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

        <Field label="Password" htmlFor="password" error={errors.password}
          hint="At least 10 characters, with a letter and a number.">
          <Input id="password" type="password" value={values.password} onChange={set("password")}
            aria-invalid={!!errors.password} autoComplete="new-password" />
        </Field>

        {values.password.length > 0 && (
          <div className="flex h-1.5 gap-1" aria-hidden>
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className={`flex-1 rounded-full ${i < strength ? "bg-violet" : "bg-white/10"}`} />
            ))}
          </div>
        )}

        <Button type="submit" size="lg" disabled={submitting} className="mt-2">
          {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
          {submitting ? "Joining…" : "Accept invitation"}
        </Button>
      </form>
    </Card>
  );
}

export default function AcceptInvitePage() {
  return (
    <Suspense fallback={<Card className="text-center"><Loader2 className="mx-auto h-10 w-10 animate-spin text-violet" /></Card>}>
      <AcceptInviteInner />
    </Suspense>
  );
}
