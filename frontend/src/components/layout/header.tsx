"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Bell, ChevronDown, Radio } from "lucide-react";
import { useCrawlerStatus } from "@/hooks/use-reddit-crawler";
import { cn } from "@/lib/utils";

const navItems = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/controls", label: "Controls" },
  { href: "/data", label: "Data" },
  { href: "/settings", label: "Settings" },
];

export function Header() {
  const pathname = usePathname();
  const { data: crawlerStatus } = useCrawlerStatus();

  return (
    <header className="panel mb-6 flex flex-col gap-4 rounded-[28px] border-white/40 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex-1">
        <p className="text-xs font-medium uppercase tracking-[0.32em] text-[var(--color-muted)]">
          Command Center
        </p>
        <h2 className="mt-2 text-2xl font-semibold text-balance">
          Operate the crawler, inspect live throughput, and export collected Reddit data.
        </h2>
        <nav className="mt-4 flex flex-wrap gap-2 lg:hidden">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "rounded-full px-3 py-2 text-sm font-medium",
                pathname === item.href
                  ? "bg-[var(--color-surface-dark)] text-white"
                  : "bg-white/75 text-[var(--color-muted)]",
              )}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="flex items-center gap-3 rounded-2xl bg-white/75 px-4 py-3">
          <div
            className={`h-2.5 w-2.5 rounded-full ${
              crawlerStatus?.isRunning ? "bg-[var(--color-accent)]" : "bg-[var(--color-danger)]"
            }`}
          />
          <div>
            <p className="text-xs uppercase tracking-[0.2em] text-[var(--color-muted)]">Status</p>
            <p className="text-sm font-medium">
              {crawlerStatus?.isRunning ? "Crawler running" : "Crawler idle"}
            </p>
          </div>
          <Radio className="h-4 w-4 text-[var(--color-muted)]" />
        </div>

        <button className="flex items-center justify-center rounded-2xl border border-[var(--color-border)] bg-white/70 p-3 text-[var(--color-muted)] hover:bg-white">
          <Bell className="h-4 w-4" />
        </button>

        <button className="flex items-center gap-3 rounded-2xl bg-[var(--color-surface-dark)] px-4 py-3 text-left text-white">
          <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-white/12">
            <span className="text-sm font-semibold">AR</span>
          </div>
          <div>
            <p className="text-sm font-medium">Amina Rahman</p>
            <p className="text-xs text-white/65">Operations Admin</p>
          </div>
          <ChevronDown className="h-4 w-4 text-white/60" />
        </button>
      </div>
    </header>
  );
}
