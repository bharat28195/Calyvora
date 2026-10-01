import { expect, test } from "@playwright/test";
import { newTenant, seedTenant, signIn, watchForPageErrors } from "./support/tenant";

/**
 * Every screen an admin can reach, opened.
 *
 * <p>This is the test the suite was written for. Types, lint, `next build` and the unit tests all
 * pass on a page that renders nothing because it threw in an effect — and on a page that crashes
 * because an API shape changed under it, which has happened four times this week as endpoints moved
 * to cursor pages. Opening the screen is the only check that can tell.
 *
 * <p>The assertion is deliberately shallow: a heading with something in it, a status under 400, and
 * a clean console. Pinning the wording would turn every improved sentence into a failing test, and
 * the defects this exists to catch are not subtle — they are blank pages and stack traces.
 */

/** Everything in the admin's left nav, plus the panes hanging off it. */
const SCREENS = [
  "/dashboard",
  "/me",
  "/me/attendance",
  "/me/leave",
  "/people",
  "/people/org",
  "/finance/pay",
  "/finance/mine",
  "/finance/expenses",
  "/finance/tax",
  "/performance/me",
  "/work",
  "/knowledge",
  "/helpdesk",
  "/feed",
  "/documents",
  "/settings",
];

test.describe("every admin screen renders", () => {
  for (const path of SCREENS) {
    test(`${path} opens without errors`, async ({ page }) => {
      const tenant = newTenant("screens");
      await seedTenant(page, tenant);
      await signIn(page, tenant);

      const problems = watchForPageErrors(page);
      const response = await page.goto(path);

      // A route that does not exist is a 404 the build will happily produce.
      expect(response?.status(), `${path} returned ${response?.status()}`).toBeLessThan(400);

      // Something must actually be on the page. A crashed screen renders an empty shell.
      const heading = page.locator("h1").first();
      await expect(heading).toBeVisible({ timeout: 15_000 });
      await expect(heading).not.toHaveText("", { timeout: 15_000 });

      expect(problems, `${path}:\n${problems.join("\n")}`).toEqual([]);
    });
  }
});

test.describe("empty states", () => {
  test("a brand-new company is told there is nothing yet, not shown a broken table", async ({ page }) => {
    const tenant = newTenant("empty");
    await seedTenant(page, tenant);
    await signIn(page, tenant);

    const problems = watchForPageErrors(page);
    // A fresh tenant has no claims, no documents, no tickets. Each of these lists has to cope with
    // nothing at all — the state every customer is in for their first week and no seeded demo ever
    // reproduces.
    for (const path of ["/finance/expenses", "/documents", "/helpdesk", "/work"]) {
      await page.goto(path);
      await expect(page.locator("h1").first()).toBeVisible({ timeout: 15_000 });
    }
    expect(problems, problems.join("\n")).toEqual([]);
  });
});
