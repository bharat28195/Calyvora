"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Clock } from "lucide-react";
import { api } from "@/lib/api";
import { useSession } from "@/hooks/useSession";
import { Button } from "@/components/ui/button";

/**
 * Ends a session that nobody is using, after the company's configured idle window.
 *
 * <p>Two halves, and only one of them is here. The server caps the refresh cookie's lifetime at the
 * idle window, so a session that is not renewed dies whether or not this component ever runs —
 * closing the laptop, killing the script, editing the countdown in dev tools all end the same way.
 * What this adds is the part a person experiences: a warning before it happens, a way to stay, and
 * being taken to the login screen with a reason instead of watching the app quietly break.
 *
 * <p>Activity is pointer, keyboard, scroll, or bringing the tab back to the front. Reading a long
 * page without touching anything counts as idle, which is why the warning exists rather than a
 * silent sign-out.
 */

/** Long enough to notice and react, short enough that it is not most of a fifteen-minute window. */
const WARN_SECONDS = 60;

/** Renew at most this often: the cookie only needs refreshing to prove somebody is still here. */
const MIN_RENEW_GAP_MS = 4 * 60 * 1000;

const ACTIVITY_EVENTS = ["pointerdown", "keydown", "scroll", "wheel", "touchstart"] as const;

export function IdleTimeout() {
    const { me, logout } = useSession();
    const router = useRouter();
    const minutes = me?.company.sessionIdleMinutes ?? null;

    const lastActivity = useRef(Date.now());
    const lastRenew = useRef(Date.now());
    const [secondsLeft, setSecondsLeft] = useState<number | null>(null);

    const signOut = useCallback(async () => {
        setSecondsLeft(null);
        try {
            await logout();
        } finally {
            router.replace("/login?reason=idle");
        }
    }, [logout, router]);

    const stayIn = useCallback(() => {
        lastActivity.current = Date.now();
        setSecondsLeft(null);
        // Renew immediately: the cookie is seconds from expiring, which is the whole reason the
        // dialog is on screen. Waiting for the next tick would sign the person out anyway.
        lastRenew.current = Date.now();
        void api.refresh().catch(() => void signOut());
    }, [signOut]);

    useEffect(() => {
        if (!minutes) return;   // null or 0 — no policy, nothing to run

        const windowMs = minutes * 60 * 1000;
        const mark = () => { lastActivity.current = Date.now(); };
        for (const e of ACTIVITY_EVENTS) window.addEventListener(e, mark, { passive: true });
        const onVisible = () => { if (document.visibilityState === "visible") mark(); };
        document.addEventListener("visibilitychange", onVisible);

        const tick = window.setInterval(() => {
            const idleFor = Date.now() - lastActivity.current;
            const remaining = windowMs - idleFor;

            if (remaining <= 0) {
                void signOut();
                return;
            }
            if (remaining <= WARN_SECONDS * 1000) {
                setSecondsLeft(Math.ceil(remaining / 1000));
                return;
            }
            setSecondsLeft(null);

            // Somebody is using the app: push the cookie's expiry out so it always sits one idle
            // window ahead of them. Throttled, because the token rotates on every call and doing
            // this per keystroke would be a write per keystroke.
            if (Date.now() - lastRenew.current >= Math.min(MIN_RENEW_GAP_MS, windowMs / 2)) {
                lastRenew.current = Date.now();
                void api.refresh().catch(() => { /* a dead session is handled by the next request */ });
            }
        }, 1000);

        return () => {
            for (const e of ACTIVITY_EVENTS) window.removeEventListener(e, mark);
            document.removeEventListener("visibilitychange", onVisible);
            window.clearInterval(tick);
        };
    }, [minutes, signOut]);

    if (secondsLeft === null) return null;

    return (
        // Deliberately not the shared Modal: that one closes on a click outside or Escape, and a
        // stray click must not be able to mean "keep me signed in" on a machine somebody walked
        // away from. The only ways out are the two buttons.
        <div
            className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 p-4"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="idle-title"
        >
            <div className="w-full max-w-sm rounded-2xl border border-fg/10 bg-surface p-6 text-center shadow-2xl">
                <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-amber-400/15">
                    <Clock className="h-5 w-5 text-amber-400" />
                </span>
                <h2 id="idle-title" className="mt-4 text-lg font-semibold">Still there?</h2>
                <p className="mt-2 text-sm text-fg/60">
                    You&apos;ll be signed out in{" "}
                    <span className="font-semibold tabular-nums text-fg">{secondsLeft}s</span> because nothing
                    has happened for {minutes} minutes.
                </p>
                <div className="mt-5 flex flex-col gap-2">
                    <Button size="lg" onClick={stayIn}>Stay signed in</Button>
                    <button
                        type="button"
                        onClick={() => void signOut()}
                        className="text-sm text-fg/50 hover:text-fg"
                    >
                        Sign out now
                    </button>
                </div>
            </div>
        </div>
    );
}
