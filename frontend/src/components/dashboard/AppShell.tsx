"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ReactNode } from "react";

import { navigation } from "@/lib/data";

type AppShellProps = {
  children: ReactNode;
  title: string;
  description: string;
};

export function AppShell({ children, title, description }: AppShellProps) {
  const pathname = usePathname();

  return (
    <div className="page-shell">
      <header className="surface flex w-full flex-col gap-5 px-5 py-5 lg:flex-row lg:items-center lg:justify-between">
        <div className="space-y-3">
          <span className="eyebrow">ArabTooling Reddit Crawler</span>
          <div className="space-y-2">
            <h1 className="text-3xl font-semibold tracking-tight text-white sm:text-4xl">
              {title}
            </h1>
            <p className="page-copy">{description}</p>
          </div>
        </div>
        <nav className="flex flex-wrap items-center gap-2">
          {navigation.map((item) => {
            const active = pathname === item.href;

            return (
              <Link
                key={item.href}
                href={item.href}
                className={`rounded-2xl px-4 py-2 text-sm font-medium transition ${
                  active
                    ? "bg-white text-slate-950"
                    : "bg-white/5 text-slate-200 hover:bg-white/10"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
      </header>
      {children}
    </div>
  );
}
