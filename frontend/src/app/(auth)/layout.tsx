import Link from "next/link";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-6 py-12">
      <Link href="/" className="mb-8 text-xl font-semibold tracking-tight">
        Calyvora
      </Link>
      <div className="w-full max-w-md">{children}</div>
    </div>
  );
}
