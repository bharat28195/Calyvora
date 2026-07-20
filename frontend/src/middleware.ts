import { NextResponse, type NextRequest } from "next/server";

// First line of defense (defense-in-depth — the server is the source of truth). Blocks the
// authenticated app routes when no session cookie is present. Role checks and the real
// authorization happen server-side; this only gates obvious unauthenticated access.
const PROTECTED = ["/dashboard", "/people", "/work", "/members", "/settings"];
const SESSION_COOKIE = "calyvora_session"; // live: presence of refresh cookie; mock: a marker cookie

export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;
  const isProtected = PROTECTED.some((p) => pathname === p || pathname.startsWith(`${p}/`));
  if (!isProtected) return NextResponse.next();

  const hasSession =
    req.cookies.has(SESSION_COOKIE) || req.cookies.has("calyvora_rt");
  if (!hasSession) {
    const url = req.nextUrl.clone();
    url.pathname = "/login";
    url.searchParams.set("next", pathname);
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard/:path*", "/people/:path*", "/work/:path*", "/members/:path*", "/settings/:path*"],
};
