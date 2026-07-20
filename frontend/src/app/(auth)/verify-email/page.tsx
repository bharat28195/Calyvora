"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { Button, LinkButton } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

type State = "verifying" | "success" | "error";

function VerifyEmailInner() {
  const params = useSearchParams();
  const token = params.get("token");
  const [state, setState] = useState<State>("verifying");
  const [message, setMessage] = useState("");
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return; // guard React 18 StrictMode double-invoke
    ran.current = true;
    if (!token) {
      setState("error");
      setMessage("This verification link is missing its token.");
      return;
    }
    api
      .verifyEmail(token)
      .then(() => setState("success"))
      .catch((err) => {
        setState("error");
        setMessage(err instanceof ApiError ? err.message : "Verification failed.");
      });
  }, [token]);

  if (state === "verifying") {
    return (
      <Card className="text-center">
        <Loader2 className="mx-auto h-10 w-10 animate-spin text-violet" />
        <CardTitle className="mt-4">Verifying your email…</CardTitle>
      </Card>
    );
  }

  if (state === "success") {
    return (
      <Card className="text-center">
        <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-400" />
        <CardTitle className="mt-4">Email verified</CardTitle>
        <CardDescription>Your account is active. You can now log in.</CardDescription>
        <div className="mt-6">
          <LinkButton href="/login" size="lg">
            Continue to log in
          </LinkButton>
        </div>
      </Card>
    );
  }

  return (
    <Card className="text-center">
      <XCircle className="mx-auto h-10 w-10 text-red-400" />
      <CardTitle className="mt-4">Verification failed</CardTitle>
      <CardDescription>{message}</CardDescription>
      <div className="mt-6">
        <ResendForm />
        <Link href="/login" className="mt-4 inline-block text-sm text-white/60 hover:text-white">
          Back to log in
        </Link>
      </div>
    </Card>
  );
}

function ResendForm() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await api.resendVerification(email);
      setSent(true);
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <Alert tone="success">
        If that account needs verification, a new link is on its way. Check{" "}
        <Link href="/dev/mailbox" className="underline">
          /dev/mailbox
        </Link>
        .
      </Alert>
    );
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-2 text-left">
      <Field label="Resend verification email" htmlFor="resend-email">
        <Input id="resend-email" type="email" placeholder="you@acme.com" value={email}
          onChange={(e) => setEmail(e.target.value)} required />
      </Field>
      <Button type="submit" variant="secondary" disabled={busy}>
        {busy && <Loader2 className="h-4 w-4 animate-spin" />} Resend link
      </Button>
    </form>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<Card className="text-center"><Loader2 className="mx-auto h-10 w-10 animate-spin text-violet" /></Card>}>
      <VerifyEmailInner />
    </Suspense>
  );
}
