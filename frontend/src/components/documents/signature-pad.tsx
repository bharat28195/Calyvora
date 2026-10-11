"use client";

import { useEffect, useRef, useState } from "react";
import { Eraser, PenLine, Type } from "lucide-react";

/**
 * A signature, drawn or typed, as a PNG data URI (PD-71, PD-72).
 *
 * <p>Drawn with a finger, a stylus or a mouse on a canvas sized for the device's pixel density, so the
 * stroke is crisp on paper. Typed: the name set in a handwriting face and rasterised the same way, for
 * someone at a desktop with no way to draw. Either way what leaves here is an image; the server crops
 * it to the ink and makes the paper transparent.
 */
const WIDTH = 520;
const HEIGHT = 170;
const INK = "#1b2a6b";
const SCRIPT = "'Segoe Script', 'Brush Script MT', 'Lucida Handwriting', cursive";

export function SignaturePad({
  onChange,
  defaultName = "",
}: {
  /** The signature as a PNG data URI, or null when the pad is empty. */
  onChange: (dataUri: string | null) => void;
  /** Pre-fills the typed signature. */
  defaultName?: string;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const last = useRef<{ x: number; y: number } | null>(null);
  const [mode, setMode] = useState<"draw" | "type">("draw");
  const [typed, setTyped] = useState(defaultName);
  const [hasInk, setHasInk] = useState(false);

  // A device-pixel-ratio canvas, cleared whenever the mode changes.
  useEffect(() => {
    const c = canvas.current;
    if (!c) return;
    const ratio = Math.max(1, Math.min(3, window.devicePixelRatio || 1));
    c.width = WIDTH * ratio;
    c.height = HEIGHT * ratio;
    const ctx = c.getContext("2d");
    if (!ctx) return;
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, WIDTH, HEIGHT);
    setHasInk(false);
    onChange(null);
    if (mode === "type") paintTyped(typed);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

  function paintTyped(text: string) {
    const c = canvas.current;
    const ctx = c?.getContext("2d");
    if (!c || !ctx) return;
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, WIDTH, HEIGHT);
    const t = text.trim();
    if (!t) {
      setHasInk(false);
      onChange(null);
      return;
    }
    let size = 64;
    ctx.font = `${size}px ${SCRIPT}`;
    while (ctx.measureText(t).width > WIDTH - 40 && size > 24) {
      size -= 4;
      ctx.font = `${size}px ${SCRIPT}`;
    }
    ctx.fillStyle = INK;
    ctx.textBaseline = "middle";
    ctx.textAlign = "center";
    ctx.fillText(t, WIDTH / 2, HEIGHT / 2 + 4);
    setHasInk(true);
    onChange(c.toDataURL("image/png"));
  }

  function point(e: React.PointerEvent<HTMLCanvasElement>) {
    const r = e.currentTarget.getBoundingClientRect();
    return { x: ((e.clientX - r.left) / r.width) * WIDTH, y: ((e.clientY - r.top) / r.height) * HEIGHT };
  }

  function down(e: React.PointerEvent<HTMLCanvasElement>) {
    if (mode !== "draw") return;
    e.currentTarget.setPointerCapture(e.pointerId);
    drawing.current = true;
    last.current = point(e);
  }

  function move(e: React.PointerEvent<HTMLCanvasElement>) {
    if (!drawing.current || mode !== "draw") return;
    const ctx = canvas.current?.getContext("2d");
    const p = point(e);
    if (!ctx || !last.current) return;
    ctx.strokeStyle = INK;
    ctx.lineWidth = e.pointerType === "pen" ? 2.2 + (e.pressure || 0.5) * 1.6 : 2.6;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.beginPath();
    ctx.moveTo(last.current.x, last.current.y);
    ctx.lineTo(p.x, p.y);
    ctx.stroke();
    last.current = p;
    if (!hasInk) setHasInk(true);
  }

  function up() {
    if (!drawing.current) return;
    drawing.current = false;
    last.current = null;
    if (canvas.current && hasInk) onChange(canvas.current.toDataURL("image/png"));
  }

  function clear() {
    const ctx = canvas.current?.getContext("2d");
    if (!ctx) return;
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, WIDTH, HEIGHT);
    setHasInk(false);
    onChange(null);
    if (mode === "type") setTyped("");
  }

  return (
    <div>
      <div className="mb-2 flex gap-1 text-xs">
        <button type="button" onClick={() => setMode("draw")}
          className={`inline-flex items-center gap-1 rounded-md px-2.5 py-1 ${mode === "draw" ? "bg-violet/15 text-violet" : "text-fg/50 hover:bg-fg/5"}`}>
          <PenLine className="h-3.5 w-3.5" /> Draw
        </button>
        <button type="button" onClick={() => setMode("type")}
          className={`inline-flex items-center gap-1 rounded-md px-2.5 py-1 ${mode === "type" ? "bg-violet/15 text-violet" : "text-fg/50 hover:bg-fg/5"}`}>
          <Type className="h-3.5 w-3.5" /> Type
        </button>
      </div>
      {mode === "type" && (
        <input
          value={typed}
          onChange={(e) => { setTyped(e.target.value); paintTyped(e.target.value); }}
          placeholder="Type your name"
          className="mb-2 w-full rounded-md border border-fg/15 bg-fg/[0.03] px-3 py-2 text-sm"
        />
      )}
      <div className="relative overflow-hidden rounded-lg border border-fg/15 bg-white">
        <canvas
          ref={canvas}
          onPointerDown={down}
          onPointerMove={move}
          onPointerUp={up}
          onPointerCancel={up}
          className={`block w-full touch-none ${mode === "draw" ? "cursor-crosshair" : ""}`}
          style={{ aspectRatio: `${WIDTH} / ${HEIGHT}` }}
          aria-label={mode === "draw" ? "Draw your signature here" : "Your typed signature"}
        />
        <div className="pointer-events-none absolute inset-x-6 bottom-6 border-b border-dashed border-neutral-300" />
        {!hasInk && mode === "draw" && (
          <span className="pointer-events-none absolute inset-0 flex items-center justify-center text-sm text-neutral-400">
            Sign here
          </span>
        )}
      </div>
      <button type="button" onClick={clear} className="mt-1.5 inline-flex items-center gap-1 text-xs text-fg/50 hover:text-fg">
        <Eraser className="h-3.5 w-3.5" /> Clear
      </button>
    </div>
  );
}
