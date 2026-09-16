"use client";

import { useRef, useState } from "react";
import { Loader2, Trash2, Upload } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Letterhead } from "@/lib/types";
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
 * <p>Uploading switches it on, because uploading is the act of choosing it. The switch stays
 * separate so an internal memo can be printed plain without deleting the file.
 */

const ACCEPT = "image/png,image/jpeg,image/webp";
const MAX_MB = 2;

export function LetterpadUpload({
    letterhead,
    onChange,
}: {
    letterhead: Letterhead;
    onChange: (next: Letterhead) => void;
}) {
    const input = useRef<HTMLInputElement>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    async function upload(file: File | undefined) {
        if (!file) return;
        setError(null);
        // Checked here as well as on the server: a 2 MB limit that only announces itself after the
        // file has been sent is a slow way to say no, especially on an Indian office connection.
        if (file.size > MAX_MB * 1024 * 1024) {
            setError(`That file is ${(file.size / 1024 / 1024).toFixed(1)} MB. Keep it under ${MAX_MB} MB — export at 150 dpi rather than 300.`);
            return;
        }
        setBusy(true);
        try {
            onChange(await api.uploadLetterpad(file));
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "That upload did not work.");
        } finally {
            setBusy(false);
            if (input.current) input.current.value = "";   // so the same file can be picked again
        }
    }

    async function remove() {
        setBusy(true);
        setError(null);
        try {
            onChange(await api.removeLetterpad());
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "That could not be removed.");
        } finally {
            setBusy(false);
        }
    }

    async function toggle(useBackground: boolean) {
        setBusy(true);
        setError(null);
        try {
            onChange(await api.saveLetterhead({ useBackground }));
        } catch (e) {
            setError(e instanceof ApiError ? e.message : "That could not be saved.");
        } finally {
            setBusy(false);
        }
    }

    return (
        <Card>
            <CardTitle>Upload your letterpad</CardTitle>
            <p className="mt-1 text-xs text-fg/50">
                Already have company stationery? Upload it and letters print straight onto it — no need to
                rebuild it below.
            </p>

            {error && <Alert tone="error" className="mt-3">{error}</Alert>}

            {letterhead.hasBackground ? (
                <div className="mt-4">
                    <div className="overflow-hidden rounded-lg border border-fg/10 bg-white">
                        {/* The real thing, at A4 proportions, so what is checked here is what prints. */}
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                            src={api.letterpadImageUrl(letterhead.updatedAt)}
                            alt="The uploaded company letterpad"
                            className="block w-full"
                        />
                    </div>
                    <p className="mt-2 truncate text-xs text-fg/40">{letterhead.backgroundName}</p>

                    <label className="mt-3 flex items-center gap-2 text-sm text-fg/70">
                        <input
                            type="checkbox"
                            checked={letterhead.useBackground}
                            disabled={busy}
                            onChange={(e) => void toggle(e.target.checked)}
                            className="h-4 w-4 rounded border-fg/20 bg-fg/5"
                        />
                        Print letters on this
                    </label>
                    <p className="mt-1 text-xs text-fg/40">
                        {letterhead.useBackground
                            ? "The heading and footer below are not printed — your letterpad already has them."
                            : "Letters print on the composed stationery below until you switch this on."}
                    </p>

                    <div className="mt-4 flex gap-2">
                        <Button variant="secondary" size="sm" disabled={busy}
                            onClick={() => input.current?.click()}>
                            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                            Replace
                        </Button>
                        <Button variant="ghost" size="sm" disabled={busy} onClick={() => void remove()}>
                            <Trash2 className="h-4 w-4" /> Remove
                        </Button>
                    </div>
                </div>
            ) : (
                <div className="mt-4">
                    <button
                        type="button"
                        disabled={busy}
                        onClick={() => input.current?.click()}
                        className="flex w-full flex-col items-center gap-2 rounded-lg border border-dashed border-fg/20 px-4 py-8 text-center transition-colors hover:border-violet/50 hover:bg-fg/[0.02] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet disabled:opacity-50"
                    >
                        {busy
                            ? <Loader2 className="h-6 w-6 animate-spin text-violet" />
                            : <Upload className="h-6 w-6 text-fg/30" />}
                        <span className="text-sm font-medium">{busy ? "Uploading…" : "Choose a file"}</span>
                        <span className="text-xs text-fg/40">
                            PNG, JPEG or WebP · up to {MAX_MB} MB · A4 portrait prints best
                        </span>
                    </button>
                    <p className="mt-2 text-xs text-fg/40">
                        Have it as a PDF? Export page one as an image first — a PDF cannot be used as the
                        background of a printed page here.
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
