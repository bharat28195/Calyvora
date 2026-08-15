"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Loader2, MailCheck } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/**
 * Step one of a forgotten password (PD-23): ask for a code.
 *
 * <p>The confirmation is deliberately worded to cover both cases — "if that address has an account".
 * The backend answers identically whether or not it does, so that this endpoint cannot be used to
 * ask "does this person work here?" one address at a time. A screen that said "sent!" for real
 * addresses and "not found" for others would hand back exactly what the backend refuses to give.
 */
export default function ForgotPasswordPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!email.trim()) {
      setError("Enter the email you sign in with.");
      return;
    }
    setSubmitting(true);
    try {
      await api.forgotPassword(email.trim());
      setSent(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (sent) {
    return (
      <Card className="text-center">
        <MailCheck className="mx-auto h-10 w-10 text-emerald-400" />
        <CardTitle className="mt-4">Check your email</CardTitle>
        <CardDescription>
          If <span className="text-fg">{email.trim()}</span> has an account, a 6-digit code is on its
          way. It expires in 15 minutes.
        </CardDescription>
        <div className="mt-6 flex flex-col gap-3">
          <Button
            size="lg"
            onClick={() => router.push(`/reset-password?email=${encodeURIComponent(email.trim())}`)}
          >
            I have the code
          </Button>
          <button
            type="button"
            onClick={() => setSent(false)}
            className="text-sm text-fg/50 hover:text-fg"
          >
            Wrong address? Try another
          </button>
        </div>
      </Card>
    );
  }

  return (
    <Card>
      <CardTitle>Forgotten your password?</CardTitle>
      <CardDescription>
        Enter the email you sign in with and we&apos;ll send you a 6-digit code to set a new one.
      </CardDescription>

      <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}

        <Field label="Work email" htmlFor="email">
          <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)}
            autoComplete="email" placeholder="you@company.com" autoFocus />
        </Field>

        <Button type="submit" size="lg" disabled={submitting}>
          {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
          {submitting ? "Sending…" : "Send me a code"}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-fg/50">
        Remembered it?{" "}
        <Link href="/login" className="text-fg hover:underline">
          Back to sign in
        </Link>
      </p>
    </Card>
  );
}
