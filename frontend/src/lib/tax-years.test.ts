import { describe, expect, it } from "vitest";
import { recentTaxYears, taxYearOf, yearQuery } from "./tax-years";

describe("tax years", () => {
  it("runs April to March", () => {
    expect(taxYearOf(new Date(2026, 3, 1))).toBe("2026-27");   // 1 April 2026
    expect(taxYearOf(new Date(2027, 2, 31))).toBe("2026-27");  // 31 March 2027
    expect(taxYearOf(new Date(2026, 2, 31))).toBe("2025-26");
    expect(taxYearOf(new Date(2099, 5, 1))).toBe("2099-00");
  });

  it("lists the recent years newest first", () => {
    expect(recentTaxYears(3, new Date(2026, 9, 10))).toEqual(["2026-27", "2025-26", "2024-25"]);
  });

  it("leaves the current year out of links", () => {
    expect(yearQuery(taxYearOf())).toBe("");
    expect(yearQuery("2020-21", { employee: "e1" })).toBe("?employee=e1&year=2020-21");
  });
});
