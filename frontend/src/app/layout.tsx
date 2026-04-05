import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Reddit Crawler",
  description: "Production dashboard for subreddit crawling, monitoring, and exports."
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
