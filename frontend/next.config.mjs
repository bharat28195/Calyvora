/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Proxy API calls to the Spring Boot backend in local dev so the browser talks same-origin
  // (keeps the refresh-token cookie first-party and avoids CORS during development).
  async rewrites() {
    const backend = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default nextConfig;
