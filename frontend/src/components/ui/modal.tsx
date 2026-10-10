"use client";

import { useEffect } from "react";
import { X } from "lucide-react";

export function Modal({
  open,
  onClose,
  title,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={onClose}
    >
      <div
        // max-h + scroll so a tall form (the employee edit has a dozen fields) can always be
        // reached and the Save button at the bottom is never off-screen. 90dvh leaves a margin and
        // uses the dynamic viewport so a mobile browser's address bar doesn't clip it.
        className="max-h-[90dvh] w-full max-w-md overflow-y-auto rounded-2xl border border-fg/10 bg-surface px-6 pb-6 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Pinned: on a long form the name and the close button stay in view however far you scroll. */}
        <div className="sticky top-0 z-10 -mx-6 flex items-center justify-between border-b border-fg/10 bg-surface px-6 pb-3 pt-6">
          <h2 className="text-lg font-semibold">{title}</h2>
          <button onClick={onClose} aria-label="Close" className="rounded-md p-1 text-fg/50 hover:bg-fg/5 hover:text-fg">
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="mt-4">{children}</div>
      </div>
    </div>
  );
}
