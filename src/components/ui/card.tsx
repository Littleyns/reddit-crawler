import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils";

export type CardVariant = "default" | "elevated" | "outline" | "spotlight";

export interface CardProps extends HTMLAttributes<HTMLElement> {
  variant?: CardVariant;
  as?: "article" | "section" | "div";
}

const cardVariantClasses: Record<CardVariant, string> = {
  default: "panel border-[var(--ds-border-soft)]",
  elevated: "panel-strong border-[var(--ds-border-strong)] shadow-[var(--ds-shadow-panel)]",
  outline: "border border-[var(--ds-border-soft)] bg-[rgba(8,17,31,0.36)]",
  spotlight:
    "panel relative overflow-hidden border-[var(--ds-border-strong)] before:absolute before:inset-x-0 before:top-0 before:h-px before:bg-linear-to-r before:from-transparent before:via-[rgba(103,179,255,0.72)] before:to-transparent",
};

export function Card({
  as: Component = "section",
  className,
  variant = "default",
  ...props
}: CardProps) {
  return (
    <Component
      className={cn(
        "interactive-surface hover-glow rounded-[var(--radius-xl)] p-6",
        cardVariantClasses[variant],
        className,
      )}
      {...props}
    />
  );
}

export function CardHeader({
  className,
  title,
  description,
  action,
  children,
}: {
  className?: string;
  title?: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div className={cn("flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between", className)}>
      <div className="min-w-0">
        {title ? <h2 className="text-xl font-semibold text-[var(--ds-text-primary)]">{title}</h2> : null}
        {description ? (
          <p className="mt-2 max-w-2xl text-sm leading-6 text-[var(--ds-text-secondary)]">
            {description}
          </p>
        ) : null}
        {children}
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}

export function CardContent({
  className,
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("mt-6", className)} {...props} />;
}

export function CardFooter({
  className,
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("mt-6 flex flex-wrap items-center gap-3", className)} {...props} />;
}
