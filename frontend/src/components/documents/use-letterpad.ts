"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";

/**
 * The uploaded letterpad as an image URL the browser can draw, or null until it has loaded (or when
 * there is none). Fetched with the session token — see {@code api.letterpadImage}.
 */
export function useLetterpadImage(version: string | null | undefined, enabled: boolean): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!enabled || !version) {
      setUrl(null);
      return;
    }
    let live = true;
    api.letterpadImage(version).then((u) => { if (live) setUrl(u); }).catch(() => { if (live) setUrl(null); });
    return () => { live = false; };
  }, [version, enabled]);
  return url;
}
