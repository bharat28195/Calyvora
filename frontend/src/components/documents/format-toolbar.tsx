"use client";

import type { RefObject } from "react";
import { Bold, Italic, Heading1, Heading2, List, ListOrdered, Minus } from "lucide-react";

/**
 * The formatting toolbar over the letter body (PD-20).
 *
 * <p>It edits the text rather than replacing it with a rich-text widget, which is the whole design:
 * what gets saved stays readable, diffable and repairable by hand, and no untrusted HTML ever
 * reaches the renderer. The buttons wrap the selection or prefix the line, exactly as a person would
 * type it — so someone who knows the format can ignore the toolbar entirely.
 */
export function FormatToolbar({
  textareaRef,
  value,
  onChange,
}: {
  textareaRef: RefObject<HTMLTextAreaElement | null>;
  value: string;
  onChange: (next: string) => void;
}) {
  /** Wrap the selection in `marker`, or drop the marker in ready for typing when nothing is selected. */
  function wrap(marker: string) {
    const el = textareaRef.current;
    if (!el) return;
    const start = el.selectionStart ?? 0;
    const end = el.selectionEnd ?? start;
    const selected = value.slice(start, end);
    const next = `${value.slice(0, start)}${marker}${selected}${marker}${value.slice(end)}`;
    onChange(next);
    restore(el, selected ? start + marker.length : start + marker.length,
      selected ? end + marker.length : start + marker.length);
  }

  /**
   * Put `prefix` at the start of every selected line — and take it off again if all of them already
   * have it, so the same button un-bullets a list.
   */
  function prefixLines(prefix: string | ((index: number) => string)) {
    const el = textareaRef.current;
    if (!el) return;
    const start = el.selectionStart ?? 0;
    const end = el.selectionEnd ?? start;
    const from = value.lastIndexOf("\n", start - 1) + 1;
    const toRaw = value.indexOf("\n", end);
    const to = toRaw === -1 ? value.length : toRaw;

    const lines = value.slice(from, to).split("\n");
    const first = typeof prefix === "function" ? prefix(0) : prefix;
    const allPrefixed = lines.every((l) => l.startsWith(first) || l.trim() === "");
    const next = lines
      .map((line, i) => {
        const p = typeof prefix === "function" ? prefix(i) : prefix;
        if (line.trim() === "") return line;
        return allPrefixed ? line.slice(first.length) : `${p}${line}`;
      })
      .join("\n");

    onChange(value.slice(0, from) + next + value.slice(to));
    restore(el, from, from + next.length);
  }

  /** Selection is restored after React re-renders the textarea, or the cursor jumps to the end. */
  function restore(el: HTMLTextAreaElement, start: number, end: number) {
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(start, end);
    });
  }

  function insertBlock(text: string) {
    const el = textareaRef.current;
    if (!el) return;
    const at = el.selectionStart ?? value.length;
    const lineStart = value.lastIndexOf("\n", at - 1) + 1;
    const next = `${value.slice(0, lineStart)}${text}\n${value.slice(lineStart)}`;
    onChange(next);
    restore(el, lineStart + text.length + 1, lineStart + text.length + 1);
  }

  return (
    <div className="flex flex-wrap items-center gap-1 rounded-lg border border-fg/10 bg-fg/5 p-1">
      <ToolButton label="Bold" onClick={() => wrap("**")}><Bold className="h-4 w-4" /></ToolButton>
      <ToolButton label="Italic" onClick={() => wrap("*")}><Italic className="h-4 w-4" /></ToolButton>
      <Divider />
      <ToolButton label="Heading" onClick={() => prefixLines("# ")}><Heading1 className="h-4 w-4" /></ToolButton>
      <ToolButton label="Subheading" onClick={() => prefixLines("## ")}><Heading2 className="h-4 w-4" /></ToolButton>
      <Divider />
      <ToolButton label="Bulleted list" onClick={() => prefixLines("- ")}><List className="h-4 w-4" /></ToolButton>
      <ToolButton label="Numbered list" onClick={() => prefixLines((i) => `${i + 1}. `)}>
        <ListOrdered className="h-4 w-4" />
      </ToolButton>
      <ToolButton label="Divider line" onClick={() => insertBlock("---")}><Minus className="h-4 w-4" /></ToolButton>
    </div>
  );
}

function ToolButton({
  label, onClick, children,
}: { label: string; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      aria-label={label}
      className="rounded-md p-1.5 text-fg/60 transition-colors hover:bg-violet/15 hover:text-violet focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
    >
      {children}
    </button>
  );
}

function Divider() {
  return <span className="mx-0.5 h-5 w-px bg-fg/10" aria-hidden />;
}
