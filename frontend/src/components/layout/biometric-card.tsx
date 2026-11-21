"use client";

import { useEffect, useState } from "react";
import { Fingerprint } from "lucide-react";
import { biometricAvailable, biometricLockOn, setBiometricLock, verifyBiometric } from "@/lib/native";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/**
 * Account → "Unlock with fingerprint or face", in the phone app only (PD-81). Switching it on asks
 * for the fingerprint once, so nobody can turn it on for a phone they cannot unlock.
 */
export function BiometricCard() {
  const [available, setAvailable] = useState(false);
  const [on, setOn] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void biometricAvailable().then(setAvailable);
    setOn(biometricLockOn());
  }, []);

  if (!available) return null;

  async function toggle(next: boolean) {
    setError(null);
    if (next && !(await verifyBiometric("Confirm to turn on fingerprint unlock"))) {
      setError("That didn't match, so fingerprint unlock is still off.");
      return;
    }
    setBiometricLock(next);
    setOn(next);
  }

  return (
    <Card className="mt-6">
      <CardTitle className="flex items-center gap-2"><Fingerprint className="h-4 w-4 text-violet" /> Fingerprint or face unlock</CardTitle>
      <label className="mt-3 flex items-start gap-3 text-sm">
        <input type="checkbox" className="mt-0.5 h-4 w-4" checked={on} onChange={(e) => void toggle(e.target.checked)} />
        <span>
          <span className="font-medium">Ask for my fingerprint or face when I open Orbit</span>
          <span className="block text-fg/60">Only on this phone. Your password still works on every other device.</span>
        </span>
      </label>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
    </Card>
  );
}
