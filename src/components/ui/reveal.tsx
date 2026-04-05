"use client";

import type { ElementType, HTMLAttributes, ReactNode } from "react";
import { useAnimationState } from "@/hooks/use-animation-state";
import { useIntersectionReveal } from "@/hooks/use-intersection-reveal";
import { cn } from "@/lib/utils";

interface RevealProps extends HTMLAttributes<HTMLElement> {
  as?: ElementType;
  children: ReactNode;
  delay?: number;
  inView?: boolean;
  once?: boolean;
}

export function Reveal({
  as: Component = "div",
  children,
  className,
  delay = 0,
  inView = false,
  once = true,
  style,
  ...props
}: RevealProps) {
  const mounted = useAnimationState(delay);
  const { ref, isVisible } = useIntersectionReveal({ once });
  const visible = mounted && (inView ? isVisible : true);

  return (
    <Component
      ref={inView ? ref : undefined}
      data-visible={visible}
      className={cn("motion-enter", className)}
      style={style}
      {...props}
    >
      {children}
    </Component>
  );
}

export function StaggerItem({
  as: Component = "div",
  children,
  className,
  index,
  step = 80,
  style,
  ...props
}: Omit<RevealProps, "delay"> & { index: number; step?: number }) {
  const { ref, isVisible } = useIntersectionReveal();

  return (
    <Component
      ref={ref}
      data-visible={isVisible}
      className={cn("stagger-item", className)}
      style={{ ...style, ["--stagger-delay" as string]: `${index * step}ms` }}
      {...props}
    >
      {children}
    </Component>
  );
}
