import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Orbit — The AI-Native Enterprise OS · by Calyvora",
  description:
    "Orbit by Calyvora — one platform, one identity, one data fabric, one AI layer. Replace the 20–40 disconnected tools you run with a single AI-native Enterprise Operating System.",
  // Served from /public rather than the app-router file convention so the same
  // three files back both the app and the marketing site — one mark, one place
  // to change it. See website/orbit/favicon.svg for the source geometry.
  icons: {
    icon: [
      { url: "/favicon.svg", type: "image/svg+xml" },
      { url: "/favicon.ico", sizes: "48x48" },
    ],
    apple: "/apple-touch-icon.png",
  },
};

/*
 * Runs before first paint so a stored dark preference doesn't flash light.
 *
 * Light unless dark was explicitly chosen — the OS preference is deliberately not consulted. Orbit
 * opens the same way for everyone the first time; dark is a decision, and once made it is remembered
 * and applies on every device.
 */
const noFlashTheme = `(function(){try{var d=document.documentElement;if(localStorage.getItem('calyvora-theme')==='dark'){d.classList.add('dark');d.classList.remove('light');}else{d.classList.add('light');d.classList.remove('dark');}}catch(e){}})();`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    // `light` here, not on the script alone: if JavaScript never runs the page must still be
    // readable, and an unclassed root would take the bare :root palette anyway.
    <html lang="en" className="light">
      <head>
        <script dangerouslySetInnerHTML={{ __html: noFlashTheme }} />
      </head>
      <body>{children}</body>
    </html>
  );
}
