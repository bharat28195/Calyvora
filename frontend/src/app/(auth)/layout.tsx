import Link from "next/link";
import { ThemeToggle } from "@/components/layout/theme-toggle";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center px-6 py-12">
      <div className="absolute right-5 top-5">
        <ThemeToggle />
      </div>
      <Link href="/" className="mb-8 text-xl font-semibold tracking-tight">
        Calyvora
      </Link>
      <div className="w-full max-w-md">{children}</div>
    </div>
  );
}
