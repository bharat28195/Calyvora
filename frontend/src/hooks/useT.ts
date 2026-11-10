"use client";

import { useCallback } from "react";
import { useSession } from "@/hooks/useSession";
import { translate, DEFAULT_LANGUAGE } from "@/lib/i18n";

/**
 * `t("Log out")` in the signed-in person's language (V68). English until they choose otherwise, and
 * English for anything not translated yet.
 */
export function useT() {
  const { me } = useSession();
  const language = me?.language ?? DEFAULT_LANGUAGE;
  return useCallback(
    (text: string, values?: Record<string, string | number>) => translate(language, text, values),
    [language],
  );
}
