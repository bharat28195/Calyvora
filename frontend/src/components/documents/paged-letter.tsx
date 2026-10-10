"use client";

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { useLetterpadImage } from "@/components/documents/use-letterpad";
import type { Letterhead } from "@/lib/types";

/**
 * A letter on the company letterpad as the A4 pages it prints on (PD-69): page one on the letterpad,
 * page two onwards on the continuation sheet, the text kept inside each sheet's writing area and
 * carried to the next page where it runs out. The same areas the PDF uses, so what is checked on
 * screen is what is emailed and printed.
 *
 * <p>Laid out at A4 size in CSS pixels (96 per inch) and scaled to fit, so millimetres convert exactly
 * and the line breaks do not change with the width of the window.
 */

const PAGE_W = 794;   // 210 mm
const PAGE_H = 1123;  // 297 mm
const MM = PAGE_W / 210;

/** A measured piece of the body. {@code gap} marks a blank line, dropped at the top of a page. */
type Block = { html: string; height: number; gap?: boolean; pageBreak?: boolean };

export function PagedLetter({
  html,
  letterhead,
  fontFamily,
  className = "",
}: {
  /** The rendered body (renderLetter in letter.tsx). */
  html: string;
  letterhead: Letterhead;
  fontFamily: string;
  className?: string;
}) {
  const first = useLetterpadImage(letterhead.updatedAt, true, "background");
  const later = useLetterpadImage(letterhead.updatedAt, true, "continuation");
  const side = (letterhead.sideMm ?? 22) * MM;
  const area = {
    firstTop: (letterhead.firstTopMm ?? 40) * MM,
    firstBottom: (letterhead.firstBottomMm ?? 32) * MM,
    laterTop: (letterhead.laterTopMm ?? 22) * MM,
    laterBottom: (letterhead.laterBottomMm ?? 32) * MM,
  };
  const width = PAGE_W - 2 * side;

  const measure = useRef<HTMLDivElement>(null);
  const [pages, setPages] = useState<string[][]>([[]]);
  const [fontsReady, setFontsReady] = useState(0);

  useEffect(() => {
    // Line breaks depend on the font; lay out again once the web fonts have arrived.
    document.fonts?.ready.then(() => setFontsReady((n) => n + 1)).catch(() => undefined);
  }, []);

  useLayoutEffect(() => {
    const root = measure.current;
    if (!root) return;
    const blocks = toBlocks(root);
    const capacity = (i: number) =>
      PAGE_H - (i === 0 ? area.firstTop + area.firstBottom : area.laterTop + area.laterBottom);
    const out: string[][] = [[]];
    let used = 0;
    const newPage = () => { out.push([]); used = 0; };
    for (const b of blocks) {
      if (b.pageBreak) {
        if (out[out.length - 1].length > 0) newPage();
        continue;
      }
      if (used > 0 && used + b.height > capacity(out.length - 1)) newPage();
      // A blank line at the top of a page is just white space; drop it.
      if (used === 0 && b.gap) continue;
      out[out.length - 1].push(b.html);
      used += b.height;
    }
    setPages(out);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [html, width, area.firstTop, area.firstBottom, area.laterTop, area.laterBottom, fontFamily, fontsReady]);

  // Scale the A4 pages to the space available.
  const frame = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const el = frame.current;
    if (!el) return;
    const fit = () => setScale(Math.min(1, el.clientWidth / PAGE_W));
    fit();
    const ro = new ResizeObserver(fit);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const text = { fontFamily, fontSize: "14px", lineHeight: 1.5 } as const;

  return (
    <div ref={frame} className={`letter-pages w-full ${className}`}>
      {/* The measuring copy: the whole body at the writing width, never shown. */}
      <div
        ref={measure}
        aria-hidden
        className="letter-measure text-neutral-800"
        style={{ ...text, position: "absolute", left: -10000, top: 0, width, visibility: "hidden" }}
        dangerouslySetInnerHTML={{ __html: html }}
      />
      <div className="flex flex-col items-center gap-4">
        {pages.map((blocks, i) => (
          <div key={i} style={{ width: PAGE_W * scale, height: PAGE_H * scale }}
               className="overflow-hidden rounded-md shadow-md ring-1 ring-black/10">
            <div
              className="relative bg-white text-neutral-800"
              style={{
                width: PAGE_W,
                height: PAGE_H,
                transform: `scale(${scale})`,
                transformOrigin: "top left",
                backgroundImage: (i === 0 ? first : later ?? first) ? `url(${i === 0 ? first : later ?? first})` : undefined,
                backgroundSize: "100% 100%",
              }}
            >
              <div
                style={{
                  ...text,
                  position: "absolute",
                  left: side,
                  width,
                  top: i === 0 ? area.firstTop : area.laterTop,
                }}
                dangerouslySetInnerHTML={{ __html: blocks.join("") }}
              />
            </div>
          </div>
        ))}
      </div>
      <p className="mt-2 text-center text-xs text-fg/40">
        {pages.length} {pages.length === 1 ? "page" : "pages"} · A4 on your letterpad
      </p>
    </div>
  );
}

/**
 * The measured blocks of the body. Each top-level element's height runs to the start of the next one,
 * so collapsed margins are counted once. A list or a table is split into one block per item / row —
 * the table keeping its header and its column widths — so a long salary annexure continues on the
 * next page rather than jumping there whole.
 */
function toBlocks(root: HTMLElement): Block[] {
  const kids = Array.from(root.children) as HTMLElement[];
  const out: Block[] = [];
  kids.forEach((el, i) => {
    const next = kids[i + 1];
    const height = (next ? next.offsetTop : root.scrollHeight) - el.offsetTop;
    if (el.classList.contains("letter-page-break")) {
      out.push({ html: "", height: 0, pageBreak: true });
      return;
    }
    const tag = el.tagName.toLowerCase();
    if ((tag === "ul" || tag === "ol") && el.children.length > 1) {
      const items = Array.from(el.children) as HTMLElement[];
      // Item j runs from its own top to the next item's; the first also carries the list's top
      // margin and the last everything to the next block.
      // offsetTop is relative to the measuring box (it is positioned) for the list and its items alike.
      items.forEach((li, j) => {
        const start = j === 0 ? el.offsetTop : li.offsetTop;
        const end = j + 1 < items.length ? items[j + 1].offsetTop : el.offsetTop + height;
        const margins = `margin-top:${j === 0 ? "" : "0"};margin-bottom:${j + 1 < items.length ? "0" : ""}`;
        const start_ = tag === "ol" ? ` start="${j + 1}"` : "";
        out.push({ html: `<${tag} class="${el.className}"${start_} style="${margins}">${li.outerHTML}</${tag}>`, height: end - start });
      });
      return;
    }
    if (tag === "table") {
      const rows = Array.from(el.querySelectorAll("tbody > tr")) as HTMLElement[];
      const thead = el.querySelector("thead") as HTMLElement | null;
      if (rows.length > 1) {
        const widths = Array.from((thead ?? rows[0]).querySelectorAll("th,td")).map((c) => (c as HTMLElement).offsetWidth);
        const cols = `<colgroup>${widths.map((w) => `<col style="width:${w}px">`).join("")}</colgroup>`;
        const below = height - el.offsetHeight;   // the table's bottom margin and anything up to the next block
        rows.forEach((tr, j) => {
          const last = j + 1 === rows.length;
          const h = tr.offsetHeight + (j === 0 ? (thead?.offsetHeight ?? 0) + (tr.offsetTop - (thead?.offsetHeight ?? 0)) : 0) + (last ? below : 0);
          const margins = `table-layout:fixed;${j === 0 ? "" : "margin-top:0;"}${last ? "" : "margin-bottom:0;"}`;
          const head = j === 0 && thead ? thead.outerHTML : "";
          out.push({ html: `<table class="${el.className}" style="${margins}">${cols}${head}<tbody>${tr.outerHTML}</tbody></table>`, height: h });
        });
        return;
      }
    }
    out.push({ html: el.outerHTML, height, gap: el.classList.contains("h-3") });
  });
  return out;
}
