import type { Metadata } from "next";
import { MainLayout } from "@/components/layout/main-layout";
import { Providers } from "@/components/providers";
import "./globals.css";

export const metadata: Metadata = {
  title: "Reddit Crawler Control Center",
  description:
    "Operate the Reddit Crawler, monitor scraping activity, and manage exports from a unified dashboard.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-screen w-screen bg-[var(--color-bg-base)] text-[var(--color-fg-primary)]">
        <Providers>
          <MainLayout>{children}</MainLayout>
        </Providers>
      </body>
    </html>
  );
}
