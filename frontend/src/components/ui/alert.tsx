import * as React from "react";
import { cn } from "@/lib/utils";

type Tone = "error" | "success" | "info";

const tones: Record<Tone, string> = {
  error: "border-red-500/30 bg-red-500/10 text-red-200",
  success: "border-emerald-500/30 bg-emerald-500/10 text-emerald-200",
  info: "border-fg/15 bg-fg/5 text-fg/80",
};

export function Alert({
  tone = "info",
  className,
  children,
  ...props
}: { tone?: Tone } & React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={cn("rounded-lg border px-4 py-3 text-sm", tones[tone], className)}
      {...props}
    >
      {children}
    </div>
  );
}
