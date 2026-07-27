"use client";

import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import { settingsSchema } from "@/lib/validators";
import type { CompanySettings } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

const TIMEZONES = ["UTC", "Asia/Kolkata", "Asia/Singapore", "Asia/Dubai", "Europe/London", "Europe/Berlin", "America/New_York", "America/Los_Angeles"];
const LOCALES = ["en", "en-GB", "fr", "de", "es", "hi"] as const;
const CURRENCIES = ["INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD"] as const;
const CURRENCY_LABEL: Record<(typeof CURRENCIES)[number], string> = {
  INR: "₹ Indian Rupee (INR)", USD: "$ US Dollar (USD)", EUR: "€ Euro (EUR)", GBP: "£ British Pound (GBP)",
  AED: "د.إ UAE Dirham (AED)", SGD: "$ Singapore Dollar (SGD)", AUD: "$ Australian Dollar (AUD)", CAD: "$ Canadian Dollar (CAD)",
};
const LANGUAGE_LABEL: Record<(typeof LOCALES)[number], string> = {
  en: "English (US)", "en-GB": "English (UK)", fr: "Français", de: "Deutsch", es: "Español", hi: "हिन्दी",
};

export default function SettingsPage() {
  const { me, setMe } = useSession();
  const [settings, setSettings] = useState<CompanySettings | null>(null);
  const [timezone, setTimezone] = useState("UTC");
  const [locale, setLocale] = useState<(typeof LOCALES)[number]>("en");
  const [currency, setCurrency] = useState<(typeof CURRENCIES)[number]>("INR");
  const [logoUrl, setLogoUrl] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.getSettings().then((s) => {
      setSettings(s);
      setTimezone(s.timezone);
      setLocale((LOCALES as readonly string[]).includes(s.locale) ? (s.locale as (typeof LOCALES)[number]) : "en");
      setCurrency((CURRENCIES as readonly string[]).includes(s.currency) ? (s.currency as (typeof CURRENCIES)[number]) : "INR");
      setLogoUrl(s.logoUrl ?? "");
    }).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load settings"));
  }, []);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSaved(false);
    const parsed = settingsSchema.safeParse({ timezone, locale, currency, logoUrl });
    if (!parsed.success) {
      setError(parsed.error.issues[0].message);
      return;
    }
    setBusy(true);
    try {
      const updated = await api.updateSettings({ timezone, locale, currency, logoUrl });
      setSettings(updated);
      setSaved(true);
      // Reflect currency/timezone across the app immediately (money + timestamps read from the session).
      if (me) setMe({ ...me, company: { ...me.company, currency: updated.currency, timezone: updated.timezone } });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save settings");
    } finally {
      setBusy(false);
    }
  }

  if (!settings) {
    return (
      <Card><Loader2 className="mx-auto h-6 w-6 animate-spin text-violet" /></Card>
    );
  }

  return (
    <div className="max-w-xl">
      <h1 className="text-2xl font-semibold tracking-tight">Company settings</h1>
      <p className="mt-1 text-fg/50">Configure your workspace.</p>

      <Card className="mt-8">
        <CardTitle>{me?.company.name}</CardTitle>
        <CardDescription>Company name is fixed for this sprint. Slug: {me?.company.slug}</CardDescription>

        <form onSubmit={save} className="mt-6 flex flex-col gap-4" noValidate>
          {error && <Alert tone="error">{error}</Alert>}
          {saved && <Alert tone="success">Settings saved.</Alert>}

          <div className="rounded-lg border border-fg/10 bg-fg/[0.02] p-4">
            <p className="text-sm font-medium">Localization</p>
            <p className="mt-0.5 text-xs text-fg/50">Currency is used everywhere money is shown. Language is saved for a future update.</p>

            <Field label="Currency" htmlFor="currency" className="mt-4">
              <select id="currency" value={currency} onChange={(e) => setCurrency(e.target.value as (typeof CURRENCIES)[number])}
                className="h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet">
                {CURRENCIES.map((c) => <option key={c} value={c} className="bg-surface">{CURRENCY_LABEL[c]}</option>)}
              </select>
            </Field>

            <Field label="Timezone" htmlFor="timezone" className="mt-4">
              <select id="timezone" value={timezone} onChange={(e) => setTimezone(e.target.value)}
                className="h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet">
                {TIMEZONES.map((tz) => <option key={tz} value={tz} className="bg-surface">{tz}</option>)}
              </select>
            </Field>

            <Field label="Language" htmlFor="locale" className="mt-4">
              <select id="locale" value={locale} onChange={(e) => setLocale(e.target.value as (typeof LOCALES)[number])}
                className="h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet">
                {LOCALES.map((l) => <option key={l} value={l} className="bg-surface">{LANGUAGE_LABEL[l]}</option>)}
              </select>
            </Field>
          </div>

          <Field label="Logo URL" htmlFor="logoUrl" hint="Optional. An https link to your logo.">
            <Input id="logoUrl" type="url" value={logoUrl} placeholder="https://…"
              onChange={(e) => setLogoUrl(e.target.value)} />
          </Field>

          <Button type="submit" disabled={busy} className="mt-2 self-start">
            {busy && <Loader2 className="h-4 w-4 animate-spin" />} Save changes
          </Button>
        </form>
      </Card>
    </div>
  );
}
