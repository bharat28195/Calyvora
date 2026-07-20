import * as React from "react";

/** Accessible labelled form field with an inline error slot. */
export function Field({
  label,
  htmlFor,
  error,
  hint,
  children,
}: {
  label: string;
  htmlFor: string;
  error?: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-sm font-medium text-white/80">
        {label}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-white/40">{hint}</p>}
      {error && (
        <p id={`${htmlFor}-error`} className="text-xs text-red-400">
          {error}
        </p>
      )}
    </div>
  );
}
