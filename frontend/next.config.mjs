/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Emit a self-contained server bundle (.next/standalone) for a slim production Docker image.
  output: "standalone",
  // Lets a production build run without clobbering the .next/ a live `npm run dev` is serving from
  // (set NEXT_DIST_DIR=.next-build to verify a build while the dev server stays up).
  distDir: process.env.NEXT_DIST_DIR ?? ".next",
  // Proxy API calls to the Spring Boot backend in local dev so the browser talks same-origin
  // (keeps the refresh-token cookie first-party and avoids CORS during development).
  async rewrites() {
    // Accept a bare hostname (e.g. Render's fromService gives "app.onrender.com" with no scheme)
    // as well as a full URL. Default to https for a hosted host, http for localhost.
    let backend = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";
    if (!/^https?:\/\//.test(backend)) {
      backend = `${backend.startsWith("localhost") ? "http" : "https"}://${backend}`;
    }
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default nextConfig;
