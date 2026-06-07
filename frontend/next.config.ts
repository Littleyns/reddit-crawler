import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  experimental: { ppr: false },
  turbopack: { root: '/home/kali/projects/reddit-crawler/frontend' },
  async rewrites() {
    const target = process.env.API_PROXY_TARGET ?? "http://localhost:8000/api";
    return [
      { source: "/api/:path*", destination: `${target}/:path*` },
    ];
  },
};

export default nextConfig;
