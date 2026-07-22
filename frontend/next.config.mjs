/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Lets a production build run without clobbering the .next/ a live `npm run dev` is serving from
  // (set NEXT_DIST_DIR=.next-build to verify a build while the dev server stays up).
  distDir: process.env.NEXT_DIST_DIR ?? ".next",
  // Proxy API calls to the Spring Boot backend in local dev so the browser talks same-origin
  // (keeps the refresh-token cookie first-party and avoids CORS during development).
  async rewrites() {
    const backend = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default nextConfig;
