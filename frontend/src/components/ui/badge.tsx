import { cn } from "@/lib/utils";

const tones: Record<string, string> = {
  OWNER: "bg-violet/20 text-violet",
  ADMIN: "bg-aqua/20 text-aqua",
  MEMBER: "bg-white/10 text-white/70",
  ACTIVE: "bg-emerald-500/15 text-emerald-300",
  PENDING_VERIFICATION: "bg-amber-500/15 text-amber-300",
  INVITED: "bg-amber-500/15 text-amber-300",
  PENDING: "bg-amber-500/15 text-amber-300",
  DISABLED: "bg-white/10 text-white/40",
  APPROVED: "bg-emerald-500/15 text-emerald-300",
  REJECTED: "bg-red-500/15 text-red-300",
  CANCELLED: "bg-white/10 text-white/40",
};

export function Badge({ value }: { value: string }) {
  return (
    <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", tones[value] ?? "bg-white/10 text-white/70")}>
      {value.replace(/_/g, " ").toLowerCase()}
    </span>
  );
}
