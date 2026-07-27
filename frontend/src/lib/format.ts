/**
 * App-wide localization. The company's currency + timezone come down on `/me` and are cached here so
 * any module can format money and timestamps consistently without threading settings through props.
 * `setLocaleConfig` is called from the session whenever `me` loads or settings change.
 */

type LocaleConfig = { currency: string; timezone: string; locale: string };

let config: LocaleConfig = { currency: "INR", timezone: "UTC", locale: "en" };

/** Pick a sensible number-grouping locale per currency (e.g. INR → 1,45,000). */
const LOCALE_FOR_CURRENCY: Record<string, string> = {
  INR: "en-IN", USD: "en-US", EUR: "de-DE", GBP: "en-GB",
  AED: "ar-AE", SGD: "en-SG", AUD: "en-AU", CAD: "en-CA",
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

/** A timestamp rendered in the company timezone (date + time). */
export function dateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  try {
    return new Intl.DateTimeFormat(LOCALE_FOR_CURRENCY[config.currency], {
      dateStyle: "medium", timeStyle: "short", timeZone: config.timezone,
    }).format(new Date(iso));
  } catch {
    return new Date(iso).toLocaleString();
  }
}
