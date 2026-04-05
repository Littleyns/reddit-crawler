import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";
export type ButtonSize = "sm" | "md" | "lg";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  leadingIcon?: ReactNode;
  trailingIcon?: ReactNode;
  loading?: boolean;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    "bg-[var(--ds-primary-500)] text-[var(--ds-text-inverse)] shadow-[0_18px_40px_rgba(45,132,234,0.26)] hover:bg-[var(--ds-primary-600)]",
  secondary:
    "border border-[var(--ds-border-strong)] bg-[var(--ds-secondary-500)] text-[var(--ds-text-primary)] hover:bg-[var(--ds-secondary-600)]",
  ghost:
    "border border-transparent bg-transparent text-[var(--ds-text-secondary)] hover:bg-[var(--ds-ghost-hover)] hover:text-[var(--ds-text-primary)]",
  danger:
    "bg-[var(--ds-danger-500)] text-[var(--ds-text-inverse)] shadow-[0_18px_40px_rgba(240,84,103,0.22)] hover:bg-[var(--ds-danger-600)]",
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: "min-h-10 rounded-[var(--radius-sm)] px-3.5 py-2 text-sm",
  md: "min-h-11 rounded-[var(--radius-sm)] px-4 py-2.5 text-sm",
  lg: "min-h-12 rounded-[var(--radius-md)] px-5 py-3 text-sm",
};

export function Button({
  className,
  variant = "primary",
  size = "md",
  leadingIcon,
  trailingIcon,
  loading,
  disabled,
  children,
  ...props
}: ButtonProps) {
  const isDisabled = disabled || loading;

  return (
    <button
      className={cn(
        "interactive-ripple interactive-surface inline-flex items-center justify-center gap-2 font-medium whitespace-nowrap disabled:translate-y-0 disabled:cursor-not-allowed disabled:opacity-55",
        sizeClasses[size],
        variantClasses[variant],
        className,
      )}
      disabled={isDisabled}
      {...props}
    >
      {leadingIcon}
      {children}
      {trailingIcon}
    </button>
  );
}
