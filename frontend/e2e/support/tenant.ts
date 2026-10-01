import type { Page } from "@playwright/test";

/**
 * A company and a signed-out user, planted in the mock backend before the page loads.
 *
 * <p>Self-serve signup is closed (PD-21) — {@code /register} redirects to the trial request form —
 * so there is no way to create an account through the UI, and these tests need one to have anything
 * to sign in to. The mock keeps its whole world in one localStorage key, so seeding it is writing
 * that key before the app's first script runs.
 *
 * <p>Deliberately stops short of signing in. The login form is itself one of the things worth
 * testing, and a helper that hands back an authenticated session would skip the most-used screen in
 * the product.
 */

const KEY = "calyvora_mock_db_v1";

export interface Tenant {
  companyName: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

/** A fresh tenant per test, so tests share nothing and can run in parallel. */
export function newTenant(label: string): Tenant {
  const unique = `${label}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
  return {
    companyName: `E2E ${label}`,
    email: `${unique}@e2e.test`,
    password: "Passw0rd!x",
    firstName: "Ada",
    lastName: "Tester",
  };
}

/**
 * Write the tenant into the mock's storage before any of the app's code runs.
 *
 * <p>`addInitScript` rather than an `evaluate` after navigation: the app reads the database on its
 * first render, so seeding afterwards would race the very screen under test.
 */
export async function seedTenant(page: Page, tenant: Tenant, role = "ADMIN"): Promise<void> {
  await page.addInitScript(
    ([storageKey, t, userRole]) => {
      const uuid = () =>
        "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
          const r = (Math.random() * 16) | 0;
          return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
        });
      const companyId = uuid();
      const userId = uuid();
      const db = {
        companies: [{ id: companyId, name: t.companyName, slug: `e2e-${companyId.slice(0, 8)}`, status: "ACTIVE" }],
        users: [{
          id: userId, companyId, email: t.email, password: t.password,
          firstName: t.firstName, lastName: t.lastName, role: userRole, status: "ACTIVE",
        }],
        tokens: [], invitations: [],
        settings: [{
          companyId, timezone: "Asia/Kolkata", locale: "en", currency: "INR",
          legalName: null, address: null, logoUrl: null, sessionIdleMinutes: null,
        }],
        employees: [], departments: [], onboarding: [], leave: [], projects: [], tasks: [],
        sprints: [], tickets: [], spaces: [], pages: [], sessions: {}, mailbox: [],
      };
      // Only when there is nothing there. addInitScript runs on every navigation, so writing
      // unconditionally would re-seed on each goto and wipe the session login had just created —
      // which looks exactly like "the app forgot I was signed in".
      if (!window.localStorage.getItem(storageKey)) {
        window.localStorage.setItem(storageKey, JSON.stringify(db));
      }
    },
    [KEY, tenant, role] as const,
  );
}

/** Sign in through the real form — this is a screen under test, not a shortcut around one. */
export async function signIn(page: Page, tenant: Tenant): Promise<void> {
  await page.goto("/login");
  await page.fill("#email", tenant.email);
  await page.fill("#password", tenant.password);
  await page.getByRole("button", { name: /log in/i }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20_000 });
}

/**
 * Fail the test if the page reported an error to the console or crashed on mount.
 *
 * <p>The whole point of this suite: a screen that renders an empty div after throwing in an effect
 * looks identical to a screen with nothing to show, and every static gate we have calls both green.
 * React logs the error, so listening is enough to tell them apart.
 */
export function watchForPageErrors(page: Page): string[] {
  const problems: string[] = [];
  page.on("pageerror", (err) => problems.push(`uncaught: ${err.message}`));
  page.on("console", (msg) => {
    if (msg.type() !== "error") return;
    const text = msg.text();
    // Next's dev overlay and favicon 404s are noise, not defects.
    if (/favicon|Download the React DevTools|hydration/i.test(text)) return;
    problems.push(`console: ${text}`);
  });
  return problems;
}
