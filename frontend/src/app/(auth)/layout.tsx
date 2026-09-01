import Link from "next/link";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { Wordmark } from "@/components/layout/wordmark";

/**
 * The signed-out shell: login, register, invitation, verification and password reset.
 *
 * <p>These screens were a card centred on an empty page — the first thing anyone sees of Orbit, and
 * it looked unfinished. The backdrop gives the card a ground to sit on: faint concentric rings behind
 * it, like orbits, taken from the product's own name rather than a generic dot grid, with a dot field
 * and a little grain so large flat areas do not band.
 *
 * <p>It is drawn entirely in CSS from the theme tokens (see globals.css), so it adds no image request
 * and follows light and dark on its own. Everything here is `pointer-events: none` and sits under the
 * content, so nothing decorative can intercept a click on the form.
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-6 py-12">
      <div className="auth-bg" aria-hidden="true" />
      <div className="auth-grain" aria-hidden="true" />

      <div className="absolute right-5 top-5 z-10">
        <ThemeToggle />
      </div>
      <Link href="/" className="relative z-10 mb-8">
        <Wordmark className="scale-110" />
      </Link>
      <div className="relative z-10 w-full max-w-md">{children}</div>
    </div>
  );
}
