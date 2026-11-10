"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Letterhead, Signatory } from "@/lib/types";

/** The signature and seal drawn at a letter's [[signature]] line, as object URLs (PD-71). */
export interface SignatureMarks {
  signature: string | null;
  seal: string | null;
}

const NONE: SignatureMarks = { signature: null, seal: null };

/**
 * Loads the signatory's signature and the company seal for a letter. Both are fetched with the
 * session token (the API reads it from the Authorization header only, which an <img> cannot send).
 */
export function useSignatureMarks(signatoryId: string | null | undefined, letterhead: Letterhead | null | undefined,
                                  signatories?: Signatory[] | null): SignatureMarks {
  const [marks, setMarks] = useState<SignatureMarks>(NONE);
  const [list, setList] = useState<Signatory[] | null>(signatories ?? null);

  useEffect(() => {
    if (signatories) { setList(signatories); return; }
    if (!signatoryId) return;
    api.signatories().then(setList).catch(() => setList([]));
  }, [signatoryId, signatories]);

  useEffect(() => {
    let live = true;
    const who = list?.find((s) => s.id === signatoryId);
    const sig = who?.hasImage ? api.markImage("signature", who.id, who.updatedAt).catch(() => null) : Promise.resolve(null);
    const seal = letterhead?.hasSeal ? api.markImage("seal", "seal", letterhead.updatedAt).catch(() => null) : Promise.resolve(null);
    Promise.all([sig, seal]).then(([s, l]) => { if (live) setMarks({ signature: s, seal: l }); });
    return () => { live = false; };
  }, [list, signatoryId, letterhead?.hasSeal, letterhead?.updatedAt]);

  return marks;
}
