"use client";

import { ReactNode } from "react";
import { Header } from "@/components/layout/header";
import { Sidebar } from "@/components/layout/sidebar";

export function MainLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-screen w-full min-w-0 bg-[var(--color-bg-base)] text-[var(--color-fg-primary)] overflow-hidden">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <Header />
        <main className="min-h-0 flex-1 w-full min-w-0 overflow-auto px-3 sm:px-4 lg:px-5 py-3">
          {children}
        </main>
      </div>
    </div>
  );
}
