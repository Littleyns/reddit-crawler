"use client";

import { useEffect, useId, useRef, useState } from "react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface PopoverBaseProps {
  trigger: ReactNode;
  content: ReactNode;
  align?: "start" | "center" | "end";
  widthClassName?: string;
}

const alignClasses = {
  start: "left-0",
  center: "left-1/2 -translate-x-1/2",
  end: "right-0",
};

export function Popover({
  trigger,
  content,
  align = "start",
  widthClassName = "w-80",
}: PopoverBaseProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const contentId = useId();

  useEffect(() => {
    function handleOutside(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }

    document.addEventListener("mousedown", handleOutside);
    return () => document.removeEventListener("mousedown", handleOutside);
  }, []);

  return (
    <div ref={containerRef} className="relative inline-flex">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={contentId}
        onClick={() => setOpen((current) => !current)}
        className="inline-flex"
      >
        {trigger}
      </button>
      {open ? (
        <div
          id={contentId}
          role="dialog"
          className={cn(
            "dialog-fade dialog-slide panel-strong absolute top-[calc(100%+0.75rem)] z-40 rounded-[var(--radius-md)] border border-[var(--ds-border-strong)] p-4 shadow-[var(--ds-shadow-floating)]",
            alignClasses[align],
            widthClassName,
          )}
        >
          {content}
        </div>
      ) : null}
    </div>
  );
}

export function Tooltip({
  label,
  children,
  side = "top",
}: {
  label: ReactNode;
  children: ReactNode;
  side?: "top" | "bottom";
}) {
  const [open, setOpen] = useState(false);
  const id = useId();

  return (
    <span
      className="relative inline-flex"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onFocus={() => setOpen(true)}
      onBlur={() => setOpen(false)}
    >
      <span aria-describedby={open ? id : undefined} className="inline-flex">
        {children}
      </span>
      {open ? (
        <span
          id={id}
          role="tooltip"
          className={cn(
            "dialog-fade pointer-events-none absolute left-1/2 z-40 -translate-x-1/2 whitespace-nowrap rounded-full border border-[var(--ds-border-strong)] bg-[var(--ds-surface-contrast)] px-3 py-1.5 text-xs font-medium text-[var(--ds-text-primary)] shadow-[var(--ds-shadow-floating)]",
            side === "top" ? "bottom-[calc(100%+0.5rem)]" : "top-[calc(100%+0.5rem)]",
          )}
        >
          {label}
        </span>
      ) : null}
    </span>
  );
}
