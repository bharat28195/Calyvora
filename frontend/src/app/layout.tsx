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

// Runs before first paint so a light-theme preference doesn't flash dark.
const noFlashTheme = `(function(){try{var t=localStorage.getItem('calyvora-theme');var d=document.documentElement;if(t==='light'){d.classList.add('light');d.classList.remove('dark');}else{d.classList.add('dark');d.classList.remove('light');}}catch(e){}})();`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="dark">
      <head>
        <script dangerouslySetInnerHTML={{ __html: noFlashTheme }} />
      </head>
      <body>{children}</body>
    </html>
  );
}
