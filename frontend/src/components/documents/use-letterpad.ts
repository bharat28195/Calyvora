"use client";

import { useEffect, useState } from "react";
import { api, type LetterpadSheet } from "@/lib/api";

/**
 * The uploaded letterpad (or, PD-69, its continuation sheet) as an image URL the browser can draw, or
 * null until it has loaded (or when there is none). Fetched with the session token — see
 * {@code api.letterpadImage}.
 */
export function useLetterpadImage(
  version: string | null | undefined,
  enabled: boolean,
  sheet: LetterpadSheet = "background",
): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!enabled || !version) {
      setUrl(null);
      return;
    }
    let live = true;
    api.letterpadImage(version, sheet).then((u) => { if (live) setUrl(u); }).catch(() => { if (live) setUrl(null); });
    return () => { live = false; };
  }, [version, enabled, sheet]);
  return url;
}
