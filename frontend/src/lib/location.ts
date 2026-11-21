/**
 * Location at check-in (PD-81). The browser's own geolocation — which the Android/iOS app shell
 * also provides, behind the phone's normal permission prompt — read only at the moment someone
 * checks in or out. Nothing here watches position in the background.
 */

export type CheckinLocationMode = "OFF" | "RECORD" | "ENFORCE";

export type Fix = { latitude: number; longitude: number; accuracy: number };

export type Office = { id: string; name: string; latitude: number; longitude: number; radiusM: number };

export type CheckinLocationSettings = { mode: CheckinLocationMode; offices: Office[] };

export type OfficeInput = { name: string; latitude: number; longitude: number; radiusM: number };

/** One check-in/out session with where it happened, for the team day sheet. */
export type LocationSpot = {
  employeeId: string;
  checkIn: string | null;
  checkOut: string | null;
  inLatitude: number | null; inLongitude: number | null; inAccuracyM: number | null;
  inOffice: string | null; inDistanceM: number | null;
  outLatitude: number | null; outLongitude: number | null; outAccuracyM: number | null;
  outOffice: string | null; outDistanceM: number | null;
};

/** Plain words for why location could not be read — said on the check-in card. */
export class LocationError extends Error {}

/**
 * Ask the device where it is, once. High accuracy, because "inside a 200 m office radius" needs GPS,
 * not a cell tower guess; a fresh reading, because a cached one from home would check someone in
 * from the wrong place.
 */
export function currentFix(timeoutMs = 15000): Promise<Fix> {
  return new Promise((resolve, reject) => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      reject(new LocationError("This device can't share its location."));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ latitude: p.coords.latitude, longitude: p.coords.longitude, accuracy: p.coords.accuracy }),
      (err) => reject(new LocationError(
        err.code === err.PERMISSION_DENIED
          ? "Location is blocked for Orbit. Allow it in your phone or browser settings, then try again."
          : err.code === err.TIMEOUT
            ? "Couldn't get a GPS fix in time. Step near a window or outside and try again."
            : "Couldn't read your location. Check that location is turned on.")),
      { enableHighAccuracy: true, timeout: timeoutMs, maximumAge: 0 },
    );
  });
}

export function distanceLabel(m: number | null | undefined): string {
  if (m == null) return "";
  return m >= 1000 ? `${(m / 1000).toFixed(1)} km` : `${m} m`;
}

export function mapLink(lat: number, lng: number): string {
  return `https://www.google.com/maps?q=${lat},${lng}`;
}
