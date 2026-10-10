/**
 * Tax years as the tax screens name them ("2026-27": April 2026 to March 2027).
 *
 * <p>Form 130 is wanted most in June, for the year that has just ended — so every document screen
 * offers the last few years, not just the current one.
 */
export function taxYearOf(date: Date = new Date()): string {
  const start = date.getMonth() >= 3 ? date.getFullYear() : date.getFullYear() - 1;
  return `${start}-${String((start + 1) % 100).padStart(2, "0")}`;
}

/** The current tax year and the ones before it, newest first. */
export function recentTaxYears(count = 3, today: Date = new Date()): string[] {
  const start = Number(taxYearOf(today).slice(0, 4));
  return Array.from({ length: count }, (_, i) => {
    const y = start - i;
    return `${y}-${String((y + 1) % 100).padStart(2, "0")}`;
  });
}

/** "?year=2025-27" for links, or nothing for the current year (the server's default). */
export function yearQuery(year: string | null | undefined, extra?: Record<string, string | null | undefined>): string {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(extra ?? {})) if (v) p.set(k, v);
  if (year && year !== taxYearOf()) p.set("year", year);
  const q = p.toString();
  return q ? `?${q}` : "";
}
