import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: "class",
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // Brand accents (theme-independent)
        violet: "#7c5cff",
        aqua: "#22d3ee",
        ink: "#0b0b12",
        // Semantic tokens — flip between light & dark via CSS variables in globals.css.
        // `<alpha-value>` lets every opacity modifier (text-fg/50, bg-fg/5, …) keep working.
        app: "rgb(var(--app) / <alpha-value>)", // page background
        surface: "rgb(var(--surface) / <alpha-value>)", // elevated solid (panels, modals, selects)
        fg: "rgb(var(--fg) / <alpha-value>)", // foreground tint (text, borders, subtle fills)
      },
      fontFamily: {
        sans: ["var(--font-sans)", "system-ui", "sans-serif"],
      },
    },
  },
  plugins: [],
};

export default config;
