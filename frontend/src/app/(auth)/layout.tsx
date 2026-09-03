import Link from "next/link";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { Wordmark } from "@/components/layout/wordmark";

/**
 * The signed-out shell: login, register, invitation, verification and password reset.
 *
 * <p>These screens were a card centred on an empty page — the first thing anyone sees of Orbit, and
 * it looked unfinished. The backdrop now says what the product is: the HR tools — people, attendance,
 * payroll, leave, documents, reports, hiring — in orbit around the card, on a hairline grid that fades
 * out behind it. The node positions are checked against this layout, so changing the card width or the
 * wordmark above it means re-running the placement check in auth-orbit.svg.
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
