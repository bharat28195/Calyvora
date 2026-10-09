"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Globe, KeyRound, Loader2, ShieldCheck, UserRound } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import { useT } from "@/hooks/useT";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { PasswordInput } from "@/components/ui/password-input";
import { Alert } from "@/components/ui/alert";
import { LANGUAGES, DEFAULT_LANGUAGE, translate } from "@/lib/i18n";
import { COMMON_TIMEZONES, TIMEZONES, zoneLabel } from "@/lib/timezones";
import { formatDate, formatTime, withLocale } from "@/lib/format";
import type { DateFormatPref, TimeFormatPref } from "@/lib/types";

const SELECT = "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet";

type Tab = "profile" | "preferences" | "password";

/**
 * Account settings: who you are signed in as, your language and region, and changing your password.
 * One section at a time, in tabs, so nothing here needs scrolling; the profile menu deep-links
 * straight to a tab (?tab=password).
 *
 * <p>Also where a company's first admin lands on first sign-in: their password was chosen by Calyvora
 * and emailed, so the app holds them on the password tab until they choose their own (V67).
 */
export default function AccountPage() {
  const session = useSession();
  const me = session.me;
  const t = useT();
  const params = useSearchParams();
  const forced = !!me?.user.mustChangePassword;
  const [tab, setTab] = useState<Tab>("profile");

  useEffect(() => {
    const asked = params.get("tab") ?? (typeof window !== "undefined" ? window.location.hash.slice(1) : "");
    if (forced) setTab("password");
    else if (asked === "preferences" || asked === "password" || asked === "profile") setTab(asked);
  }, [params, forced]);

  if (!me) return null;

  const tabs: { id: Tab; label: string; icon: React.ReactNode }[] = [
    { id: "profile", label: t("Profile"), icon: <UserRound className="h-4 w-4" /> },
    { id: "preferences", label: t("Language & region"), icon: <Globe className="h-4 w-4" /> },
    { id: "password", label: t("Password"), icon: <KeyRound className="h-4 w-4" /> },
  ];

  return (
    <div className="max-w-xl">
      <h1 className="text-2xl font-semibold tracking-tight">{t("Account settings")}</h1>
      <p className="mt-1 text-fg/50">{t("Your sign-in details, and how Orbit looks for you.")}</p>

      {forced && (
        <Alert tone="info" className="mt-6">
          <ShieldCheck className="mr-1 inline h-4 w-4" />
          {t("Welcome to Orbit! You signed in with a temporary password. Choose your own to continue.")}
        </Alert>
      )}

      <div role="tablist" className="mt-6 flex gap-1 border-b border-fg/10">
        {tabs.map((x) => (
          <button key={x.id} role="tab" aria-selected={tab === x.id} disabled={forced && x.id !== "password"}
            onClick={() => setTab(x.id)}
            className={"-mb-px inline-flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm transition-colors disabled:opacity-40 "
              + (tab === x.id ? "border-violet font-medium text-fg" : "border-transparent text-fg/50 hover:text-fg")}>
            {x.icon}{x.label}
          </button>
        ))}
      </div>

      {tab === "profile" && <Card className="mt-6">
        <CardTitle>{t("Signed in as")}</CardTitle>
        <dl className="mt-3 grid grid-cols-[8rem_1fr] gap-y-2 text-sm">
          <dt className="text-fg/50">{t("Name")}</dt><dd>{me.user.firstName} {me.user.lastName}</dd>
          <dt className="text-fg/50">{t("Email")}</dt><dd>{me.user.email}</dd>
          <dt className="text-fg/50">{t("Company")}</dt><dd>{me.company.name}</dd>
          <dt className="text-fg/50">{t("Role")}</dt><dd className="capitalize">{me.user.role.toLowerCase()}</dd>
        </dl>
      </Card>}
      {tab === "preferences" && <PreferencesCard />}
      {tab === "password" && <PasswordCard />}
    </div>
  );
}

