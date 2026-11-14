"use client";

import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { forgetFeatures } from "@/hooks/useFeatures";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";

/**
 * The company's working day, for everyone not rostered onto a shift: when it starts, how many hours
 * of work it asks for, and how long after the start a person with no check-in counts as absent.
 * A day short of the hours becomes a half day, which payroll pays as half. Managers and HR can still
 * correct any day by hand.
 */
export function WorkDayCard() {
  const [start, setStart] = useState("09:30");
  const [workHours, setWorkHours] = useState("9");
  const [graceHours, setGraceHours] = useState("2");
  const [track, setTrack] = useState(true);
  const [trackFrom, setTrackFrom] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.workDay().then((p) => {
      setStart(p.workDayStart.slice(0, 5));
      setWorkHours(String(p.workDayMinutes / 60));
      setGraceHours(String(p.absentGraceMinutes / 60));
      setTrack(p.trackAttendance ?? true);
      setTrackFrom(p.trackAttendanceFrom ?? null);
    }).catch(() => {}).finally(() => setLoaded(true));
  }, []);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null); setSaved(false);
    try {
      const out = await api.setWorkDay({
        workDayStart: start,
        workDayMinutes: Math.round(Number(workHours) * 60),
        absentGraceMinutes: Math.round(Number(graceHours) * 60),
        trackAttendance: track,
      });
      setTrackFrom(out.trackAttendanceFrom ?? null);
      // The menu shows or hides check-in from this; the next page load reads it fresh.
      forgetFeatures();
      setSaved(true);
    } catch (err) { setError(err instanceof ApiError ? err.message : "Couldn't save the working day"); }
    finally { setBusy(false); }
  }

  return (
    <Card className="mt-6">
      <CardTitle>Working day and attendance</CardTitle>
      <p className="mt-1 text-sm text-fg/60">
        For everyone not on a shift. No check-in by the start plus the grace means absent; fewer hours
        than required means a half day.
      </p>
      <label className="mt-4 flex items-start gap-3 text-sm">
        <input type="checkbox" className="mt-0.5 h-4 w-4" checked={track}
          onChange={(e) => { setTrack(e.target.checked); setSaved(false); }} />
        <span>
          <span className="font-medium">Track attendance in Orbit</span>
          <span className="block text-fg/60">
            {track
              ? (trackFrom ? `People check in here, and the absence and half-day rules apply from ${trackFrom}.` : "People check in here, and the absence and half-day rules apply from the day you save.")
              : "Off: nobody checks in here and nobody is marked absent by the clock. Payroll pays every working day, less approved unpaid leave and any absence HR marks by hand."}
          </span>
        </span>
      </label>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      {saved && <Alert tone="success" className="mt-3">Saved.</Alert>}
      <form onSubmit={save} className="mt-4 grid gap-3 sm:grid-cols-4">
        <Field label="Day starts" htmlFor="wd-start">
          <Input id="wd-start" type="time" value={start} onChange={(e) => { setStart(e.target.value); setSaved(false); }} />
        </Field>
        <Field label="Required hours" htmlFor="wd-hours">
          <Input id="wd-hours" type="number" min="0.5" max="24" step="0.5" value={workHours}
            onChange={(e) => { setWorkHours(e.target.value); setSaved(false); }} />
        </Field>
        <Field label="Absent after (hours)" htmlFor="wd-grace">
          <Input id="wd-grace" type="number" min="0" max="12" step="0.25" value={graceHours}
            onChange={(e) => { setGraceHours(e.target.value); setSaved(false); }} />
        </Field>
        <div className="flex items-end">
          <Button type="submit" disabled={busy || !loaded}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Save</Button>
        </div>
      </form>
    </Card>
  );
}
