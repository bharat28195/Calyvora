"use client";

import { useMemo } from "react";
import { EMPTY } from "@/lib/documents";

/**
 * Renders a letter body as a page-like sheet (feedback D2). Deliberately looks like paper — a client
 * demo should see the document, not a text area. Marked `letter-sheet` so printing isolates it
 * (see the print rules in globals.css).
 */
export function LetterSheet({ body, className = "" }: { body: string; className?: string }) {
  const html = useMemo(() => renderLetter(body), [body]);
  return (
    <div
      className={`letter-sheet rounded-xl border border-fg/10 bg-white px-8 py-10 text-[15px] leading-relaxed text-neutral-800 shadow-sm sm:px-12 sm:py-14 ${className}`}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
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

function renderLetter(body: string): string {
  const lines = (body ?? "").replace(/\r\n/g, "\n").split("\n");
  const out: string[] = [];
  let list: string[] | null = null;
  const flush = () => {
    if (list) {
      out.push(`<ul class="my-3 ml-5 list-disc space-y-1">${list.join("")}</ul>`);
      list = null;
    }
  };
  for (const line of lines) {
    if (/^\s*[-*]\s+/.test(line)) {
      (list ??= []).push(`<li>${inline(line.replace(/^\s*[-*]\s+/, ""))}</li>`);
      continue;
    }
    flush();
    if (line.trim() === "") out.push('<div class="h-3"></div>');
    else out.push(`<p>${inline(line)}</p>`);
  }
  flush();
  return out.join("\n");
}
