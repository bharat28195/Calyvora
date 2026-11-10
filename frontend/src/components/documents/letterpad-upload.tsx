"use client";

import { useRef, useState } from "react";
import { Loader2, RotateCcw, Trash2, Upload } from "lucide-react";
import { api, ApiError, type LetterpadSheet } from "@/lib/api";
import type { Letterhead } from "@/lib/types";
import { useLetterpadImage } from "@/components/documents/use-letterpad";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/**
 * Upload the company's own letterpad and print letters on it as-is.
 *
 * <p>The designer beside this composes stationery from fields — logo, heading, address, colour —
 * which is the right answer for a company that does not have any. Most do: their printer produced a
 * letterpad years ago, it is what their contracts and their bank forms go out on, and what they
 * want is for Orbit's letters to match it rather than to be a second, nearly-identical design.
 *
 * <p>PD-69: a letter runs to as many pages as it needs. Page one prints on the letterpad, page two
 * onwards on the continuation sheet — page 2 of the uploaded file, a sheet uploaded on its own, or the
 * letterpad with its header cleared. The writing area on each is measured on upload, shown here as a
 * dashed box, and can be adjusted; the page's Save button stores the adjustment.
 *
 * <p>Uploading switches it on, because uploading is the act of choosing it. The switch stays
 * separate so an internal memo can be printed plain without deleting the file.
 */

// PDF and Word too since PD-64: the server turns the pages into the images letters print on.
const ACCEPT = ".pdf,.docx,image/png,image/jpeg,image/webp,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document";
const MAX_MB = 10;

type AreaKey = "firstTopMm" | "firstBottomMm" | "laterTopMm" | "laterBottomMm" | "sideMm";

