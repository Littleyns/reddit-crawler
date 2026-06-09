import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  experimental: { ppr: false },
  turbopack: { root: '/home/kali/projects/reddit-crawler/frontend' },
  async rewrites() {
    const target = "http://162.19.205.8:8080/api";
    return [
      { source: "/api/:path*", destination: `${target}/:path*` },
    ];
  },
};

export default nextConfig;
