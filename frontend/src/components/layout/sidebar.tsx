"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Database,
  Gauge,
  PlayCircle,
  Settings,
} from "lucide-react";
import { cn } from "@/lib/utils";

const navItems = [
  { href: "/dashboard", label: "Dashboard", icon: Gauge },
  { href: "/controls", label: "Controls", icon: PlayCircle },
  { href: "/data", label: "Data", icon: Database },
  { href: "/settings", label: "Settings", icon: Settings },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="panel hidden w-72 shrink-0 flex-col rounded-[28px] border-white/40 px-5 py-6 lg:flex">
      <div className="mb-8 flex items-center gap-3 px-2">
        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[var(--color-surface-dark)] text-white">
          <span className="font-mono text-sm font-medium">RC</span>
        </div>
        <div>
          <p className="text-sm font-medium uppercase tracking-[0.28em] text-[var(--color-muted)]">
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
                "group flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-medium",
                isActive
                  ? "bg-[var(--color-surface-dark)] text-white shadow-lg"
                  : "text-[var(--color-muted)] hover:bg-white/70 hover:text-[var(--color-foreground)]",
              )}
            >
              <Icon className={cn("h-4 w-4", isActive ? "text-[var(--color-warning)]" : "")} />
              {label}
            </Link>
          );
        })}
      </nav>

      <div className="rounded-3xl bg-[var(--color-surface-dark)] p-5 text-white">
        <p className="text-xs uppercase tracking-[0.28em] text-white/60">Crawler Health</p>
        <p className="mt-3 text-3xl font-semibold">98.4%</p>
        <p className="mt-2 text-sm text-white/70">
          Pipeline reliability over the last 7 days across all subreddit jobs.
        </p>
      </div>
    </aside>
  );
}
