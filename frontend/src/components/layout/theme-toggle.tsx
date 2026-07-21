"use client";

import { useEffect, useState } from "react";
import { Sun, Moon } from "lucide-react";

/**
 * Light/dark toggle. The theme is a `.light` class on <html> (dark is the default, set before paint
 * by the no-flash script in the root layout); the choice persists in localStorage.
 */
export function ThemeToggle() {
  const [light, setLight] = useState(false);

  useEffect(() => {
    setLight(document.documentElement.classList.contains("light"));
  }, []);

  function toggle() {
    const next = !light;
    const el = document.documentElement;
    el.classList.toggle("light", next);
    el.classList.toggle("dark", !next);
    try {
      localStorage.setItem("calyvora-theme", next ? "light" : "dark");
    } catch {
      /* ignore private-mode storage errors */
    }
    setLight(next);
  }

  return (
    <button
      onClick={toggle}
      className="inline-flex h-9 w-9 items-center justify-center rounded-md text-fg/60 hover:bg-fg/5 hover:text-fg"
      aria-label={light ? "Switch to dark theme" : "Switch to light theme"}
      title={light ? "Dark theme" : "Light theme"}
    >
      {light ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />}
    </button>
  );
}