/** Language, timezone and date/time style — this person's own, saved to their account (V68). */
function PreferencesCard() {
  const session = useSession();
  const me = session.me!;
  const t = useT();
  const prefs = me.user.preferences;

  // English unless this person picked otherwise.
  const [language, setLanguage] = useState<string>(prefs?.language ?? me.language ?? DEFAULT_LANGUAGE);
  const [timezone, setTimezone] = useState<string>(prefs?.timezone ?? "");
  const [dateFormat, setDateFormat] = useState<DateFormatPref | "">(prefs?.dateFormat ?? "");
  const [timeFormat, setTimeFormat] = useState<TimeFormatPref | "">(prefs?.timeFormat ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  // Labels in the language being chosen, so the person sees what they are about to get.
  const tt = (text: string, values?: Record<string, string | number>) => translate(language, text, values);
  const now = new Date();
  // India time unless the company or this person chose another zone.
  const companyZone = me.company.timezone || "Asia/Kolkata";
  const zone = timezone || companyZone;
  const preview = (d: DateFormatPref | null, h: TimeFormatPref | null) =>
    withLocale({ language, timezone: zone, dateFormat: d, timeFormat: h }, () => ({ date: formatDate(now), time: formatTime(now) }));
  const example = preview(dateFormat || null, timeFormat || null);
  const otherZones = TIMEZONES.filter((z) => !COMMON_TIMEZONES.includes(z));

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSaved(false);
    setBusy(true);
    try {
      const next = await api.updatePreferences({
        language, timezone: timezone || null, dateFormat: dateFormat || null, timeFormat: timeFormat || null,
      });
      session.setMe(next);
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("Couldn't save your preferences"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card id="preferences" className="mt-6 scroll-mt-24">
      <CardTitle className="flex items-center gap-2"><Globe className="h-4 w-4 text-violet" /> {tt("Language & region")}</CardTitle>
      <p className="mt-1 text-sm text-fg/60">
        {tt("Choose the language, timezone and date style Orbit uses for you. Only your own account changes.")}
      </p>

      {saved && <Alert tone="success" className="mt-4">{t("Preferences saved.")}</Alert>}
      {error && <Alert tone="error" className="mt-4">{error}</Alert>}

      <form onSubmit={save} className="mt-5 flex flex-col gap-4">
        <Field label={tt("Language")} htmlFor="language">
          <select id="language" value={language} onChange={(e) => { setLanguage(e.target.value); setSaved(false); }} className={SELECT}>
            {LANGUAGES.map((l) => <option key={l.code} value={l.code} className="bg-surface">{l.label}</option>)}
          </select>
        </Field>
        {language !== "en" && (
          <p className="-mt-2 text-xs text-fg/50">
            {tt("Menus and account screens are translated; other screens are being translated and show English until then.")}
          </p>
        )}

        <Field label={tt("Timezone")} htmlFor="timezone"
          hint={tt("Your timezone also sets the clock your attendance is recorded in.")}>
          <select id="timezone" value={timezone} onChange={(e) => { setTimezone(e.target.value); setSaved(false); }} className={SELECT}>
            <option value="" className="bg-surface">{tt("{zone} (default)", { zone: zoneLabel(companyZone) })}</option>
            <optgroup label={tt("Common timezones")}>
              {COMMON_TIMEZONES.map((z) => <option key={z} value={z} className="bg-surface">{zoneLabel(z)}</option>)}
            </optgroup>
            <optgroup label={tt("All timezones")}>
              {otherZones.map((z) => <option key={z} value={z} className="bg-surface">{zoneLabel(z)}</option>)}
            </optgroup>
          </select>
        </Field>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={tt("Date format")} htmlFor="dateFormat">
            <select id="dateFormat" value={dateFormat} onChange={(e) => { setDateFormat(e.target.value as DateFormatPref | ""); setSaved(false); }} className={SELECT}>
              <option value="" className="bg-surface">{tt("Same as the language ({example})", { example: preview(null, null).date })}</option>
              <option value="DMY" className="bg-surface">DD/MM/YYYY ({preview("DMY", null).date})</option>
              <option value="MDY" className="bg-surface">MM/DD/YYYY ({preview("MDY", null).date})</option>
              <option value="YMD" className="bg-surface">YYYY-MM-DD ({preview("YMD", null).date})</option>
            </select>
          </Field>
          <Field label={tt("Time format")} htmlFor="timeFormat">
            <select id="timeFormat" value={timeFormat} onChange={(e) => { setTimeFormat(e.target.value as TimeFormatPref | ""); setSaved(false); }} className={SELECT}>
              <option value="" className="bg-surface">{tt("Same as the language ({example})", { example: preview(null, null).time })}</option>
              <option value="H12" className="bg-surface">{tt("12-hour ({example})", { example: preview(null, "H12").time })}</option>
              <option value="H24" className="bg-surface">{tt("24-hour ({example})", { example: preview(null, "H24").time })}</option>
            </select>
          </Field>
        </div>

        <div className="rounded-lg border border-fg/10 bg-fg/[0.02] px-3 py-2 text-sm">
          <span className="text-fg/50">{tt("Preview")}:</span> {example.date}, {example.time}
        </div>

        <div>
          <Button type="submit" disabled={busy}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />}
            {tt("Save preferences")}
          </Button>
        </div>
      </form>
    </Card>
  );
}

function PasswordCard() {
  const session = useSession();
  const router = useRouter();
  const t = useT();
  const forced = !!session.me?.user.mustChangePassword;

  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const longEnough = next.length >= 10;
  const hasLetter = /[A-Za-z]/.test(next);
  const hasNumber = /\d/.test(next);
  const matches = next.length > 0 && next === confirm;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!matches) {
      setError(t("The two new passwords don't match."));
      return;
    }
    setBusy(true);
    try {
      const result = await api.changePassword(current, next);
      session.setMe(result.me);
      setDone(true);
      setCurrent(""); setNext(""); setConfirm("");
      if (forced) router.replace("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("Couldn't change your password"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card id="password" className="mt-6 scroll-mt-24">
      <CardTitle className="flex items-center gap-2"><KeyRound className="h-4 w-4 text-violet" /> {t("Change password")}</CardTitle>
      <p className="mt-1 text-sm text-fg/60">
        {t("You'll stay signed in here; every other device is signed out.")}
      </p>

      {done && !forced && <Alert tone="success" className="mt-4">{t("Password changed.")}</Alert>}
      {error && <Alert tone="error" className="mt-4">{error}</Alert>}

      <form onSubmit={submit} className="mt-5 flex flex-col gap-4">
        <Field label={forced ? t("Temporary password (from your welcome email)") : t("Current password")} htmlFor="current">
          <PasswordInput id="current" autoComplete="current-password" required value={current}
            onChange={(e) => setCurrent(e.target.value)} />
        </Field>
        <Field label={t("New password")} htmlFor="next">
          <PasswordInput id="next" autoComplete="new-password" required value={next}
            onChange={(e) => setNext(e.target.value)} />
        </Field>
        <ul className="-mt-2 space-y-0.5 text-xs">
          <Rule ok={longEnough}>{t("At least 10 characters")}</Rule>
          <Rule ok={hasLetter}>{t("A letter")}</Rule>
          <Rule ok={hasNumber}>{t("A number")}</Rule>
        </ul>
        <Field label={t("Confirm new password")} htmlFor="confirm">
          <PasswordInput id="confirm" autoComplete="new-password" required value={confirm}
            onChange={(e) => setConfirm(e.target.value)} />
        </Field>
        <div>
          <Button type="submit" disabled={busy || !longEnough || !hasLetter || !hasNumber || !matches || !current}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />}
            {forced ? t("Set my password") : t("Change password")}
          </Button>
        </div>
      </form>
    </Card>
  );
}

function Rule({ ok, children }: { ok: boolean; children: React.ReactNode }) {
  return <li className={ok ? "text-emerald-500" : "text-fg/40"}>{ok ? "✓" : "•"} {children}</li>;
}
