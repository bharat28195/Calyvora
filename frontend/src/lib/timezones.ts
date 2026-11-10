/**
 * Every zone the browser knows, so the list is the same one the server validates against. Falls
 * back to a short hand-written list on the rare runtime that lacks the API, rather than an empty
 * dropdown that makes the field look broken.
 */
export const TIMEZONES: string[] = (() => {
  try {
    const all = (Intl as unknown as { supportedValuesOf?: (k: string) => string[] }).supportedValuesOf?.("timeZone");
    if (all && all.length > 0) return all;
  } catch { /* fall through */ }
  return [...COMMON_ZONES()];
})();

function COMMON_ZONES(): string[] {
  return ["Asia/Kolkata", "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
    "Europe/London", "Europe/Berlin", "Europe/Paris", "Asia/Dubai", "Asia/Singapore", "Australia/Sydney"];
}

/** The zones most customers are in — India, the four US mainland zones, and a few hubs — listed first. */
export const COMMON_TIMEZONES: string[] = COMMON_ZONES();

/** "(UTC−04:00) America/New York" — the offset as it is today, so the choice is easy to recognise. */
export function zoneLabel(zone: string): string {
  try {
    const name = new Intl.DateTimeFormat("en-US", { timeZone: zone, timeZoneName: "longOffset" })
      .formatToParts(new Date()).find((p) => p.type === "timeZoneName")?.value ?? "";
    const offset = name === "GMT" ? "UTC+00:00" : name.replace("GMT", "UTC");
    return `(${offset}) ${zone.replace(/_/g, " ")}`;
  } catch {
    return zone.replace(/_/g, " ");
  }
}
