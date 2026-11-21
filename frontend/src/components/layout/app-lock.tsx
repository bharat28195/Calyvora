"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Fingerprint, Loader2 } from "lucide-react";
import { useSession } from "@/hooks/useSession";
import { biometricLockOn, isNativeApp, onAppStateChange, verifyBiometric } from "@/lib/native";
import { Button } from "@/components/ui/button";

/** Coming back after this long in the background asks again; a quick glance at a message does not. */
const RELOCK_AFTER_MS = 30_000;

/**
 * Fingerprint / face unlock for the Orbit phone app (PD-81). Opening the app, or returning to it after
 * a while, covers the screen until the phone's own biometric check passes. In a browser, or when the
 * person has not switched it on, this renders nothing.
 */
export function AppLock() {
  const { logout } = useSession();
  const [locked, setLocked] = useState(false);
  const [checking, setChecking] = useState(false);
  const hiddenAt = useRef<number | null>(null);

  const unlock = useCallback(async () => {
    setChecking(true);
    const ok = await verifyBiometric("Confirm it's you to open Orbit");
    setChecking(false);
    if (ok) setLocked(false);
  }, []);

  useEffect(() => {
    if (!isNativeApp() || !biometricLockOn()) return;
    setLocked(true);
    void unlock();
    let stop = () => {};
    void onAppStateChange((active) => {
      if (!biometricLockOn()) return;
      if (!active) { hiddenAt.current = Date.now(); return; }
      if (hiddenAt.current && Date.now() - hiddenAt.current > RELOCK_AFTER_MS) {
        setLocked(true);
        void unlock();
      }
      hiddenAt.current = null;
    }).then((s) => { stop = s; });
    return () => stop();
  }, [unlock]);

  if (!locked) return null;
  return (
    <div className="fixed inset-0 z-[100] flex flex-col items-center justify-center gap-6 bg-app px-6 text-center">
      <Fingerprint className="h-14 w-14 text-violet" />
      <div>
        <p className="text-lg font-semibold">Orbit is locked</p>
        <p className="mt-1 text-sm text-fg/60">Use your fingerprint or face to continue.</p>
      </div>
      <Button onClick={unlock} disabled={checking}>
        {checking && <Loader2 className="h-4 w-4 animate-spin" />} Unlock
      </Button>
      <button onClick={() => void logout()} className="text-sm text-fg/50 hover:text-fg">
        Sign in with password instead
      </button>
    </div>
  );
}
