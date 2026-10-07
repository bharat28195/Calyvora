"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { KeyRound, Loader2, ShieldCheck } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { PasswordInput } from "@/components/ui/password-input";
import { Alert } from "@/components/ui/alert";

/**
 * Account settings: who you are signed in as, and changing your password.
 *
 * <p>Also where a company's first admin lands on first sign-in: their password was chosen by Calyvora
 * and emailed, so the app holds them here until they choose their own (V67).
 */
export default function AccountPage() {
  const session = useSession();
  const router = useRouter();
  const me = session.me;
  const forced = !!me?.user.mustChangePassword;

  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const longEnough = next.length >= 10;
  const hasLetter = /[A-Za-z]/.test(next);
  const hasNumber = /\d/.test(next);
  const matches = next.length > 0 && next === confirm;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!matches) {
      setError("The two new passwords don't match.");
      return;
    }
    setBusy(true);
    try {
      const result = await api.changePassword(current, next);
      session.setMe(result.me);
      setDone(true);
      setCurrent(""); setNext(""); setConfirm("");
      if (forced) router.replace("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't change your password");
    } finally {
      setBusy(false);
    }
  }

  if (!me) return null;

  return (
    <div className="max-w-xl">
      <h1 className="text-2xl font-semibold tracking-tight">Account settings</h1>
      <p className="mt-1 text-fg/50">Your sign-in details.</p>

      {forced && (
        <Alert tone="info" className="mt-6">
          <ShieldCheck className="mr-1 inline h-4 w-4" />
          Welcome to Orbit! You signed in with a temporary password. Choose your own to continue.
        </Alert>
      )}

      <Card className="mt-6">
        <CardTitle>Signed in as</CardTitle>
        <dl className="mt-3 grid grid-cols-[8rem_1fr] gap-y-2 text-sm">
          <dt className="text-fg/50">Name</dt><dd>{me.user.firstName} {me.user.lastName}</dd>
          <dt className="text-fg/50">Email</dt><dd>{me.user.email}</dd>
          <dt className="text-fg/50">Company</dt><dd>{me.company.name}</dd>
          <dt className="text-fg/50">Role</dt><dd className="capitalize">{me.user.role.toLowerCase()}</dd>
        </dl>
      </Card>

      <Card className="mt-6">
        <CardTitle className="flex items-center gap-2"><KeyRound className="h-4 w-4 text-violet" /> Change password</CardTitle>
        <p className="mt-1 text-sm text-fg/60">
          You&apos;ll stay signed in here; every other device is signed out.
        </p>

        {done && !forced && <Alert tone="success" className="mt-4">Password changed.</Alert>}
        {error && <Alert tone="error" className="mt-4">{error}</Alert>}

        <form onSubmit={submit} className="mt-5 flex flex-col gap-4">
          <Field label={forced ? "Temporary password (from your welcome email)" : "Current password"} htmlFor="current">
            <PasswordInput id="current" autoComplete="current-password" required value={current}
              onChange={(e) => setCurrent(e.target.value)} />
          </Field>
          <Field label="New password" htmlFor="next">
            <PasswordInput id="next" autoComplete="new-password" required value={next}
              onChange={(e) => setNext(e.target.value)} />
          </Field>
          <ul className="-mt-2 space-y-0.5 text-xs">
            <Rule ok={longEnough}>At least 10 characters</Rule>
            <Rule ok={hasLetter}>A letter</Rule>
            <Rule ok={hasNumber}>A number</Rule>
          </ul>
          <Field label="Confirm new password" htmlFor="confirm">
            <PasswordInput id="confirm" autoComplete="new-password" required value={confirm}
              onChange={(e) => setConfirm(e.target.value)} />
          </Field>
          <div>
            <Button type="submit" disabled={busy || !longEnough || !hasLetter || !hasNumber || !matches || !current}>
              {busy && <Loader2 className="h-4 w-4 animate-spin" />}
              {forced ? "Set my password" : "Change password"}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}

function Rule({ ok, children }: { ok: boolean; children: React.ReactNode }) {
  return <li className={ok ? "text-emerald-500" : "text-fg/40"}>{ok ? "✓" : "•"} {children}</li>;
}
