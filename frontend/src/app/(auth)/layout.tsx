import Link from "next/link";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { Wordmark } from "@/components/layout/wordmark";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center px-6 py-12">
      <div className="absolute right-5 top-5">
        <ThemeToggle />
      </div>
      <Link href="/" className="mb-8">
        <Wordmark className="scale-110" />
      </Link>
      <div className="w-full max-w-md">{children}</div>
    </div>
  );
}
