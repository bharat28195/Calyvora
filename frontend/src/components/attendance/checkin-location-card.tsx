"use client";

import { useEffect, useState } from "react";
import { Crosshair, Loader2, MapPin, Trash2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { currentFix, LocationError, mapLink, type CheckinLocationMode, type Office } from "@/lib/location";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";

const MODES: { value: CheckinLocationMode; label: string; help: string }[] = [
  { value: "OFF", label: "Off", help: "Location is never asked for or stored." },
  { value: "RECORD", label: "Record", help: "Location is saved at check-in and check-out and shown on the team day sheet. Nobody is stopped." },
  { value: "ENFORCE", label: "Office only", help: "Check-in and check-out only work inside one of the offices below. With no offices added, it behaves like Record." },
];

/**
 * Location at check-in (PD-81): the mode, and the offices that count as "at work". Adding an office
 * is easiest standing in it — "Use my location" fills in the point.
 */
export function CheckinLocationCard() {
  const [mode, setMode] = useState<CheckinLocationMode>("OFF");
  const [offices, setOffices] = useState<Office[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [locating, setLocating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [lat, setLat] = useState("");
  const [lng, setLng] = useState("");
  const [radius, setRadius] = useState("200");

  useEffect(() => {
    api.checkinLocation()
      .then((s) => { setMode(s.mode); setOffices(s.offices); })
      .catch(() => {})
      .finally(() => setLoaded(true));
  }, []);

  async function changeMode(next: CheckinLocationMode) {
    setBusy(true); setError(null);
    try {
      const s = await api.setCheckinLocationMode(next);
      setMode(s.mode);
    } catch (e) { setError(e instanceof ApiError ? e.message : "Couldn't change that"); }
    finally { setBusy(false); }
  }

  async function useMyLocation() {
    setLocating(true); setError(null);
    try {
      const f = await currentFix();
      setLat(f.latitude.toFixed(6));
      setLng(f.longitude.toFixed(6));
    } catch (e) { setError(e instanceof LocationError ? e.message : "Couldn't read your location"); }
    finally { setLocating(false); }
  }

  async function add(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      const o = await api.addOffice({
        name: name.trim(), latitude: Number(lat), longitude: Number(lng), radiusM: Math.round(Number(radius)),
      });
      setOffices((list) => [...list, o]);
      setName(""); setLat(""); setLng(""); setRadius("200");
    } catch (err) { setError(err instanceof ApiError ? err.message : "Couldn't add the office"); }
    finally { setBusy(false); }
  }

  async function remove(id: string) {
    setBusy(true); setError(null);
    try {
      await api.deleteOffice(id);
      setOffices((list) => list.filter((o) => o.id !== id));
    } catch (err) { setError(err instanceof ApiError ? err.message : "Couldn't remove the office"); }
    finally { setBusy(false); }
  }

  const current = MODES.find((m) => m.value === mode)!;

  return (
    <Card className="mt-6">
      <CardTitle>Location at check-in</CardTitle>
      <p className="mt-1 text-sm text-fg/60">
        The Orbit app (and browsers that allow it) can share where someone is at the moment they check in
        or out. Only those two moments are recorded — nobody is tracked during the day.
      </p>

      <div className="mt-4 inline-flex rounded-lg border border-fg/10 p-0.5" role="radiogroup" aria-label="Location mode">
        {MODES.map((m) => (
          <button key={m.value} type="button" role="radio" aria-checked={mode === m.value}
            disabled={busy || !loaded} onClick={() => changeMode(m.value)}
            className={`rounded-md px-3 py-1.5 text-sm ${mode === m.value ? "bg-violet text-white" : "text-fg/60 hover:text-fg"}`}>
            {m.label}
          </button>
        ))}
      </div>
      <p className="mt-2 text-xs text-fg/50">{current.help}</p>

      {error && <Alert tone="error" className="mt-3">{error}</Alert>}

      {mode !== "OFF" && (
        <>
          <h3 className="mt-6 text-sm font-medium">Offices</h3>
          {offices.length === 0 && <p className="mt-1 text-sm text-fg/50">No offices yet.</p>}
          <div className="mt-2 flex flex-col gap-2">
            {offices.map((o) => (
              <div key={o.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-fg/10 px-3 py-2">
                <div className="min-w-0">
                  <p className="text-sm font-medium">{o.name}</p>
                  <a href={mapLink(o.latitude, o.longitude)} target="_blank" rel="noreferrer"
                    className="inline-flex items-center gap-1 text-xs text-fg/50 hover:text-violet">
                    <MapPin className="h-3 w-3" /> {o.latitude.toFixed(5)}, {o.longitude.toFixed(5)} · within {o.radiusM} m
                  </a>
                </div>
                <button onClick={() => remove(o.id)} disabled={busy} aria-label={`Remove ${o.name}`}
                  className="rounded-md p-1.5 text-fg/40 hover:bg-fg/5 hover:text-red-400">
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            ))}
          </div>

          <form onSubmit={add} className="mt-4 grid gap-3 sm:grid-cols-6">
            <Field label="Office name" htmlFor="ol-name" className="sm:col-span-2">
              <Input id="ol-name" required maxLength={120} value={name} onChange={(e) => setName(e.target.value)}
                placeholder="Head office" />
            </Field>
            <Field label="Latitude" htmlFor="ol-lat">
              <Input id="ol-lat" required inputMode="decimal" value={lat} onChange={(e) => setLat(e.target.value)} />
            </Field>
            <Field label="Longitude" htmlFor="ol-lng">
              <Input id="ol-lng" required inputMode="decimal" value={lng} onChange={(e) => setLng(e.target.value)} />
            </Field>
            <Field label="Radius (m)" htmlFor="ol-radius">
              <Input id="ol-radius" required type="number" min="25" max="5000" value={radius}
                onChange={(e) => setRadius(e.target.value)} />
            </Field>
            <div className="flex items-end gap-2">
              <Button type="submit" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Add</Button>
            </div>
          </form>
          <button type="button" onClick={useMyLocation} disabled={locating}
            className="mt-2 inline-flex items-center gap-1.5 text-sm text-violet hover:underline">
            {locating ? <Loader2 className="h-4 w-4 animate-spin" /> : <Crosshair className="h-4 w-4" />}
            Use my current location
          </button>
        </>
      )}
    </Card>
  );
}
