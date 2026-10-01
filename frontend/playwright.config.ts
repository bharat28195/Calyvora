import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests, against the mock backend.
 *
 * <p>Every frontend gate we had was static — types, lint, `next build`, and unit tests over `lib/`.
 * Not one of them opened a screen, so a page that compiled, type-checked and then threw on mount was
 * green by all four. That is not hypothetical: the tax pages shipped with a named export from a page
 * file and only `next build` caught it, and it caught it for a reason unrelated to whether the page
 * worked. These tests exist to render the thing.
 *
 * <p><b>Mock mode, not the real backend.</b> The mock is a faithful twin of the API — the codebase
 * keeps the two in step deliberately — and it means the suite needs no Postgres, no Java, and no
 * four-minute cold start. What is being tested here is the part that has never been tested at all:
 * routing, nav, guards, empty states, and whether a screen renders for a given role. Anything that
 * turns on the real server's behaviour belongs in the Java integration tests, which already cover it
 * across thirty modules and six roles.
 *
 * <p>Built and served rather than `next dev`, because the defect this suite was written after was
 * one only the production build shows.
 */
export default defineConfig({
  testDir: "./e2e",
  // Each spec registers its own company, so nothing is shared and order cannot matter.
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["list"]] : [["list"]],

  use: {
    baseURL: "http://127.0.0.1:3100",
    // On failure only: a trace is large, and one per passing test would bury the useful ones.
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },

  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],

  webServer: {
    // The production build, on a port that will not collide with a dev server someone has open.
    command: "npm run build && npx next start --port 3100 --hostname 127.0.0.1",
    url: "http://127.0.0.1:3100/login",
    reuseExistingServer: !process.env.CI,
    timeout: 300_000,
    stdout: "pipe",
    stderr: "pipe",
  },
});
