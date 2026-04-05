"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Blocks, Database, Gauge, PlayCircle, Settings } from "lucide-react";
import { cn } from "@/lib/utils";

const navItems = [
  { href: "/dashboard", label: "Dashboard", icon: Gauge },
  { href: "/controls", label: "Controls", icon: PlayCircle },
  { href: "/data", label: "Data", icon: Database },
  { href: "/settings", label: "Settings", icon: Settings },
  { href: "/design-system", label: "Design System", icon: Blocks },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="panel hidden w-72 shrink-0 flex-col rounded-[calc(var(--radius-xl)+0.25rem)] px-5 py-6 lg:flex">
      <div className="mb-8 flex items-center gap-3 px-2">
        <div className="flex h-11 w-11 items-center justify-center rounded-[var(--radius-md)] bg-[var(--ds-primary-500)] text-[var(--ds-text-inverse)]">
          <span className="font-mono text-sm font-medium">RC</span>
        </div>
        <div>
          <p className="text-sm font-medium uppercase tracking-[0.28em] text-[var(--ds-text-muted)]">
            ArabTooling
          </p>
          <h1 className="text-lg font-semibold">Reddit Crawler</h1>
        </div>
      </div>

      <nav className="flex flex-1 flex-col gap-2">
        {navItems.map(({ href, label, icon: Icon }) => {
          const isActive = pathname === href;

          return (
            <Link
              key={href}
              href={href}
              className={cn(
                "group flex items-center gap-3 rounded-[var(--radius-md)] px-4 py-3 text-sm font-medium",
                isActive
                  ? "bg-[var(--ds-primary-500)] text-[var(--ds-text-inverse)] shadow-[0_20px_40px_rgba(45,132,234,0.24)]"
                  : "text-[var(--ds-text-secondary)] hover:bg-[rgba(103,179,255,0.09)] hover:text-[var(--ds-text-primary)]",
              )}
            >
              <Icon className={cn("h-4 w-4", isActive ? "text-[var(--ds-text-inverse)]" : "")} />
              {label}
            </Link>
          );
        })}
      </nav>

      <div className="rounded-[calc(var(--radius-lg)+0.125rem)] border border-[var(--ds-border-strong)] bg-[linear-gradient(180deg,rgba(103,179,255,0.14),rgba(8,17,31,0.2))] p-5 text-white">
        <p className="text-xs uppercase tracking-[0.28em] text-white/60">Crawler Health</p>
        <p className="mt-3 text-3xl font-semibold">98.4%</p>
        <p className="mt-2 text-sm text-white/70">
          Pipeline reliability over the last 7 days across all subreddit jobs.
        </p>
      </div>
    </aside>
  );
}
