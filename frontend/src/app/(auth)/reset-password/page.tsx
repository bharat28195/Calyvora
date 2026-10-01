"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { CheckCircle2, Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { resetPasswordSchema, passwordStrength, type ResetPasswordInput } from "@/lib/validators";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

const STRENGTH_LABELS = ["Too weak", "Weak", "Fair", "Good", "Strong"];

/** Step two of a forgotten password (PD-23): spend the code, set the new password. */
function ResetPasswordInner() {
  const router = useRouter();
  const params = useSearchParams();

  const [values, setValues] = useState<ResetPasswordInput>({
    // Carried over from the previous screen so nobody retypes it — and so the address the code was
    // sent to is the address it is spent against.
    email: params.get("email") ?? "",
    code: "",
    newPassword: "",
  });
  const [errors, setErrors] = useState<Partial<Record<keyof ResetPasswordInput, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const set = (key: keyof ResetPasswordInput) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setValues((v) => ({ ...v, [key]: e.target.value }));

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    const parsed = resetPasswordSchema.safeParse(values);
    if (!parsed.success) {
      const fieldErrors: Partial<Record<keyof ResetPasswordInput, string>> = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof ResetPasswordInput;
        fieldErrors[key] ??= issue.message;
      }
      setErrors(fieldErrors);
      return;
    }
    setErrors({});
    setSubmitting(true);
    try {
      await api.resetPassword(parsed.data);
      setDone(true);
    } catch (err) {
      // The backend deliberately gives one message for every failure — wrong code, expired code,
      // unknown account — so it is shown as-is rather than guessed at.
      setFormError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <Card className="text-center">
        <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-400" />
        <CardTitle className="mt-4">Password changed</CardTitle>
        <CardDescription>
          You&apos;ve been signed out everywhere else, so anyone still holding an old session will
          need this new password too.
        </CardDescription>
        <Button size="lg" className="mt-6 w-full" onClick={() => router.push("/login")}>
          Sign in
        </Button>
      </Card>
    );
  }

  const strength = passwordStrength(values.newPassword);

  return (
    <Card>
      <CardTitle>Set a new password</CardTitle>
      <CardDescription>Enter the 6-digit code we emailed you, and choose a new password.</CardDescription>

      <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-4" noValidate>
        {formError && <Alert tone="error">{formError}</Alert>}

        <Field label="Work email" htmlFor="email" error={errors.email}>
          <Input id="email" type="email" value={values.email} onChange={set("email")}
            aria-invalid={!!errors.email} autoComplete="email" />
        </Field>

        <Field label="6-digit code" htmlFor="code" error={errors.code} hint="Expires 15 minutes after it's sent.">
          <Input id="code" value={values.code} onChange={set("code")} aria-invalid={!!errors.code}
            inputMode="numeric" autoComplete="one-time-code" maxLength={6} placeholder="000000"
            autoFocus={!!values.email}
            className="text-center font-mono text-lg tracking-[0.4em]" />
        </Field>

        <Field label="New password" htmlFor="newPassword" error={errors.newPassword}
          hint="At least 10 characters, with a letter and a number.">
          <Input id="newPassword" type="password" value={values.newPassword} onChange={set("newPassword")}
            aria-invalid={!!errors.newPassword} autoComplete="new-password" />
        </Field>

        {values.newPassword.length > 0 && (
          <div className="flex items-center gap-2" aria-hidden>
            <div className="flex h-1.5 flex-1 gap-1">
              {[0, 1, 2, 3].map((i) => (
                <div key={i} className={`flex-1 rounded-full ${i < strength ? "bg-violet" : "bg-fg/10"}`} />
              ))}
            </div>
            <span className="w-16 text-right text-xs text-fg/50">{STRENGTH_LABELS[strength]}</span>
          </div>
        )}

        <Button type="submit" size="lg" disabled={submitting} className="mt-2">
          {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
          {submitting ? "Saving…" : "Set new password"}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-fg/50">
        Code expired?{" "}
        <Link href="/forgot-password" className="text-fg hover:underline">
          Ask for another
        </Link>
      </p>
    </Card>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<Card><CardTitle>Set a new password</CardTitle></Card>}>
      <ResetPasswordInner />
    </Suspense>
  );
}
