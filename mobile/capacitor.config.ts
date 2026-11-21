import type { CapacitorConfig } from "@capacitor/cli";

/**
 * Orbit's phone app (PD-81).
 *
 * The app loads the live Orbit site rather than a copy bundled inside it, so every feature shipped
 * to the web is in the app the same minute — no store release for a screen change. Capacitor still
 * injects its native bridge into that page, which is how the site reaches the fingerprint sensor.
 *
 * ORBIT_URL picks the environment at build time:
 *   production (default)  https://orbit.calyvora.in
 *   demo                  ORBIT_URL=https://calyvora-frontend.onrender.com npx cap sync
 */
const url = process.env.ORBIT_URL ?? "https://orbit.calyvora.in";

const config: CapacitorConfig = {
  appId: "in.calyvora.orbit",
  appName: "Orbit",
  // Shown only if the site can't be reached at all (offline on first launch).
  webDir: "www",
  server: {
    url,
    cleartext: false,
    // Links to anywhere else (maps, email) open outside the app.
    allowNavigation: [new URL(url).host],
  },
  android: {
    allowMixedContent: false,
  },
};

export default config;