export function LetterpadUpload({
    letterhead,
    onChange,
    onDraft,
}: {
    letterhead: Letterhead;
    /** A saved change: the server's answer. */
    onChange: (next: Letterhead) => void;
    /** An unsaved change to the writing area, previewed at once and stored by the page's Save. */
    onDraft: (patch: Partial<Letterhead>) => void;
}) {
    const input = useRef<HTMLInputElement>(null);
    const [target, setTarget] = useState<LetterpadSheet>("background");
    const [busy, setBusy] = useState(false);
    const first = useLetterpadImage(letterhead.updatedAt, letterhead.hasBackground, "background");
    const later = useLetterpadImage(letterhead.updatedAt, letterhead.hasBackground, "continuation");
    const [error, setError] = useState<string | null>(null);

    function pick(sheet: LetterpadSheet) {
        setTarget(sheet);
        input.current?.click();
    }

    async function upload(file: File | undefined) {
        if (!file) return;
        setError(null);
        // Checked here as well as on the server: a limit that only announces itself after the file
        // has been sent is a slow way to say no, especially on an Indian office connection.
        if (file.size > MAX_MB * 1024 * 1024) {
            setError(`That file is ${(file.size / 1024 / 1024).toFixed(1)} MB. Keep it under ${MAX_MB} MB.`);
            return;
        }
        setBusy(true);
        try {
            onChange(await api.uploadLetterpad(file, target));
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "That upload did not work.");
        } finally {
            setBusy(false);
            if (input.current) input.current.value = "";   // so the same file can be picked again
        }
    }

    async function run(action: () => Promise<Letterhead>, failure: string) {
        setBusy(true);
        setError(null);
        try {
            onChange(await action());
        } catch (e) {
            setError(e instanceof ApiError ? e.message : failure);
        } finally {
            setBusy(false);
        }
    }

    const sameEverywhere = letterhead.laterPages === "SAME";
    const source = letterhead.continuationSource;

    return (
        <Card>
            <CardTitle>Upload your letterpad</CardTitle>
            <p className="mt-1 text-xs text-fg/50">
                Already have company stationery? Upload it and letters print straight onto it, on as many
                pages as they need.
            </p>

            {error && <Alert tone="error" className="mt-3">{error}</Alert>}

            {letterhead.hasBackground ? (
                <div className="mt-4">
                    <div className="grid grid-cols-2 gap-3">
                        <Sheet label="Page 1" image={first}
                            top={letterhead.firstTopMm ?? 40} bottom={letterhead.firstBottomMm ?? 32}
                            side={letterhead.sideMm ?? 22} />
                        <Sheet label="Page 2 onwards" image={sameEverywhere ? first : later}
                            top={letterhead.laterTopMm ?? 22} bottom={letterhead.laterBottomMm ?? 32}
                            side={letterhead.sideMm ?? 22} />
                    </div>
                    <p className="mt-2 truncate text-xs text-fg/40">{letterhead.backgroundName}</p>

                    <label className="mt-3 flex items-center gap-2 text-sm text-fg/70">
                        <input
                            type="checkbox"
                            checked={letterhead.useBackground}
                            disabled={busy}
                            onChange={(e) => void run(() => api.saveLetterhead({ useBackground: e.target.checked }), "That could not be saved.")}
                            className="h-4 w-4 rounded border-fg/20 bg-fg/5"
                        />
                        Print letters on this
                    </label>

                    <div className="mt-4 rounded-lg border border-fg/10 p-3">
                        <p className="text-sm font-medium">Page 2 onwards</p>
                        <div className="mt-2 flex flex-col gap-1.5 text-sm text-fg/70">
                            <label className="flex items-center gap-2">
                                <input type="radio" name="later" checked={!sameEverywhere} disabled={busy}
                                    onChange={() => void run(() => api.saveLetterhead({ laterPages: "CONTINUATION" }), "That could not be saved.")} />
                                Continuation sheet
                                <span className="text-xs text-fg/40">
                                    {source === "PAGE2" ? "· page 2 of your file"
                                        : source === "UPLOADED" ? "· uploaded separately"
                                        : "· your letterpad without the header"}
                                </span>
                            </label>
                            <label className="flex items-center gap-2">
                                <input type="radio" name="later" checked={sameEverywhere} disabled={busy}
                                    onChange={() => void run(() => api.saveLetterhead({ laterPages: "SAME" }), "That could not be saved.")} />
                                The full letterpad on every page
                            </label>
                        </div>
                        {!sameEverywhere && (
                            <div className="mt-2 flex flex-wrap gap-2">
                                <Button variant="secondary" size="sm" disabled={busy} onClick={() => pick("continuation")}>
                                    <Upload className="h-4 w-4" /> Upload a continuation sheet
                                </Button>
                                {source === "UPLOADED" && (
                                    <Button variant="ghost" size="sm" disabled={busy}
                                        onClick={() => void run(() => api.removeLetterpad("continuation"), "That could not be removed.")}>
                                        Use page 1 without the header
                                    </Button>
                                )}
                            </div>
                        )}
                    </div>

                    <div className="mt-4 rounded-lg border border-fg/10 p-3">
                        <div className="flex items-center justify-between gap-2">
                            <p className="text-sm font-medium">Where the letter is written</p>
                            <Button variant="ghost" size="sm" disabled={busy}
                                onClick={() => void run(() => api.saveLetterhead({ remeasure: true }), "That could not be measured.")}>
                                <RotateCcw className="h-3.5 w-3.5" /> Measure again
                            </Button>
                        </div>
                        <p className="mt-1 text-xs text-fg/45">
                            Found automatically from your letterpad: the dashed box above. Space in millimetres from
                            the edge of the paper; Save at the top keeps a change.
                        </p>
                        <div className="mt-3 grid grid-cols-3 gap-2 text-xs">
                            <span />
                            <span className="text-fg/50">Top</span>
                            <span className="text-fg/50">Bottom</span>
                            <span className="self-center text-fg/60">Page 1</span>
                            <Mm value={letterhead.firstTopMm} k="firstTopMm" onDraft={onDraft} />
                            <Mm value={letterhead.firstBottomMm} k="firstBottomMm" onDraft={onDraft} />
                            <span className="self-center text-fg/60">Page 2 on</span>
                            <Mm value={letterhead.laterTopMm} k="laterTopMm" onDraft={onDraft} disabled={sameEverywhere} />
                            <Mm value={letterhead.laterBottomMm} k="laterBottomMm" onDraft={onDraft} disabled={sameEverywhere} />
                            <span className="self-center text-fg/60">Left &amp; right</span>
                            <Mm value={letterhead.sideMm} k="sideMm" onDraft={onDraft} />
                        </div>
                    </div>

                    <div className="mt-4 flex gap-2">
                        <Button variant="secondary" size="sm" disabled={busy} onClick={() => pick("background")}>
                            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                            Replace
                        </Button>
                        <Button variant="ghost" size="sm" disabled={busy}
                            onClick={() => void run(() => api.removeLetterpad(), "That could not be removed.")}>
                            <Trash2 className="h-4 w-4" /> Remove
                        </Button>
                    </div>
                </div>
            ) : (
                <div className="mt-4">
                    <button
                        type="button"
                        disabled={busy}
                        onClick={() => pick("background")}
                        className="flex w-full flex-col items-center gap-2 rounded-lg border border-dashed border-fg/20 px-4 py-8 text-center transition-colors hover:border-violet/50 hover:bg-fg/[0.02] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet disabled:opacity-50"
                    >
                        {busy
                            ? <Loader2 className="h-6 w-6 animate-spin text-violet" />
                            : <Upload className="h-6 w-6 text-fg/30" />}
                        <span className="text-sm font-medium">{busy ? "Uploading…" : "Choose a file"}</span>
                        <span className="text-xs text-fg/40">
                            PDF, Word (.docx) or an image · up to {MAX_MB} MB · A4 portrait
                        </span>
                    </button>
                    <p className="mt-2 text-xs text-fg/40">
                        Upload the file your printer or designer gave you. If it has a second page, that becomes
                        the sheet for page 2 onwards; if not, page 1 is used without its header.
                    </p>
                </div>
            )}

            <input
                ref={input}
                type="file"
                accept={ACCEPT}
                hidden
                onChange={(e) => void upload(e.target.files?.[0])}
            />
        </Card>
    );
}

