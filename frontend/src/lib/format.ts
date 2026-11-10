/**
 * App-wide localization. The company's currency, and the person's own language, timezone and date/time
 * style, come down on `/me` and are cached here so any module can format money, dates and timestamps
 * consistently without threading settings through props. `setLocaleConfig` is called from the session
 * whenever `me` loads or changes.
 */

import type { DateFormatPref, TimeFormatPref } from "@/lib/types";

type LocaleConfig = {
  currency: string;
  timezone: string;
  /** The language the app is shown in ("en", "hi", …). */
  language: string;
  /** Null writes dates the way the language does. */
  dateFormat: DateFormatPref | null;
  /** Null follows the language. */
  timeFormat: TimeFormatPref | null;
};

let config: LocaleConfig = { currency: "INR", timezone: "UTC", language: "en", dateFormat: null, timeFormat: null };

/** Pick a sensible number-grouping locale per currency (e.g. INR → 1,45,000). */
const LOCALE_FOR_CURRENCY: Record<string, string> = {
  INR: "en-IN", USD: "en-US", EUR: "de-DE", GBP: "en-GB",
  AED: "ar-AE", SGD: "en-SG", AUD: "en-AU", CAD: "en-CA",
};

/** The regional flavour of each language. English takes its region from the company currency. */
const LOCALE_FOR_LANGUAGE: Record<string, string> = {
  hi: "hi-IN", es: "es-ES", fr: "fr-FR", de: "de-DE",
};

export function setLocaleConfig(next: Partial<LocaleConfig>): void {
  config = { ...config, ...next };
}

export function currentCurrency(): string {
  return config.currency;
}

export function currentTimezone(): string {
  return config.timezone;
}

export function currentLanguage(): string {
  return config.language;
}

/** The BCP-47 locale dates and words are written in. */
export function displayLocale(): string {
  return LOCALE_FOR_LANGUAGE[config.language] ?? LOCALE_FOR_CURRENCY[config.currency] ?? "en-IN";
}

/**
 * Format an amount in the company currency (or an explicit override). Whole rupees/dollars — payroll
 * here works in whole units. Falls back to a plain "CUR 1,234" if the runtime lacks the currency.
 */
export function money(n: number | null | undefined, currencyOverride?: string): string {
  if (n == null) return "—";
  const currency = currencyOverride ?? config.currency;
  const locale = LOCALE_FOR_CURRENCY[currency];
  try {
    return new Intl.NumberFormat(locale, { style: "currency", currency, maximumFractionDigits: 0 }).format(n);
  } catch {
    return `${currency} ${n.toLocaleString()}`;
  }
}

type DateInput = string | Date | null | undefined;

/**
 * A calendar date ("2026-10-07") is a day, not an instant: it must print as the 7th everywhere, not
 * shift to the 6th for somebody west of UTC. Those are read and written in UTC; full timestamps are
 * written in the person's timezone.
 */
function toInstant(value: DateInput): { at: Date; zone: string } | null {
  if (value == null || value === "") return null;
  if (value instanceof Date) return isNaN(value.getTime()) ? null : { at: value, zone: config.timezone };
  const day = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (day) return { at: new Date(Date.UTC(+day[1], +day[2] - 1, +day[3])), zone: "UTC" };
  const at = new Date(value);
  return isNaN(at.getTime()) ? null : { at, zone: config.timezone };
}

function numericDate(at: Date, zone: string, order: DateFormatPref): string {
  const parts = new Intl.DateTimeFormat("en-GB", { timeZone: zone, day: "2-digit", month: "2-digit", year: "numeric" })
    .formatToParts(at);
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? "";
  const [d, m, y] = [get("day"), get("month"), get("year")];
  return order === "MDY" ? `${m}/${d}/${y}` : order === "YMD" ? `${y}-${m}-${d}` : `${d}/${m}/${y}`;
}

/** A date in the person's chosen style: "07/10/2026", or the language's own ("7 Oct 2026") by default. */
export function formatDate(value: DateInput): string {
  const v = toInstant(value);
  if (!v) return "—";
  if (config.dateFormat) return numericDate(v.at, v.zone, config.dateFormat);
  try {
    return new Intl.DateTimeFormat(displayLocale(), { dateStyle: "medium", timeZone: v.zone }).format(v.at);
  } catch {
    return v.at.toLocaleDateString();
  }
}

/** A clock time in the person's timezone, 12- or 24-hour as they chose. */
export function formatTime(value: DateInput): string {
  const v = toInstant(value);
  if (!v) return "—";
  try {
    return new Intl.DateTimeFormat(displayLocale(), {
      hour: "2-digit", minute: "2-digit", timeZone: v.zone, ...hourCycle(),
    }).format(v.at);
  } catch {
    return v.at.toLocaleTimeString();
  }
}

/** A timestamp rendered in the person's timezone (date + time). */
export function dateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  return `${formatDate(iso)}, ${formatTime(iso)}`;
}

/** "October 2026" for "2026-10", in the person's language. */
export function monthYear(ym: string, month: "long" | "short" = "long"): string {
  const [y, m] = ym.split("-").map(Number);
  if (!y || !m) return ym;
  return new Intl.DateTimeFormat(displayLocale(), { month, year: "numeric", timeZone: "UTC" })
    .format(new Date(Date.UTC(y, m - 1, 1)));
}

/** Any other date shape ("Mon", "7 October"…) in the person's language. */
export function formatDateWith(value: DateInput, options: Intl.DateTimeFormatOptions): string {
  const v = toInstant(value);
  if (!v) return "—";
  return new Intl.DateTimeFormat(displayLocale(), { timeZone: v.zone, ...options }).format(v.at);
}

/** The hour-cycle option for the person's time format; empty follows the language. */
export function hourCycle(): { hour12?: boolean } {
  return config.timeFormat === "H12" ? { hour12: true } : config.timeFormat === "H24" ? { hour12: false } : {};
}

/** Run `fn` as if these choices were saved — for a live preview before the person presses Save. */
export function withLocale<T>(next: Partial<LocaleConfig>, fn: () => T): T {
  const saved = config;
  config = { ...config, ...next };
  try {
    return fn();
  } finally {
    config = saved;
  }
}
