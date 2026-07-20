"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { loginSchema, type LoginInput } from "@/lib/validators";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

function LoginInner() {
  const params = useSearchParams();
  const next = params.get("next") || "/dashboard";

  const [values, setValues] = useState<LoginInput>({ email: "", password: "" });
  const [errors, setErrors] = useState<Partial<Record<keyof LoginInput, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const set = (key: keyof LoginInput) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setValues((v) => ({ ...v, [key]: e.target.value }));

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    const parsed = loginSchema.safeParse(values);
    if (!parsed.success) {
      const fe: Partial<Record<keyof LoginInput, string>> = {};
      for (const issue of parsed.error.issues) fe[issue.path[0] as keyof LoginInput] ??= issue.message;
      setErrors(fe);
      return;
    }
    setErrors({});
    setSubmitting(true);
    try {
      await api.login(parsed.data.email, parsed.data.password);
      // Full navigation so the session cookie is present for middleware on the protected route.
      window.location.assign(next);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
      setSubmitting(false);
    }
  }

  return (
    <Card>
      <CardTitle>Welcome back</CardTitle>
      <CardDescription>Log in to your Calyvora workspace.</CardDescription>

      <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-4" noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}

        <Field label="Email" htmlFor="email" error={errors.email}>
          <Input id="email" type="email" value={values.email} onChange={set("email")}
            aria-invalid={!!errors.email} autoComplete="email" placeholder="you@acme.com" />
        </Field>

        <Field label="Password" htmlFor="password" error={errors.password}>
          <Input id="password" type="password" value={values.password} onChange={set("password")}
            aria-invalid={!!errors.password} autoComplete="current-password" />
        </Field>

        <Button type="submit" size="lg" disabled={submitting} className="mt-2">
          {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
          {submitting ? "Logging in…" : "Log in"}
        </Button>
      </form>

      <p className="mt-5 text-center text-sm text-white/50">
        New here?{" "}
        <Link href="/register" className="text-white hover:underline">
          Create a workspace
        </Link>
      </p>
    </Card>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<Card><CardTitle>Welcome back</CardTitle></Card>}>
      <LoginInner />
    </Suspense>
  );
}
