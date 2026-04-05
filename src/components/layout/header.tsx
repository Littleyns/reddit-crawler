"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Bell, ChevronDown, Layers3, Radio } from "lucide-react";
import { useCrawlerStatus } from "@/hooks/use-reddit-crawler";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/status-badge";
import { cn } from "@/lib/utils";

const navItems = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/controls", label: "Controls" },
  { href: "/data", label: "Data" },
  { href: "/settings", label: "Settings" },
  { href: "/design-system", label: "Design System" },
];

export function Header() {
  const pathname = usePathname();
  const { data: crawlerStatus } = useCrawlerStatus();

  return (
    <header className="panel mb-6 flex flex-col gap-4 rounded-[calc(var(--radius-xl)+0.25rem)] px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex-1">
        <p className="text-xs font-medium uppercase tracking-[0.32em] text-[var(--ds-text-muted)]">
          Command Center
        </p>
        <h2 className="mt-2 text-2xl font-semibold text-balance">
          Operate the crawler, inspect live throughput, and review the shared component system.
        </h2>
        <nav className="mt-4 flex flex-wrap gap-2 lg:hidden">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "rounded-full px-3 py-2 text-sm font-medium",
                pathname === item.href
                  ? "bg-[var(--ds-primary-500)] text-[var(--ds-text-inverse)]"
                  : "bg-[rgba(255,255,255,0.05)] text-[var(--ds-text-secondary)]",
              )}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="flex items-center gap-3 rounded-[var(--radius-md)] border border-[var(--ds-border-soft)] bg-[rgba(8,17,31,0.48)] px-4 py-3">
          <div
            className={cn(
              "h-2.5 w-2.5 rounded-full",
              crawlerStatus?.isRunning ? "bg-[var(--ds-success-500)]" : "bg-[var(--ds-danger-500)]",
            )}
          />
          <div>
            <p className="text-xs uppercase tracking-[0.2em] text-[var(--ds-text-muted)]">Status</p>
            <p className="text-sm font-medium">
              {crawlerStatus?.isRunning ? "Crawler running" : "Crawler idle"}
            </p>
          </div>
          <Radio className="h-4 w-4 text-[var(--ds-text-muted)]" />
        </div>

        <StatusBadge
          tone={crawlerStatus?.isRunning ? "running" : "neutral"}
          label={crawlerStatus?.isRunning ? "live" : "standby"}
        />

        <Button variant="ghost" size="sm" aria-label="Notifications" leadingIcon={<Bell className="h-4 w-4" />} />

        <Button
          variant="secondary"
          size="md"
          className="justify-start"
          leadingIcon={
            <span className="flex h-10 w-10 items-center justify-center rounded-[var(--radius-sm)] bg-white/10 text-sm font-semibold">
              AR
            </span>
          }
          trailingIcon={<ChevronDown className="h-4 w-4" />}
        >
          <span className="flex flex-col items-start">
            <span className="text-sm font-medium">Amina Rahman</span>
            <span className="text-xs text-white/65">Operations Admin</span>
          </span>
        </Button>
        <Link href="/design-system" className="hidden lg:inline-flex">
          <Button variant="ghost" size="sm" leadingIcon={<Layers3 className="h-4 w-4" />}>
            Library
          </Button>
        </Link>
      </div>
    </header>
  );
}
