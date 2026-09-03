import Link from "next/link";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { Wordmark } from "@/components/layout/wordmark";

/**
 * The signed-out shell: login, register, invitation, verification and password reset.
 *
 * <p>These screens were a card centred on an empty page — the first thing anyone sees of Orbit, and
 * it looked unfinished. The backdrop is a soft colour field: four wide overlapping tints, a diffuse
 * horizon at the foot, and a bloom of the page colour behind the card so the form sits in calm. It is
 * deliberately shapeless — three earlier versions drew things (a tiled mandala, colour bands, orbit
 * rings with HR icons on them) and every one of them read as either wallpaper or clutter behind a
 * password box. See globals.css for the full history and the tuning knobs.
 *
 * <p>The colours all come from the theme tokens (see globals.css), so it follows light and dark on
 * its own. Everything here is `aria-hidden` and `pointer-events: none` and sits under the content, so
 * nothing decorative can intercept a click on the form or reach a screen reader.
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-6 py-12">
      <div className="auth-bg" aria-hidden="true" />
      <div className="auth-bloom" aria-hidden="true" />
      <div className="auth-texture" aria-hidden="true" />

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
