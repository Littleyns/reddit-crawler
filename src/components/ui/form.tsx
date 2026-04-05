import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils";

export function FormSection({
  title,
  description,
  className,
  children,
}: {
  title: ReactNode;
  description?: ReactNode;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section className={cn("space-y-4", className)}>
      <div>
        <h3 className="text-lg font-semibold text-[var(--ds-text-primary)]">{title}</h3>
        {description ? (
          <p className="mt-1 text-sm text-[var(--ds-text-secondary)]">{description}</p>
        ) : null}
      </div>
      {children}
    </section>
  );
}

export function FormGrid({
  className,
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("grid gap-4 md:grid-cols-2", className)} {...props} />;
}

export function FormActions({
  className,
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("flex flex-wrap items-center gap-3", className)} {...props} />;
}
