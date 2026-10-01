import { expect, test } from "@playwright/test";
import { newTenant, seedTenant, signIn, watchForPageErrors } from "./support/tenant";

/**
 * Getting in, being kept out, and the routes that are deliberately not what they say.
 *
 * <p>Nav guards have never been tested. They are pure routing — no server involved — which is
 * exactly the kind of logic that silently rots when a route moves, and moving a route is something
 * this codebase has now done twice in two days.
 */
test.describe("signing in", () => {
  test("a signed-out visitor asking for an app page is sent to the login screen", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole("button", { name: /log in/i })).toBeVisible();
  });

  test("the right password gets you in; the wrong one says so without saying which half", async ({ page }) => {
    const tenant = newTenant("auth");
    await seedTenant(page, tenant);

    await page.goto("/login");
    await page.fill("#email", tenant.email);
    await page.fill("#password", "not-the-password");
    await page.getByRole("button", { name: /log in/i }).click();

    // Generic on purpose — telling somebody the email exists but the password is wrong is an
    // account-enumeration oracle.
    await expect(page.getByText(/invalid email or password/i)).toBeVisible();
    await expect(page).toHaveURL(/\/login/);

    await page.fill("#password", tenant.password);
    await page.getByRole("button", { name: /log in/i }).click();
    await expect(page).not.toHaveURL(/\/login/);
  });

  test("signup is closed, and the old address lands on what replaced it", async ({ page }) => {
    // /register was the marketing site's call to action for months and is still in emails and
    // browser histories. It must not 404 (PD-21).
    await page.goto("/register");
    await expect(page).toHaveURL(/\/request-trial/);
  });

  test("expenses opens inside Finance, and the old path still resolves", async ({ page }) => {
    const tenant = newTenant("routes");
    await seedTenant(page, tenant);
    await signIn(page, tenant);

    // Notification rows in customer databases carry the old link, so it has to keep working.
    await page.goto("/me/expenses");
    await expect(page).toHaveURL(/\/finance\/expenses/);
  });
});

test.describe("signing out", () => {
  test("after signing out, an app page is no longer reachable", async ({ page }) => {
    const tenant = newTenant("signout");
    await seedTenant(page, tenant);
    await signIn(page, tenant);

    const problems = watchForPageErrors(page);
    // The real control, not a shortcut: the access token lives in memory, so faking a sign-out by
    // clearing storage would test nothing that happens when somebody presses the button.
    await page.getByRole("button", { name: /log out/i }).first().click();
    await page.waitForURL(/\/login/, { timeout: 20_000 });

    // And the session must actually be gone, not merely navigated away from.
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login/);
    expect(problems, problems.join("\n")).toEqual([]);
  });
});
