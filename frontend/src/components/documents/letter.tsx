"use client";

import { useMemo } from "react";
import { EMPTY, LETTERHEAD_FONTS } from "@/lib/documents";
import type { Letterhead } from "@/lib/types";

/**
 * Renders a letter as a page-like sheet (feedback D2), on the company letterpad when there is one
 * (PD-20). Deliberately looks like paper — a client demo should see the document, not a text area.
 * Marked `letter-sheet` so printing isolates it (see the print rules in globals.css).
 *
 * <p>The letterpad is applied at render time rather than baked into the stored body. A letter's
 * *words* are frozen when it is issued, because that is what someone signed; the stationery is
 * current, because changing your logo should not leave you with two kinds of letter in the file.
 */
export function LetterSheet({
  body,
  letterhead,
  className = "",
}: {
  body: string;
  /** Omit, or pass null, to print on plain paper. */
  letterhead?: Letterhead | null;
  className?: string;
}) {
  const html = useMemo(() => renderLetter(body), [body]);
  const font = LETTERHEAD_FONTS[letterhead?.fontFamily ?? "SERIF"].stack;
  const accent = letterhead?.brandColor ?? "#7c5cff";

  return (
    <div
      className={`letter-sheet rounded-xl border border-fg/10 bg-white px-8 py-10 text-[15px] leading-relaxed text-neutral-800 shadow-sm sm:px-12 sm:py-14 ${className}`}
      style={letterhead ? { fontFamily: font } : undefined}
    >
      {letterhead && <Letterpad letterhead={letterhead} accent={accent} />}
      <div dangerouslySetInnerHTML={{ __html: html }} />
      {letterhead && <Footer letterhead={letterhead} accent={accent} />}
    </div>
  );
}

function Letterpad({ letterhead, accent }: { letterhead: Letterhead; accent: string }) {
  const address = splitLines(letterhead.addressLines);
  const hasAnything = letterhead.logoUrl || letterhead.heading || address.length > 0;
  if (!hasAnything) return null;

  return (
    <header className="mb-8">
      <div className="flex items-start justify-between gap-6">
        {letterhead.logoUrl && (
          /* An arbitrary external URL the company pasted in, so next/image is not an option: it
             would need every possible host allow-listed up front. */
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={letterhead.logoUrl}
            alt=""
            className="max-h-16 max-w-[12rem] object-contain"
          />
        )}
        <div className={letterhead.logoUrl ? "text-right" : ""}>
          {letterhead.heading && (
            <div className="text-lg font-semibold tracking-tight" style={{ color: accent }}>
              {letterhead.heading}
            </div>
          )}
          {address.map((line, i) => (
            <div key={i} className="text-[12.5px] leading-snug text-neutral-500">{line}</div>
          ))}
        </div>
      </div>
      {letterhead.showDivider && (
        <div className="mt-5 h-[2px] rounded-full" style={{ backgroundColor: accent }} />
      )}
    </header>
  );
}

function Footer({ letterhead, accent }: { letterhead: Letterhead; accent: string }) {
  const lines = splitLines(letterhead.footerText);
  if (lines.length === 0) return null;
  return (
    <footer className="mt-10 border-t pt-4" style={{ borderColor: `${accent}33` }}>
      {lines.map((line, i) => (
        <div key={i} className="text-center text-[11.5px] leading-snug text-neutral-500">{line}</div>
      ))}
    </footer>
  );
}

function splitLines(value: string | null | undefined): string[] {
  return (value ?? "").split("\n").map((l) => l.trim()).filter(Boolean);
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/** Bold + italics + a highlight for unresolved fields, so a gap is impossible to miss. */
function inline(s: string): string {
  return escapeHtml(s)
    .replace(/\*\*([^*]+)\*\*/g, '<strong class="font-semibold text-neutral-900">$1</strong>')
    .replace(/(^|[^*])\*([^*]+)\*/g, "$1<em>$2</em>")
    .replaceAll(EMPTY, `<span class="rounded bg-amber-100 px-1 text-amber-700" title="No value for this field">${EMPTY}</span>`);
}

/**
 * The letter body format: paragraphs, bullets, numbered lists, two heading levels and a rule.
 *
 * <p>Kept as plain text with light markup rather than stored HTML — the toolbar in the editor writes
 * this, so what is saved is always something a person can read and repair by hand, and there is no
 * untrusted HTML to sanitise before it reaches `dangerouslySetInnerHTML`.
 */
function renderLetter(body: string): string {
  const lines = (body ?? "").replace(/\r\n/g, "\n").split("\n");
  const out: string[] = [];
  let bullets: string[] | null = null;
  let numbers: string[] | null = null;

  const flush = () => {
    if (bullets) {
      out.push(`<ul class="my-3 ml-5 list-disc space-y-1">${bullets.join("")}</ul>`);
      bullets = null;
    }
    if (numbers) {
      out.push(`<ol class="my-3 ml-5 list-decimal space-y-1">${numbers.join("")}</ol>`);
      numbers = null;
    }
  };

  for (const line of lines) {
    if (/^\s*[-*]\s+/.test(line)) {
      if (numbers) flush();
      (bullets ??= []).push(`<li>${inline(line.replace(/^\s*[-*]\s+/, ""))}</li>`);
      continue;
    }
    if (/^\s*\d+[.)]\s+/.test(line)) {
      if (bullets) flush();
      (numbers ??= []).push(`<li>${inline(line.replace(/^\s*\d+[.)]\s+/, ""))}</li>`);
      continue;
    }
    flush();
    if (/^\s*---+\s*$/.test(line)) {
      out.push('<hr class="my-5 border-neutral-200" />');
    } else if (/^##\s+/.test(line)) {
      out.push(`<h3 class="mt-5 mb-1 text-[15px] font-semibold text-neutral-900">${inline(line.replace(/^##\s+/, ""))}</h3>`);
    } else if (/^#\s+/.test(line)) {
      out.push(`<h2 class="mt-5 mb-2 text-[18px] font-semibold tracking-tight text-neutral-900">${inline(line.replace(/^#\s+/, ""))}</h2>`);
    } else if (line.trim() === "") {
      out.push('<div class="h-3"></div>');
    } else {
      out.push(`<p>${inline(line)}</p>`);
    }
  }
  flush();
  return out.join("\n");
}