/** One sheet at A4 proportions, with its writing area drawn as a dashed box. */
function Sheet({ label, image, top, bottom, side }: {
    label: string; image: string | null; top: number; bottom: number; side: number;
}) {
    return (
        <div>
            <div className="relative overflow-hidden rounded-md border border-fg/10 bg-white" style={{ aspectRatio: "210 / 297" }}>
                {image && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={image} alt="" className="absolute inset-0 h-full w-full" />
                )}
                <div
                    className="absolute border border-dashed border-violet/70 bg-violet/5"
                    style={{
                        top: `${(top / 297) * 100}%`,
                        bottom: `${(bottom / 297) * 100}%`,
                        left: `${(side / 210) * 100}%`,
                        right: `${(side / 210) * 100}%`,
                    }}
                />
            </div>
            <p className="mt-1 text-center text-xs text-fg/50">{label}</p>
        </div>
    );
}

function Mm({ value, k, onDraft, disabled }: {
    value: number | undefined; k: AreaKey; onDraft: (patch: Partial<Letterhead>) => void; disabled?: boolean;
}) {
    return (
        <input
            type="number"
            min={5}
            max={k === "sideMm" ? 50 : 180}
            value={value ?? ""}
            disabled={disabled}
            onChange={(e) => {
                const n = Number(e.target.value);
                if (Number.isFinite(n) && n > 0) onDraft({ [k]: Math.round(n) } as Partial<Letterhead>);
            }}
            className="w-full rounded-md border border-fg/15 bg-fg/[0.03] px-2 py-1 text-sm disabled:opacity-40"
        />
    );
}
