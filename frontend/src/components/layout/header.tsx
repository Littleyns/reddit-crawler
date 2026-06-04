"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { useCrawlerStatus } from "@/hooks/use-reddit-crawler";
import { GlobalStatusIndicator } from "@/components/global-status-indicator";

const navItems = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/controls", label: "Controls" },
  { href: "/data", label: "Data" },
  { href: "/analytics", label: "Analytics" },
  { href: "/settings", label: "Settings" },
];

export function Header() {
  const pathname = usePathname();
  const { data: crawlerStatus } = useCrawlerStatus();

  const titles: Record<string, string> = {
    "/dashboard": "Crawler Dashboard",
    "/controls": "Crawler Controls",
    "/data": "Data Explorer",
    "/analytics": "Analytics Dashboard",
    "/settings": "Settings",
  };

  return (
    <header className="w-full border-b border-[var(--color-border)] bg-[var(--color-surface-low)]">
      {/* Top row: breadcrumb + status */}
      <div className="flex items-center justify-between w-full px-3 sm:px-4 lg:px-5" style={{ height: "40px" }}>
        {/* Breadcrumb-style nav */}
        <nav className="flex items-center gap-1 text-[11px] text-[var(--color-fg-muted)]">
          <span className="shrink-0">Home</span>
          <span className="text-[var(--color-border)]">/</span>
          {navItems.map((item, i, arr) => (
            <div key={item.href} className="flex items-center gap-1 shrink-0">
              <Link
                href={item.href}
                className={cn(
                  "transition-colors rounded-none px-1",
                  pathname === item.href
                    ? "text-[var(--color-accent-text)] font-medium"
                    : "text-[var(--color-fg-muted)] hover:text-[var(--color-fg-secondary)]"
                )}
              >
                {item.label}
              </Link>
              {i < arr.length - 1 && (
                <span className="text-[var(--color-border)]">/</span>
              )}
            </div>
          ))}
        </nav>

        <div className="flex items-center gap-3 shrink-0">
          {/* Crawler status indicator */}
          <div className={cn(
            "hidden sm:flex items-center gap-2 border border-[var(--color-border)] bg-[var(--color-surface-mid)] px-2.5 py-[4px]",
          )}>
            <div className={cn(
              "h-[5px] w-[5px] rounded-none",
              crawlerStatus?.isRunning ? "bg-[var(--color-success-text)] animate-pulse" : "bg-[var(--color-danger-text)]",
            )} />
            <span className="text-[10px] font-mono font-semibold tracking-[0.1em] uppercase text-[var(--color-fg-muted)]">
              {crawlerStatus?.isRunning ? crawlerStatus.mode : "IDLE"}
            </span>
          </div>

          {/* User */}
          <button className="flex items-center gap-2 border border-[var(--color-border)] bg-[var(--color-surface-mid)] px-2 py-1 transition-colors hover:bg-[var(--color-surface-high)]">
            <div className="flex h-5 w-5 shrink-0 items-center justify-center bg-[var(--color-accent)] text-[8px] font-bold text-white rounded-none">
              AR
            </div>
            <span className="hidden lg:inline text-[11px] font-medium leading-none text-[var(--color-fg-secondary)]">Amina R.</span>
          </button>
        </div>
      </div>

      {/* Page title row */}
      <div className="w-full px-3 pb-2 pt-1.5 sm:px-4 lg:px-5">
        <h2 className="text-sm font-semibold tracking-tight text-[var(--color-fg-primary)]">{titles[pathname] || "Home"}</h2>
      </div>
    </header>
  );
}
