import * as React from "react";
import { cn } from "@/lib/utils";

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => (
    <input
      ref={ref}
      className={cn(
        "h-11 w-full rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg",
        "placeholder:text-fg/30 focus-visible:outline-none focus-visible:ring-2",
        "focus-visible:ring-violet disabled:opacity-50 aria-[invalid=true]:border-red-500/70",
        className,
      )}
      {...props}
    />
  ),
);
Input.displayName = "Input";
