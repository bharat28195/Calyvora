import { NextResponse, type NextRequest } from "next/server";

// First line of defense (defense-in-depth — the server is the source of truth). Blocks the
// authenticated app routes when no session cookie is present. Role checks and the real
// authorization happen server-side; this only gates obvious unauthenticated access.
//
// Deliberately an allow-list of PUBLIC paths rather than a list of protected ones: the previous
// shape named five protected prefixes, so the ~25 app routes added since (/payroll, /billing,
// /platform, /expenses …) rendered an empty shell to logged-out visitors instead of redirecting.
// Guarding by default means a new page is protected the moment it exists, not when someone
// remembers to add it here.
const PUBLIC = [
  "/",
  "/login",
  "/register",
  "/request-trial",
  "/verify-email",
  "/accept-invite",
  "/forgot-password",
  "/reset-password",
  "/dev/mailbox",
  // Preparing a demo happens before anyone is signed in — that is the point of it.
  "/demo/seed",
];
const SESSION_COOKIE = "calyvora_session"; // live: presence of refresh cookie; mock: a marker cookie

function isPublic(pathname: string): boolean {
  return PUBLIC.some((p) => pathname === p || (p !== "/" && pathname.startsWith(`${p}/`)));
}

/** On for the demo frontend (Render env) and local dev; off — so the pages are absent — on production. */
function demoToolsEnabled(): boolean {
  return process.env.NEXT_PUBLIC_DEMO_TOOLS === "on" || process.env.NODE_ENV === "development";
}

export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;
  // Demo tooling exists only on the demo deployment (PD-80).
  if ((pathname === "/demo" || pathname.startsWith("/demo/")) && !demoToolsEnabled()) {
    return new NextResponse("Not found", { status: 404 });
  }
  if (isPublic(pathname)) return NextResponse.next();

  const hasSession = req.cookies.has(SESSION_COOKIE) || req.cookies.has("calyvora_rt");
  if (!hasSession) {
    const url = req.nextUrl.clone();
    url.pathname = "/login";
    url.searchParams.set("next", pathname);
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = {
  // Everything except Next's own assets, the API proxy (which authenticates itself and must return
  // 401 JSON rather than a redirect), and static files.
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|.*\\.[\\w]+$).*)"],
};
