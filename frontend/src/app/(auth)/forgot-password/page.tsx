"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Loader2, MailCheck, AlertTriangle, RefreshCw } from "lucide-react";
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
 *
 * <p>Resending is allowed, but not faster than the server will honour it. The backend sends at most
 * one mail per address every RESEND_SECONDS and says nothing when it declines (see above — "too
 * soon" would leak that the address exists). So the countdown lives here: without it, a second
 * click inside the window would look exactly like a delivery, and the person would sit waiting
 * for a mail that was never sent.
 */
const RESEND_SECONDS = 30;

/** Seconds left before another mail to this address will actually go out. */
function useCountdown(since: number | null) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (since === null) return;
    setNow(Date.now());
    const id = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(id);
  }, [since]);
  if (since === null) return 0;
  return Math.max(0, Math.ceil((since + RESEND_SECONDS * 1000 - now) / 1000));
}
export default function ForgotPasswordPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  // The address and moment of the last send, so the timer follows the address rather than the
  // screen: going back to the form and resubmitting the same email is still a resend.
  const [lastSend, setLastSend] = useState<{ email: string; at: number } | null>(null);
  const secondsLeft = useCountdown(lastSend?.at ?? null);
  const sameAddress = lastSend !== null && lastSend.email === email.trim().toLowerCase();
  const waiting = sameAddress && secondsLeft > 0;
  // Whether this deployment can actually deliver mail. A fact about the server, not the account, so
  // asking costs nothing and gives nothing away. Null = unknown (production hides the endpoint).
  const [delivers, setDelivers] = useState<boolean | null>(null);
  useEffect(() => {
    void api.mailStatus().then((s) => setDelivers(s ? s.delivers : null));
  }, []);

  async function onSubmit(e?: React.SyntheticEvent) {
    e?.preventDefault();
    setError(null);
    if (!email.trim()) {
      setError("Enter the email you sign in with.");
      return;
    }
    if (waiting) return;
    setSubmitting(true);
    try {
      await api.forgotPassword(email.trim());
      setLastSend({ email: email.trim().toLowerCase(), at: Date.now() });
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
        {/* Only promise a delivery that could actually have happened. With no mail provider
            configured the code exists but was written to the server log, and telling someone to
            check an inbox leaves them waiting for a message that is never coming. */}
        {delivers === false ? (
          <>
            <AlertTriangle className="mx-auto h-10 w-10 text-amber-400" />
            <CardTitle className="mt-4">Your code was created &mdash; but not emailed</CardTitle>
            <CardDescription>
              This deployment has no mail provider configured, so nothing was sent to{" "}
              <span className="text-fg">{email.trim()}</span>. The code is in the{" "}
              <Link href="/dev/mailbox" className="text-violet underline">dev mailbox</Link>.
            </CardDescription>
          </>
        ) : (
          <>
            <MailCheck className="mx-auto h-10 w-10 text-emerald-400" />
            <CardTitle className="mt-4">Check your email</CardTitle>
            <CardDescription>
              If <span className="text-fg">{email.trim()}</span> has an account, a 6-digit code is on
              its way. It expires in 15 minutes.
            </CardDescription>
          </>
        )}
        <div className="mt-6 flex flex-col gap-3">
          <Button
            size="lg"
            onClick={() => router.push(`/reset-password?email=${encodeURIComponent(email.trim())}`)}
          >
            I have the code
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="lg"
            disabled={waiting || submitting}
            onClick={(e) => void onSubmit(e)}
          >
            {submitting ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <RefreshCw className="h-4 w-4" />
            )}
            {waiting ? `Resend in ${secondsLeft}s` : "Resend email"}
          </Button>
          {waiting && (
            <p className="text-xs text-fg/40">
              Didn&apos;t get it? Check your spam folder while you wait.
            </p>
          )}
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

        <Button type="submit" size="lg" disabled={submitting || waiting}>
          {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
          {submitting ? "Sending…" : waiting ? `Resend in ${secondsLeft}s` : "Send me a code"}
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
