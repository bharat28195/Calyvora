"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ChevronsUpDown, Globe, KeyRound, Loader2, LogOut, UserCog } from "lucide-react";
import { cn } from "@/lib/utils";
import { useT } from "@/hooks/useT";
import type { Me } from "@/lib/types";

/**
 * The signed-in person, as one button: click it for Account settings, Language & region and Log out.
 *
 * <p>`placement="up"` is the sidebar version (the menu opens above, since it sits at the bottom of
 * the screen); `"down"` is the phone header, which shows only the initials.
 */
export function ProfileMenu({ me, onLogout, loggingOut, placement }: {
  me: Me;
  onLogout: () => void;
  loggingOut: boolean;
  placement: "up" | "down";
}) {
  const t = useT();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const { user, company } = me;
  const initials = `${user.firstName?.[0] ?? ""}${user.lastName?.[0] ?? ""}`.toUpperCase() || "?";

  // Close when the person clicks elsewhere, presses Escape, or the menu took them somewhere.
  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (root.current && !root.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    document.addEventListener("mousedown", onClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onClick);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);
  useEffect(() => setOpen(false), [pathname]);

  const item = "flex w-full items-center gap-2.5 rounded-md px-3 py-2 text-sm text-fg/70 hover:bg-fg/5 hover:text-fg";

  return (
    <div ref={root} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={t("Open profile menu")}
        className={cn(
          "flex items-center gap-2.5 rounded-lg text-left transition-colors hover:bg-fg/5",
          placement === "up" ? "w-full px-2 py-2" : "p-1",
          open && "bg-fg/5",
        )}
      >
        <span className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-violet/15 text-xs font-semibold text-violet">
          {initials}
        </span>
        {placement === "up" && (
          <>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">{user.firstName} {user.lastName}</span>
              <span className="block truncate text-xs text-fg/40">{company.name} · {user.role}</span>
            </span>
            <ChevronsUpDown className="h-4 w-4 shrink-0 text-fg/40" />
          </>
        )}
      </button>

      {open && (
        <div
          role="menu"
          className={cn(
            "absolute z-50 w-60 rounded-xl border border-fg/10 bg-surface p-1.5 shadow-xl",
            placement === "up" ? "bottom-full left-0 mb-2" : "right-0 top-full mt-2",
          )}
        >
          <div className="border-b border-fg/10 px-3 pb-2.5 pt-1.5">
            <p className="truncate text-sm font-medium">{user.firstName} {user.lastName}</p>
            <p className="truncate text-xs text-fg/50">{user.email}</p>
          </div>
          <div className="py-1">
            <Link role="menuitem" href="/account" className={item}>
              <UserCog className="h-4 w-4" /> {t("Account settings")}
            </Link>
            <Link role="menuitem" href="/account#preferences" className={item}>
              <Globe className="h-4 w-4" /> {t("Language & region")}
            </Link>
            <Link role="menuitem" href="/account#password" className={item}>
              <KeyRound className="h-4 w-4" /> {t("Change password")}
            </Link>
          </div>
          <div className="border-t border-fg/10 pt-1">
            <button role="menuitem" type="button" onClick={onLogout} disabled={loggingOut}
              className={cn(item, "disabled:opacity-50")}>
              {loggingOut ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogOut className="h-4 w-4" />}
              {t("Log out")}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
