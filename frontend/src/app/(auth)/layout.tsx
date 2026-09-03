import Link from "next/link";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { Wordmark } from "@/components/layout/wordmark";

/**
 * The signed-out shell: login, register, invitation, verification and password reset.
 *
 * <p>These screens were a card centred on an empty page — the first thing anyone sees of Orbit, and
 * it looked unfinished. The backdrop gives the card a ground to sit on: a soft colour band sweeping
 * across the foot of the page and a hairline grid that fades out behind the card. It follows the
 * convention business software actually uses, after an earlier attempt at a tiled mandala wallpaper
 * turned out to look like a wallpaper sample rather than a payroll product.
 *
 * <p>The colours all come from the theme tokens (see globals.css), so it follows light and dark on
 * its own. Everything here is `aria-hidden` and `pointer-events: none` and sits under the content, so
 * nothing decorative can intercept a click on the form or reach a screen reader.
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-6 py-12">
      <div className="auth-bg" aria-hidden="true" />
      <div className="auth-grid" aria-hidden="true" />

      <div className="absolute right-5 top-5 z-10">
        <ThemeToggle />
      </div>
      <Link href="/" className="relative z-10 mb-8">
        <Wordmark className="scale-110" />
      </Link>
      <div className="auth-panel relative z-10 w-full max-w-md">{children}</div>
    </div>
  );
}
