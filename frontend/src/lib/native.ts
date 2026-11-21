/**
 * The Orbit phone app (PD-81). The app is this same website inside a native shell (mobile/), and
 * Capacitor injects a bridge into the page: `window.Capacitor`. Plugins are reached through it, so
 * the website needs no native packages of its own and behaves exactly as before in a browser.
 */

type Listener = { remove: () => Promise<void> | void };

type NativeBiometric = {
  isAvailable: () => Promise<{ isAvailable: boolean; biometryType?: number }>;
  verifyIdentity: (o: { reason?: string; title?: string; subtitle?: string; negativeButtonText?: string;
    useFallback?: boolean; maxAttempts?: number }) => Promise<void>;
};

type AppPlugin = {
  addListener: (event: "appStateChange", cb: (s: { isActive: boolean }) => void) => Promise<Listener> | Listener;
};

type Bridge = {
  isNativePlatform?: () => boolean;
  getPlatform?: () => string;
  Plugins?: { NativeBiometric?: NativeBiometric; App?: AppPlugin };
};

function bridge(): Bridge | undefined {
  if (typeof window === "undefined") return undefined;
  return (window as unknown as { Capacitor?: Bridge }).Capacitor;
}

/** True inside the Android/iOS app, false in any browser. */
export function isNativeApp(): boolean {
  return !!bridge()?.isNativePlatform?.();
}

/** Whether this phone has a fingerprint or face unlock set up. */
export async function biometricAvailable(): Promise<boolean> {
  const b = bridge()?.Plugins?.NativeBiometric;
  if (!isNativeApp() || !b) return false;
  try {
    return (await b.isAvailable()).isAvailable;
  } catch {
    return false;
  }
}

/** Ask for fingerprint or face. Resolves true when it matched. */
export async function verifyBiometric(reason = "Unlock Orbit"): Promise<boolean> {
  const b = bridge()?.Plugins?.NativeBiometric;
  if (!b) return false;
  try {
    await b.verifyIdentity({ reason, title: "Unlock Orbit", subtitle: reason, negativeButtonText: "Cancel",
      useFallback: true, maxAttempts: 3 });
    return true;
  } catch {
    return false;
  }
}

/** Called whenever the app comes back to the foreground (or goes to the background). */
export async function onAppStateChange(cb: (active: boolean) => void): Promise<() => void> {
  const app = bridge()?.Plugins?.App;
  if (!isNativeApp() || !app) return () => {};
  const l = await app.addListener("appStateChange", (s) => cb(s.isActive));
  return () => { void l.remove(); };
}

// The person's own choice, on this phone only. Per-device by nature: it says nothing about any
// other phone, and it carries no secret — the session itself stays in its HttpOnly cookie.
const KEY = "orbit.biometricLock";

export function biometricLockOn(): boolean {
  try { return localStorage.getItem(KEY) === "on"; } catch { return false; }
}

export function setBiometricLock(on: boolean): void {
  try { if (on) localStorage.setItem(KEY, "on"); else localStorage.removeItem(KEY); } catch { /* private mode */ }
}
