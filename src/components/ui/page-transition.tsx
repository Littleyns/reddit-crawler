"use client";

import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import { useAnimationState } from "@/hooks/use-animation-state";
import { cn } from "@/lib/utils";

export function PageTransition({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const isVisible = useAnimationState(20);

  return (
    <div key={pathname} data-visible={isVisible} className={cn("page-transition", "page-shell")}>
      {children}
    </div>
  );
}
