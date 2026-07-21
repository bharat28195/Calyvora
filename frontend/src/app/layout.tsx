import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Orbit — The AI-Native Enterprise OS · by Calyvora",
  description:
    "Orbit by Calyvora — one platform, one identity, one data fabric, one AI layer. Replace the 20–40 disconnected tools you run with a single AI-native Enterprise Operating System.",
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
