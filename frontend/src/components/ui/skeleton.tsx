import { cn } from "@/lib/utils";
import React from "react";

export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("animate-pulse rounded-md bg-[var(--color-surface-high)]", className)}
      {...props}
    />
  );
}

/* ---- Layout skeletons ---- */

export function ChartSkeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <Skeleton
      className={cn("h-64 w-full flex items-center justify-center", className)}
      {...props}
    />
  );
}

export function CardSkeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <Skeleton
      className={cn("h-32 w-full rounded-md", className)}
      {...props}
    />
  );
}

export function ListSkeleton({ lines = 5, className, ...props }: { lines?: number } & React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn("flex flex-col gap-2", className)} {...props}>
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} className="h-10 w-full" />
      ))}
    </div>
  );
}
