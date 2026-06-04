"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BarChart3, Database, LayoutDashboard, Settings as GaugeIcon, PlayCircle, Settings as SettingsIcon } from "lucide-react";
import { cn } from "@/lib/utils";

const navItems = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/controls", label: "Controls", icon: PlayCircle },
  { href: "/data", label: "Data", icon: Database },
  { href: "/analytics", label: "Analytics", icon: BarChart3 },
  { href: "/settings", label: "Settings", icon: SettingsIcon },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="flex w-48 shrink-0 flex-col border-r border-[var(--color-border)] bg-[var(--color-surface-low)]">
      {/* Brand */}
      <div className="flex h-[44px] items-center gap-2.5 border-b border-[var(--color-border)] px-3">
        <div className="flex h-6 w-6 shrink-0 items-center justify-center bg-[var(--color-accent)] text-white font-mono text-[9px] font-bold rounded-none">
          RC
        </div>
        <div className="overflow-hidden">
          <span className="block text-[10px] font-semibold tracking-[0.12em] uppercase text-[var(--color-fg-tertiary)]">
            Reddit
          </span>
          <span className="block text-sm font-semibold leading-tight">
            Crawler
          </span>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-2 py-2 overflow-y-auto">
        {navItems.map(({ href, label, icon: Icon }) => {
          const isActive = pathname === href;
          return (
            <Link
              key={href}
              href={href}
              className={cn(
                "group flex w-full cursor-pointer items-center gap-2 rounded-none text-[11px] font-medium py-2 px-2 transition-colors",
                isActive
                  ? "bg-[var(--color-accent-soft)] text-[var(--color-accent-text)]"
                  : "text-[var(--color-fg-muted)] hover:bg-[var(--color-surface-high)] hover:text-[var(--color-fg-secondary)]"
              )}
            >
              <Icon className={cn("h-3.5 w-3.5 shrink-0", isActive ? "text-[var(--color-accent-text)]" : "")} />
              <span className="min-w-0 truncate">{label}</span>
            </Link>
          );
        })}
      </nav>

      {/* Footer */}
      <div className="border-t border-[var(--color-border)] px-3 py-2">
        <div
          style={{ backgroundColor: "#051f0e" }}
          className="flex items-center gap-1.5 bg-[var(--color-success-bg)] px-2 py-1 text-[9px] font-semibold tracking-[0.1em] uppercase text-[var(--color-success-text)] border border-[var(--color-success-border)] rounded-none"
        >
          <div className="h-[5px] w-[5px] shrink-0 bg-[var(--color-success-text)] animate-pulse rounded-none" />
          Online
        </div>
      </div>
    </aside>
  );
}
