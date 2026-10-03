"use client";

import * as React from "react";
import { Eye, EyeOff } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * A password field with a show/hide toggle.
 *
 * <p>Same look as {@link Input}, with an eye button that flips the field between hidden and visible.
 * People mistype passwords they cannot see — on a login, and far more on the "set a password" steps
 * where there is no second chance — so letting them check what they typed cuts failed sign-ins and
 * support messages. The toggle is a real button (not a swap of the input's type alone) so it is
 * reachable by keyboard and announced to screen readers.
 *
 * <p>Takes every prop a native password input does except `type`, which it owns.
 */
export const PasswordInput = React.forwardRef<
  HTMLInputElement,
  Omit<React.InputHTMLAttributes<HTMLInputElement>, "type">
>(({ className, ...props }, ref) => {
  const [visible, setVisible] = React.useState(false);
  return (
    <div className="relative">
      <input
        ref={ref}
        type={visible ? "text" : "password"}
        className={cn(
          "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 pr-11 text-sm text-fg",
          "placeholder:text-fg/30 focus-visible:outline-none focus-visible:ring-2",
          "focus-visible:ring-violet disabled:opacity-50 aria-[invalid=true]:border-red-500/70",
          className,
        )}
        {...props}
      />
      <button
        type="button"
        // Does not submit the form, and stays out of the tab order's way until wanted.
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? "Hide password" : "Show password"}
        aria-pressed={visible}
        tabIndex={-1}
        className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-fg/40 hover:bg-fg/5 hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet"
      >
        {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
      </button>
    </div>
  );
});
PasswordInput.displayName = "PasswordInput";
