import { brand, brandMark, hasParent } from "@/lib/brand";

/** Product wordmark: a gradient app mark + product name, with "by Calyvora" when a product name is set. */
export function Wordmark({ className }: { className?: string }) {
  return (
    <span className={"inline-flex items-center gap-2 " + (className ?? "")}>
      <span className="grid h-7 w-7 place-items-center rounded-lg bg-gradient-to-br from-violet to-aqua text-sm font-bold text-white shadow-sm shadow-violet/30">
        {brandMark}
      </span>
      <span className="flex flex-col leading-none">
        <span className="font-semibold tracking-tight">{brand.product}</span>
        {hasParent && <span className="mt-0.5 text-[10px] text-fg/40">by {brand.parent}</span>}
      </span>
    </span>
  );
}
